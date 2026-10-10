package com.peru.expresdm.ui.routes

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.data.Place
import com.peru.expresdm.data.RecentPlaceStore
import com.peru.expresdm.databinding.ItemPlaceBinding
import com.peru.expresdm.databinding.SheetTripPlannerBinding
import com.peru.expresdm.ui.formatDistance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "¿A dónde vas?": se elige de dónde se sale y a dónde se va, y el inicio
 * muestra cómo llegar.
 *
 * Por defecto se sale desde donde está el usuario; el origen solo hay que
 * tocarlo para planear un viaje desde otro sitio. Mientras se escribe aparecen
 * lugares del mapa (centros comerciales, calles, universidades…), no rutas:
 * qué micro tomar se calcula al elegir el lugar correcto.
 */
class TripPlannerSheet : BottomSheetDialogFragment() {

    private enum class Field { ORIGIN, DESTINATION }

    private var _binding: SheetTripPlannerBinding? = null
    private val binding get() = _binding!!

    /** El mismo ViewModel que el mapa del inicio: el viaje elegido se dibuja allí. */
    private val viewModel: RoutesViewModel by viewModels({ requireParentFragment() })

    private val adapter by lazy { PlaceAdapter(onPlaceClick = { place -> onPlacePicked(place) }) }

    /** `null`: se sale desde donde está el usuario. */
    private var origin: Place? = null
    private var destination: Place? = null
    private var activeField = Field.DESTINATION

    private var lookupJob: Job? = null

    /** Al escribir el nombre de un lugar elegido no hay que tratarlo como texto nuevo. */
    private var settingText = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTripPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.resultsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.resultsRecycler.adapter = adapter

        // Al reabrir con un viaje en curso, se parte de él para poder ajustarlo.
        (viewModel.homeState.value.search as? PlaceSearchState.Chosen)?.let { current ->
            origin = current.origin
            destination = current.place
        }
        showPlaces()

        setUpField(binding.originInput, Field.ORIGIN)
        setUpField(binding.destinationInput, Field.DESTINATION)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSearch.setOnClickListener { search(activeInput().text?.toString().orEmpty()) }
        binding.btnSwap.setOnClickListener { swap() }
        binding.btnOriginHere.setOnClickListener {
            origin = null
            showPlaces()
            showIdle()
        }
        binding.btnPlan.setOnClickListener { finish() }
        binding.btnPickOnMap.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_ACTION to ACTION_PICK_ON_MAP))
            dismiss()
        }
        binding.btnClearRecents.setOnClickListener { RecentPlaceStore.get(requireContext()).clear() }

        buildPopular()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentPlaces.collect { renderRecents(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // El teclado encoge la hoja en lugar de tapar los resultados.
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    // --- Campos ---

    private fun setUpField(input: EditText, field: Field) {
        input.setOnFocusChangeListener { _, focused ->
            if (focused) {
                activeField = field
                onTextChanged(input.text?.toString().orEmpty())
            }
        }
        input.doAfterTextChanged { text ->
            if (settingText || activeField != field) return@doAfterTextChanged
            // Cambiar el texto deja de referirse al lugar que estaba elegido.
            if (field == Field.ORIGIN) origin = null else destination = null
            renderActions()
            onTextChanged(text?.toString().orEmpty())
        }
        input.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                search(input.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
    }

    private fun activeInput(): EditText =
        if (activeField == Field.ORIGIN) binding.originInput else binding.destinationInput

    /** Escribe en los campos los lugares elegidos, sin disparar sugerencias. */
    private fun showPlaces() {
        settingText = true
        binding.originInput.setText(origin?.name.orEmpty())
        binding.destinationInput.setText(destination?.name.orEmpty())
        settingText = false
        renderActions()
    }

    private fun renderActions() {
        val binding = _binding ?: return
        binding.btnOriginHere.isVisible = origin != null
        binding.btnPlan.isVisible = destination != null
    }

    // --- Sugerencias y búsqueda ---

    /**
     * Mientras se escribe: lugares del mapa que coinciden. Se espera a que el
     * usuario pare un momento, para no preguntar al buscador con cada letra.
     */
    private fun onTextChanged(text: String) {
        lookupJob?.cancel()
        binding.searchProgress.isInvisible = true
        val chosen = if (activeField == Field.ORIGIN) origin else destination
        if (text.isBlank() || chosen != null) {
            showIdle()
            return
        }
        if (text.trim().length < MIN_QUERY_LENGTH) {
            showResults(R.string.planner_typing_label, getString(R.string.search_too_short), emptyList())
            return
        }
        lookUp(text, delayMs = TYPING_PAUSE_MS)
    }

    /** Al pulsar buscar: lo mismo, pero ya, y se guarda el teclado para ver la lista. */
    private fun search(text: String) {
        if (text.trim().length < MIN_QUERY_LENGTH) {
            Snackbar.make(binding.root, R.string.search_too_short, Snackbar.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        lookUp(text, delayMs = 0)
    }

    private fun lookUp(text: String, delayMs: Long) {
        lookupJob?.cancel()
        lookupJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(delayMs)
            _binding?.searchProgress?.isInvisible = false
            val found = viewModel.suggestPlaces(text)
            val binding = _binding ?: return@launch
            binding.searchProgress.isInvisible = true
            val hint = when {
                found.offline -> getString(R.string.planner_places_offline)
                found.places.isEmpty() -> getString(R.string.planner_places_none)
                else -> getString(R.string.planner_places_hint)
            }
            showResults(R.string.planner_places_label, hint, found.places)
        }
    }

    private fun showIdle() {
        val binding = _binding ?: return
        binding.idleContent.isVisible = true
        binding.resultsContent.isVisible = false
        adapter.submitList(emptyList())
    }

    private fun showResults(@StringRes label: Int, hint: String, places: List<Place>) {
        val binding = _binding ?: return
        binding.idleContent.isVisible = false
        binding.resultsContent.isVisible = true
        binding.resultsLabel.setText(label)
        binding.resultsHint.text = hint
        adapter.submitList(places.map { PlaceResult(it, viewModel.distanceTo(it)) })
    }

    // --- Elegir ---

    private fun onPlacePicked(place: Place) {
        if (activeField == Field.ORIGIN) {
            origin = place
            showPlaces()
            showIdle()
            // Con el destino ya puesto, cambiar el origen completa el viaje.
            if (destination != null) finish() else binding.destinationInput.requestFocus()
        } else {
            destination = place
            finish()
        }
    }

    /** Lo que era salida pasa a ser destino, y al revés. */
    private fun swap() {
        val newOrigin = destination
        // "Desde donde estoy" solo puede ser destino si se sabe dónde es.
        val newDestination = origin ?: viewModel.currentPlace()
        if (newOrigin == null && origin == null) return
        if (newDestination == null) {
            Snackbar.make(binding.root, R.string.destination_needs_location, Snackbar.LENGTH_LONG).show()
            return
        }
        origin = newOrigin
        destination = newDestination
        binding.root.requestFocus()
        hideKeyboard()
        showPlaces()
        showIdle()
    }

    private fun finish() {
        val target = destination ?: return
        hideKeyboard()
        viewModel.planTrip(origin, target)
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_ACTION to ACTION_PLANNED))
        dismiss()
    }

    // --- Atajos ---

    private fun renderRecents(places: List<Place>) {
        val binding = _binding ?: return
        binding.recentsSection.isVisible = places.isNotEmpty()
        binding.recentsContainer.removeAllViews()
        places.forEach { place ->
            val row = ItemPlaceBinding.inflate(layoutInflater, binding.recentsContainer, false)
            row.placeIcon.setImageResource(R.drawable.ic_clock)
            row.placeName.text = place.name
            row.placeDetail.text = place.detail
            row.placeDetail.isVisible = place.detail.isNotBlank()
            val distance = viewModel.distanceTo(place)
            row.placeDistance.isVisible = distance != null
            distance?.let { row.placeDistance.text = requireContext().formatDistance(it) }
            row.root.setOnClickListener {
                activeField = Field.DESTINATION
                onPlacePicked(place)
            }
            binding.recentsContainer.addView(row.root)
        }
    }

    /** Los sitios a los que más se va, agrupados: un toque los busca en el mapa. */
    private fun buildPopular() {
        val context = requireContext()
        val edge = (EDGE_DP * resources.displayMetrics.density).toInt()
        PopularDestinations.GROUPS.forEach { group ->
            val title = TextView(context).apply {
                setText(group.title)
                setTextAppearance(R.style.TextAppearance_Expresdm_Body)
                setPadding(edge, edge * 3 / 4, edge, edge / 4)
            }
            val chips = ChipGroup(context).apply {
                isSingleLine = true
                setPadding(edge, 0, edge, 0)
            }
            group.places.forEach { name ->
                chips.addView(
                    Chip(context).apply {
                        text = name
                        isCheckable = false
                        setOnClickListener { searchPopular(name) }
                    }
                )
            }
            val scroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(chips)
            }
            binding.popularContainer.addView(title)
            binding.popularContainer.addView(scroll)
        }
    }

    private fun searchPopular(name: String) {
        activeField = Field.DESTINATION
        destination = null
        settingText = true
        binding.destinationInput.setText(name)
        binding.destinationInput.setSelection(name.length)
        settingText = false
        renderActions()
        search(name)
    }

    private fun hideKeyboard() {
        val binding = _binding ?: return
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        lookupJob?.cancel()
        binding.resultsRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "TripPlannerSheet"
        private const val SHEET_HEIGHT_RATIO = 0.94
        private const val MIN_QUERY_LENGTH = 2

        /** Cuánto se espera sin teclear antes de buscar. */
        private const val TYPING_PAUSE_MS = 450L
        private const val EDGE_DP = 20

        /** Con qué clave avisa esta hoja a quien la abrió, y de qué. */
        const val REQUEST_KEY = "trip_planner"
        const val RESULT_ACTION = "action"

        /** Hay un viaje elegido: toca enseñar cómo llegar. */
        const val ACTION_PLANNED = "planned"

        /** El usuario prefiere marcar el destino tocando el mapa. */
        const val ACTION_PICK_ON_MAP = "pick_on_map"

        fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag(TAG) == null) TripPlannerSheet().show(manager, TAG)
        }
    }
}

/**
 * Los destinos más habituales de Trujillo, por tipo.
 *
 * Solo llevan el nombre: la posición la da el buscador al tocarlos, así no hay
 * coordenadas escritas a mano que puedan quedar mal o desfasadas.
 */
internal object PopularDestinations {

    class Group(@StringRes val title: Int, val places: List<String>)

    val GROUPS = listOf(
        Group(
            R.string.planner_group_universities,
            listOf(
                "Universidad Nacional de Trujillo",
                "Universidad Privada Antenor Orrego",
                "Universidad César Vallejo",
                "Universidad Privada del Norte"
            )
        ),
        Group(
            R.string.planner_group_malls,
            listOf("Real Plaza Trujillo", "Mall Plaza Trujillo", "Open Plaza Los Jardines", "Mercado La Hermelinda")
        ),
        Group(
            R.string.planner_group_center,
            listOf("Plaza de Armas de Trujillo", "Plazuela El Recreo", "Estadio Mansiche", "Mercado Central de Trujillo")
        ),
        Group(
            R.string.planner_group_health,
            listOf(
                "Hospital Regional Docente de Trujillo",
                "Hospital Belén de Trujillo",
                "Hospital Víctor Lazarte Echegaray"
            )
        ),
        Group(
            R.string.planner_group_transport,
            listOf("Terrapuerto Trujillo", "Aeropuerto de Trujillo")
        ),
        Group(
            R.string.planner_group_tourism,
            listOf("Huanchaco", "Chan Chan", "Huaca de la Luna", "Las Delicias")
        )
    )
}

package com.peru.expresdm.ui.newroute

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.PathCleaner
import com.peru.expresdm.data.RecordingDraft
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.databinding.FragmentNewRouteBinding
import com.peru.expresdm.databinding.ItemNewStopBinding
import com.peru.expresdm.tracking.RouteRecorderService
import com.peru.expresdm.ui.MapMarkers
import com.peru.expresdm.ui.MapRouteRequests
import com.peru.expresdm.ui.StopMarkerStyle
import com.peru.expresdm.ui.VisibleBand
import com.peru.expresdm.ui.applyDefaults
import com.peru.expresdm.ui.formatDistance
import com.peru.expresdm.ui.frameInBand
import com.peru.expresdm.ui.themeColor
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Formulario que se abre al terminar de grabar una ruta.
 *
 * Muestra el recorrido grabado en un mapa, pide los datos de la ruta y deja
 * corregir las paradas detectadas. Al guardar, la ruta aparece en la lista y
 * se traza en el mapa del inicio.
 */
class NewRouteFragment : Fragment() {

    private var _binding: FragmentNewRouteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewRouteViewModel by viewModels()

    /** Fila de cada parada por su clave, para no rehacerlas mientras se escribe. */
    private val stopRows = mutableMapOf<Long, ItemNewStopBinding>()
    private var renderedKeys: List<Long>? = null
    private var stopLayer: FolderOverlay? = null
    private var pathLayer: FolderOverlay? = null

    /** El mapa se encuadra una sola vez, para no mover lo que el usuario esté mirando. */
    private var framedPath = false

    /** Salir con "atrás" no borra la grabación, pero conviene confirmarlo. */
    private val leaveOnBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = confirmLeave()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val draft = viewModel.draft
        if (draft == null) {
            findNavController().popBackStack()
            return
        }

        applyInsets()
        // Dónde acabará la ruta: solo aquí, compartida, o pendiente de iniciar sesión.
        binding.storageNote.setText(
            when {
                !ApiClient.isConfigured -> R.string.new_route_local_note
                AccountRepository.isSignedIn -> R.string.new_route_shared_note
                else -> R.string.new_route_shared_note_signed_out
            }
        )
        binding.toolbar.setNavigationOnClickListener { confirmLeave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, leaveOnBack)

        setUpPreview(draft)

        listOf(
            binding.nameLayout to binding.nameInput,
            binding.originLayout to binding.originInput,
            binding.destinationLayout to binding.destinationInput,
            binding.fareLayout to binding.fareInput
        ).forEach { (layout, input) -> input.doAfterTextChanged { layout.error = null } }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDiscard.setOnClickListener { confirmDiscard() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.stops.collect { renderStops(it, draft) } }
                launch { viewModel.path.collect { renderPath(it) } }
            }
        }
    }

    /** La barra de botones queda por encima de la barra de gestos y del teclado. */
    private fun applyInsets() {
        val basePadding = binding.bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { bar, insets ->
            val bottom = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            )
            bar.updatePadding(bottom = basePadding + bottom)
            insets
        }
    }

    // --- Mapa del recorrido ---

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpPreview(draft: RecordingDraft) {
        val context = requireContext()
        val map = binding.previewMap
        map.applyDefaults(context)
        // Dentro de un formulario desplazable, los gestos que empiezan en el mapa son del mapa.
        map.setOnTouchListener { touched, _ ->
            touched.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // El recorrido llega de viewModel.path; las paradas van encima.
        pathLayer = FolderOverlay().also { map.overlays.add(it) }
        stopLayer = FolderOverlay().also { map.overlays.add(it) }

        map.controller.setZoom(PREVIEW_ZOOM)
        draft.points.firstOrNull()?.let { map.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
    }

    /** Dibuja el recorrido limpio y, cuando llega, el ajustado a las calles. */
    private fun renderPath(preview: PathPreview) {
        val binding = _binding ?: return
        val layer = pathLayer ?: return
        val context = requireContext()
        val map = binding.previewMap
        val points = preview.points.map { GeoPoint(it.latitude, it.longitude) }

        layer.items.clear()
        if (points.size >= 2) {
            layer.add(line(map, points, context.themeColor(com.google.android.material.R.attr.colorSurface), 9f))
            layer.add(line(map, points, ContextCompat.getColor(context, R.color.track_recorded), 5f))
        }
        // Las paradas se muestran sobre el recorrido, como quedarán guardadas.
        drawStops(viewModel.stops.value)

        binding.pathStatus.setText(
            when {
                preview.snapping -> R.string.new_route_path_snapping
                preview.snapped -> R.string.new_route_path_snapped
                else -> R.string.new_route_path_cleaned
            }
        )

        if (!framedPath && points.isNotEmpty()) {
            framedPath = true
            map.doOnLayout {
                _binding?.previewMap?.frameInBand(
                    points = points,
                    band = VisibleBand(0, it.height),
                    borderPx = (FRAME_BORDER_DP * resources.displayMetrics.density).toInt(),
                    animated = false
                )
            }
        }
        map.invalidate()
    }

    private fun drawStops(stops: List<EditableStop>) {
        val layer = stopLayer ?: return
        val map = binding.previewMap
        val context = requireContext()
        val color = context.themeColor(com.google.android.material.R.attr.colorSecondary)

        val path = viewModel.path.value.points
        layer.items.clear()
        stops.forEachIndexed { index, stop ->
            val endpoint = index == 0 || index == stops.lastIndex
            val onPath = PathCleaner.snapToPath(GeoCoordinate(stop.latitude, stop.longitude), path)
            layer.add(
                Marker(map).apply {
                    position = GeoPoint(onPath.latitude, onPath.longitude)
                    icon = MapMarkers.numberedStop(
                        context,
                        index + 1,
                        color,
                        if (endpoint) StopMarkerStyle.ENDPOINT else StopMarkerStyle.NORMAL
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        focusStop(stop.key, scrollToMap = false)
                        true
                    }
                }
            )
        }
        map.invalidate()
    }

    private fun focusStop(key: Long, scrollToMap: Boolean) {
        val binding = _binding ?: return
        val stop = viewModel.stops.value.firstOrNull { it.key == key } ?: return
        if (scrollToMap) binding.scroll.smoothScrollTo(0, 0)
        val map = binding.previewMap
        map.controller.animateTo(
            GeoPoint(stop.latitude, stop.longitude),
            maxOf(map.zoomLevelDouble, STOP_ZOOM),
            CAMERA_ANIMATION_MS
        )
    }

    private fun line(map: MapView, points: List<GeoPoint>, @ColorInt color: Int, widthDp: Float) =
        Polyline(map).apply {
            setPoints(points)
            outlinePaint.color = color
            outlinePaint.strokeWidth = widthDp * resources.displayMetrics.density
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setOnClickListener { _, _, _ -> true }
        }

    // --- Paradas ---

    private fun renderStops(stops: List<EditableStop>, draft: RecordingDraft) {
        val binding = _binding ?: return
        val context = requireContext()

        binding.summary.text = getString(
            R.string.new_route_summary,
            context.formatDistance(draft.distanceMeters),
            draft.durationMinutes(),
            stops.size
        )

        val keys = stops.map { it.key }
        if (keys != renderedKeys) {
            renderedKeys = keys
            val container = binding.stopsContainer
            container.removeAllViews()
            stopRows.keys.retainAll(keys.toSet())
            stops.forEach { stop ->
                container.addView(stopRows.getOrPut(stop.key) { createRow(stop.key) }.root)
            }
            drawStops(stops)
        }

        val primary = context.themeColor(com.google.android.material.R.attr.colorPrimary)
        val secondary = context.themeColor(com.google.android.material.R.attr.colorSecondary)
        val canRemove = stops.size > NewRouteViewModel.MIN_STOPS

        stops.forEachIndexed { index, stop ->
            val row = stopRows[stop.key] ?: return@forEachIndexed
            val endpoint = index == 0 || index == stops.lastIndex
            row.stopNumber.text = (index + 1).toString()
            row.stopNumber.backgroundTintList = ColorStateList.valueOf(if (endpoint) secondary else primary)

            // Mientras el usuario escribe no le pisamos el texto.
            if (!row.stopNameInput.hasFocus() && row.stopNameInput.text?.toString() != stop.name) {
                row.stopNameInput.setText(stop.name)
            }
            row.stopNameLayout.helperText = if (stop.resolving) getString(R.string.new_route_stop_resolving) else null
            row.stopNameLayout.placeholderText = getString(R.string.new_route_stop_default, index + 1)

            row.btnRemoveStop.isEnabled = canRemove
            row.btnRemoveStop.alpha = if (canRemove) 1f else DISABLED_ALPHA
        }

        // Origen y destino se proponen solos con la primera y la última parada.
        suggest(binding.originInput, stops.firstOrNull()?.name)
        suggest(binding.destinationInput, stops.lastOrNull()?.name)
    }

    private fun createRow(key: Long): ItemNewStopBinding {
        val row = ItemNewStopBinding.inflate(layoutInflater, binding.stopsContainer, false)
        // Todas las filas comparten ids: sin esto Android restauraría el mismo texto en todas.
        row.stopNameInput.isSaveEnabled = false
        row.stopNameLayout.isSaveEnabled = false

        row.stopNameInput.doAfterTextChanged { text ->
            if (row.stopNameInput.hasFocus()) viewModel.renameStop(key, text?.toString().orEmpty())
        }
        row.btnRemoveStop.setOnClickListener {
            if (!viewModel.removeStop(key)) showMessage(getString(R.string.new_route_min_stops))
        }
        row.stopNumber.setOnClickListener { focusStop(key, scrollToMap = true) }
        return row
    }

    private fun suggest(input: EditText, name: String?) {
        if (!name.isNullOrBlank() && input.text.isNullOrEmpty() && !input.hasFocus()) input.setText(name)
    }

    // --- Guardar o descartar ---

    private fun save() {
        hideKeyboard()
        val result = viewModel.save(
            NewRouteForm(
                name = binding.nameInput.text?.toString().orEmpty(),
                origin = binding.originInput.text?.toString().orEmpty(),
                destination = binding.destinationInput.text?.toString().orEmpty(),
                fare = binding.fareInput.text?.toString().orEmpty(),
                schedule = binding.scheduleInput.text?.toString().orEmpty()
            )
        )

        when (result) {
            is SaveResult.Saved -> {
                Toast.makeText(requireContext(), R.string.new_route_saved, Toast.LENGTH_LONG).show()
                // El inicio la traza en cuanto vuelve a aparecer.
                MapRouteRequests.request(result.routeId)
                findNavController().popBackStack()
            }
            is SaveResult.Invalid -> showErrors(result.fields)
            SaveResult.NoDraft -> findNavController().popBackStack()
        }
    }

    private fun showErrors(fields: Set<NewRouteField>) {
        val required = getString(R.string.new_route_required)
        binding.nameLayout.error = required.takeIf { NewRouteField.NAME in fields }
        binding.originLayout.error = required.takeIf { NewRouteField.ORIGIN in fields }
        binding.destinationLayout.error = required.takeIf { NewRouteField.DESTINATION in fields }
        binding.fareLayout.error = getString(R.string.new_route_fare_invalid).takeIf { NewRouteField.FARE in fields }
        if (NewRouteField.STOPS in fields) showMessage(getString(R.string.new_route_min_stops))

        val firstError = listOf(
            NewRouteField.NAME to binding.nameLayout,
            NewRouteField.ORIGIN to binding.originLayout,
            NewRouteField.DESTINATION to binding.destinationLayout,
            NewRouteField.FARE to binding.fareLayout
        ).firstOrNull { it.first in fields }?.second ?: return

        val area = Rect()
        firstError.getDrawingRect(area)
        binding.scroll.offsetDescendantRectToMyCoords(firstError, area)
        binding.scroll.smoothScrollTo(0, (area.top - binding.scroll.height / 4).coerceAtLeast(0))
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_route_leave_title)
            .setMessage(R.string.new_route_leave_message)
            .setNegativeButton(R.string.new_route_keep_editing, null)
            .setPositiveButton(R.string.new_route_leave) { _, _ ->
                if (_binding != null) findNavController().popBackStack()
            }
            .show()
    }

    private fun confirmDiscard() {
        val appContext = requireContext().applicationContext
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recording_discard_title)
            .setMessage(R.string.recording_discard_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.recording_discard_confirm) { _, _ ->
                RouteRecorderService.discard(appContext)
                if (_binding != null) findNavController().popBackStack()
            }
            .show()
    }

    // --- Utilidades ---

    private fun showMessage(text: String) {
        _binding?.let { Snackbar.make(it.root, text, Snackbar.LENGTH_SHORT).setAnchorView(it.bottomBar).show() }
    }

    private fun hideKeyboard() {
        val focused = requireActivity().currentFocus ?: return
        requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    // --- Ciclo de vida ---

    override fun onResume() {
        super.onResume()
        _binding?.previewMap?.onResume()
    }

    override fun onPause() {
        _binding?.previewMap?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding?.previewMap?.onDetach()
        stopRows.clear()
        renderedKeys = null
        stopLayer = null
        pathLayer = null
        framedPath = false
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val PREVIEW_ZOOM = 15.0
        const val STOP_ZOOM = 17.0
        const val CAMERA_ANIMATION_MS = 500L
        const val FRAME_BORDER_DP = 28f
        const val DISABLED_ALPHA = 0.38f
    }
}

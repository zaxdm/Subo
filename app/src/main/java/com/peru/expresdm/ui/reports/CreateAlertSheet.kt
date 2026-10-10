package com.peru.expresdm.ui.reports

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.peru.expresdm.R
import com.peru.expresdm.ai.ModerationService
import com.peru.expresdm.ai.ModerationVerdict
import com.peru.expresdm.data.AlertRepository
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.PlaceSearch
import com.peru.expresdm.data.PublishResult
import com.peru.expresdm.databinding.ItemFilterChipBinding
import com.peru.expresdm.databinding.SheetCreateAlertBinding
import com.peru.expresdm.ui.AlertStyle
import com.peru.expresdm.ui.currentLocation
import com.peru.expresdm.ui.hasAnyLocationPermission
import com.peru.expresdm.ui.isLocationEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hoja para publicar una alerta.
 *
 * La alerta se marca donde está el usuario: quien avisa de un choque o de un
 * atasco lo está viendo. El comentario, si lo hay, pasa por la moderación
 * antes de publicarse.
 */
class CreateAlertSheet : BottomSheetDialogFragment() {

    private enum class LocationProblem { NO_PERMISSION, SERVICES_OFF, NO_FIX }

    private var _binding: SheetCreateAlertBinding? = null
    private val binding get() = _binding!!

    private var selectedType: AlertType? = null
    private var location: Location? = null
    private var placeName: String? = null
    private var locationProblem: LocationProblem? = null
    private var locatingJob: Job? = null
    private var publishing = false

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { locate() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetCreateAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedType = savedInstanceState?.getString(STATE_TYPE)
            ?.let { name -> AlertType.entries.firstOrNull { it.name == name } }

        buildTypeChips()
        binding.btnEnableLocation.setOnClickListener { onLocationAction() }
        binding.btnPublish.setOnClickListener { publish() }
        binding.commentInput.doAfterTextChanged { _binding?.commentLayout?.error = null }
    }

    override fun onStart() {
        super.onStart()
        // El teclado empuja el botón hacia arriba en lugar de taparlo.
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onResume() {
        super.onResume()
        // También al volver de los ajustes, por si el usuario activó la ubicación.
        if (location == null && locatingJob?.isActive != true) locate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TYPE, selectedType?.name)
    }

    private fun buildTypeChips() {
        val context = requireContext()
        AlertType.entries.forEach { type ->
            val chip = ItemFilterChipBinding.inflate(layoutInflater, binding.typeChips, false).root
            chip.id = View.generateViewId()
            chip.tag = type
            chip.setText(AlertStyle.label(type))
            chip.isCheckedIconVisible = false
            chip.setChipIconResource(AlertStyle.icon(type))
            chip.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, AlertStyle.color(type)))
            chip.isChipIconVisible = true
            chip.isChecked = type == selectedType
            binding.typeChips.addView(chip)
        }

        binding.typeChips.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedType = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? AlertType }
            if (selectedType != null) _binding?.typeError?.isVisible = false
        }
    }

    // --- Ubicación ---

    private fun locate() {
        val binding = _binding ?: return
        val context = requireContext()
        locatingJob?.cancel()

        locationProblem = when {
            !context.hasAnyLocationPermission() -> LocationProblem.NO_PERMISSION
            !context.isLocationEnabled() -> LocationProblem.SERVICES_OFF
            else -> null
        }
        if (locationProblem != null) {
            renderLocation()
            return
        }

        binding.locationText.setText(R.string.create_alert_locating)
        binding.btnEnableLocation.isVisible = false

        locatingJob = viewLifecycleOwner.lifecycleScope.launch {
            val found = context.currentLocation()
            location = found
            locationProblem = if (found == null) LocationProblem.NO_FIX else null
            renderLocation()

            if (found != null) {
                placeName = PlaceSearch.streetName(found.latitude, found.longitude)
                renderLocation()
            }
        }
    }

    private fun renderLocation() {
        val binding = _binding ?: return
        val problem = locationProblem

        binding.locationText.text = when (problem) {
            LocationProblem.NO_PERMISSION, LocationProblem.SERVICES_OFF -> getString(R.string.create_alert_no_location)
            LocationProblem.NO_FIX -> getString(R.string.create_alert_no_fix)
            null -> placeName?.let { getString(R.string.create_alert_location, it) }
                ?: getString(R.string.create_alert_location_generic)
        }

        binding.btnEnableLocation.isVisible = problem != null
        binding.btnEnableLocation.setText(
            when (problem) {
                LocationProblem.NO_PERMISSION -> R.string.location_action_enable
                LocationProblem.SERVICES_OFF -> R.string.location_action_settings
                else -> R.string.create_alert_retry
            }
        )
    }

    private fun onLocationAction() {
        when (locationProblem) {
            LocationProblem.NO_PERMISSION -> requestLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            LocationProblem.SERVICES_OFF -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            LocationProblem.NO_FIX, null -> locate()
        }
    }

    // --- Publicar ---

    private fun publish() {
        if (publishing) return

        val type = selectedType
        if (type == null) {
            binding.typeError.isVisible = true
            return
        }

        // Mientras se busca la ubicación el texto ya dice "Buscando…".
        val here = location ?: return

        val comment = binding.commentInput.text?.toString()?.trim().orEmpty()
        setPublishing(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val verdict = if (comment.isEmpty()) ModerationVerdict.Allowed else ModerationService.review(comment)
            val binding = _binding ?: return@launch

            when (verdict) {
                ModerationVerdict.Allowed -> {
                    val published = AlertRepository.publish(
                        type = type,
                        latitude = here.latitude,
                        longitude = here.longitude,
                        comment = comment.ifEmpty { null },
                        placeName = placeName
                    )
                    when (published) {
                        is PublishResult.Published -> {
                            // Si aún no sabemos la calle, se completa en segundo plano.
                            AlertRepository.resolvePlaceName(published.alert.id)
                            parentFragmentManager.setFragmentResult(
                                RESULT_KEY,
                                bundleOf(RESULT_ALERT_ID to published.alert.id)
                            )
                            dismiss()
                        }

                        is PublishResult.Failed -> {
                            setPublishing(false)
                            binding.commentLayout.error = getString(
                                if (published.needsSignIn) R.string.publish_needs_account else R.string.publish_failed
                            )
                        }
                    }
                }

                is ModerationVerdict.RejectedLocally -> {
                    setPublishing(false)
                    binding.commentLayout.error = getString(R.string.moderation_local_reason, verdict.word)
                }

                is ModerationVerdict.RejectedByModel -> {
                    setPublishing(false)
                    binding.commentLayout.error = verdict.reason
                }
            }
        }
    }

    private fun setPublishing(busy: Boolean) {
        publishing = busy
        val binding = _binding ?: return
        binding.btnPublish.isEnabled = !busy
        binding.commentInput.isEnabled = !busy
        binding.btnPublish.setText(if (busy) R.string.moderation_checking else R.string.create_alert_publish)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "create_alert"
        private const val STATE_TYPE = "type"

        const val RESULT_KEY = "alert_published"
        const val RESULT_ALERT_ID = "alert_id"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                CreateAlertSheet().show(fragmentManager, TAG)
            }
        }
    }
}

package com.peru.expresdm.ui.settings

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.AppLanguage
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.ArrivalTone
import com.peru.expresdm.data.CustomSound
import com.peru.expresdm.data.ThemeMode
import com.peru.expresdm.databinding.FragmentSettingsBinding
import com.peru.expresdm.tracking.AlertWatchWorker
import com.peru.expresdm.tracking.ArrivalAlarmService
import com.peru.expresdm.tracking.ArrivalSignal
import com.peru.expresdm.tracking.ArrivalVoice
import com.peru.expresdm.tracking.PushRegistration
import com.peru.expresdm.ui.AlertStyle
import com.peru.expresdm.ui.themeColor

/**
 * Ajustes del aviso de bajada, de las alertas, de apariencia y de idioma.
 *
 * Cambiar el tema o el idioma recrea la Activity: Android necesita volver a
 * resolver los recursos. Por eso los valores se guardan antes de aplicarlos.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settings: AppSettings

    /** Evita reaccionar a los cambios que provoca el propio código al pintar. */
    private var bindingSelection = false

    /** El permiso de notificaciones de Android 13 en adelante; si lo niegan, no se insiste. */
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** El selector de archivos de Android, para añadir un sonido propio. */
    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && _binding != null) addCustomSound(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = AppSettings.get(requireContext())

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        renderCurrentSelection()
        renderAlertTypes()
        renderPalette()

        binding.arrivalDistanceGroup.setOnCheckedChangeListener { _, checkedId ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.arrivalDistanceMeters = when (checkedId) {
                R.id.arrivalDistance100 -> 100
                R.id.arrivalDistance500 -> 500
                else -> AppSettings.DEFAULT_ARRIVAL_DISTANCE
            }
        }

        binding.arrivalVibration.setOnCheckedChangeListener { _, checked ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.arrivalVibration = checked
        }

        binding.arrivalSound.setOnCheckedChangeListener { _, checked ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.arrivalSound = checked
        }

        binding.arrivalVoice.setOnCheckedChangeListener { _, checked ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.arrivalVoice = checked
        }

        binding.alertNotifications.setOnCheckedChangeListener { _, checked ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.alertNotifications = checked
            AlertWatchWorker.sync(requireContext())
            PushRegistration.sync(requireContext())
            // Sin permiso de notificaciones, el aviso no llegaría a verse.
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        renderTones()
        binding.arrivalToneGroup.setOnCheckedChangeListener { group, checkedId ->
            if (bindingSelection) return@setOnCheckedChangeListener
            val choice = group.findViewById<View>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            settings.arrivalSoundChoice = choice
            // Se oye al elegirlo: nadie escoge un sonido por el nombre.
            ArrivalSignal.play(requireContext(), vibrate = false, sound = true)
        }
        binding.btnAddSound.setOnClickListener {
            if (settings.customSounds.size >= AppSettings.MAX_CUSTOM_SOUNDS) {
                showMessage(getString(R.string.settings_arrival_tone_limit, AppSettings.MAX_CUSTOM_SOUNDS))
            } else {
                pickSound.launch(arrayOf("audio/*"))
            }
        }

        binding.btnTestArrival.setOnClickListener { testArrival() }

        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.themeMode = when (checkedId) {
                R.id.themeLight -> ThemeMode.LIGHT
                R.id.themeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

        binding.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            if (bindingSelection) return@setOnCheckedChangeListener
            settings.language = when (checkedId) {
                R.id.languageSpanish -> AppLanguage.SPANISH
                R.id.languageEnglish -> AppLanguage.ENGLISH
                else -> AppLanguage.SYSTEM
            }
        }
    }

    private fun renderCurrentSelection() {
        bindingSelection = true

        binding.arrivalDistanceGroup.check(
            when (settings.arrivalDistanceMeters) {
                100 -> R.id.arrivalDistance100
                500 -> R.id.arrivalDistance500
                else -> R.id.arrivalDistance200
            }
        )
        binding.arrivalVibration.isChecked = settings.arrivalVibration
        binding.arrivalSound.isChecked = settings.arrivalSound
        binding.arrivalVoice.isChecked = settings.arrivalVoice
        binding.alertNotifications.isChecked = settings.alertNotifications

        binding.themeGroup.check(
            when (settings.themeMode) {
                ThemeMode.LIGHT -> R.id.themeLight
                ThemeMode.DARK -> R.id.themeDark
                ThemeMode.SYSTEM -> R.id.themeSystem
            }
        )

        binding.languageGroup.check(
            when (settings.language) {
                AppLanguage.SPANISH -> R.id.languageSpanish
                AppLanguage.ENGLISH -> R.id.languageEnglish
                AppLanguage.SYSTEM -> R.id.languageSystem
            }
        )

        bindingSelection = false
    }

    /** Una casilla por tipo de alerta: los apagados dejan de salir en el mapa y en la lista. */
    private fun renderAlertTypes() {
        val enabled = settings.alertTypes.value
        binding.alertTypesContainer.removeAllViews()

        AlertType.entries.forEach { type ->
            val check = layoutInflater
                .inflate(R.layout.item_setting_check, binding.alertTypesContainer, false) as MaterialCheckBox
            check.setText(AlertStyle.label(type))
            check.isChecked = type in enabled
            // Sin id propio, Android restauraría todas las casillas con el estado de la última.
            check.isSaveEnabled = false
            check.setOnCheckedChangeListener { _, checked -> settings.setAlertTypeEnabled(type, checked) }
            binding.alertTypesContainer.addView(check)
        }
    }

    /** Los tonos de la app y, debajo, los que añadió el usuario. */
    private fun renderTones() {
        val custom = settings.customSounds
        val selected = settings.arrivalSoundChoice
        val options = ArrivalTone.entries.map { it.key to getString(labelOf(it)) } +
            custom.map { it.uri to it.name }

        bindingSelection = true
        binding.arrivalToneGroup.clearCheck()
        binding.arrivalToneGroup.removeAllViews()
        options.forEach { (choice, label) ->
            val radio = layoutInflater
                .inflate(R.layout.item_setting_choice, binding.arrivalToneGroup, false) as MaterialRadioButton
            radio.id = View.generateViewId()
            radio.tag = choice
            radio.text = label
            // El id se genera cada vez: Android no sabría a quién devolverle el estado.
            radio.isSaveEnabled = false
            if (ArrivalTone.fromKey(choice) == null) {
                radio.setOnLongClickListener {
                    removeCustomSound(choice)
                    true
                }
            }
            binding.arrivalToneGroup.addView(radio)
            if (choice == selected) binding.arrivalToneGroup.check(radio.id)
        }
        bindingSelection = false

        binding.customSoundsNote.isVisible = custom.isNotEmpty()
    }

    private fun addCustomSound(uri: Uri) {
        val context = requireContext()
        val choice = uri.toString()
        if (settings.customSounds.none { it.uri == choice }) {
            // Sin este permiso, el archivo dejaría de poder leerse al reiniciar el teléfono.
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                showMessage(getString(R.string.settings_arrival_tone_failed))
                return
            }
            settings.customSounds = settings.customSounds + CustomSound(choice, nameOf(uri))
        }
        settings.arrivalSoundChoice = choice
        renderTones()
        ArrivalSignal.play(context, vibrate = false, sound = true)
    }

    private fun removeCustomSound(choice: String) {
        try {
            requireContext().contentResolver
                .releasePersistableUriPermission(Uri.parse(choice), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Ya no lo teníamos: no hay nada que soltar.
        }
        settings.customSounds = settings.customSounds.filterNot { it.uri == choice }
        if (settings.arrivalSoundChoice == choice) settings.arrivalSoundChoice = ArrivalTone.DEFAULT.key
        ArrivalSignal.stop()
        renderTones()
        showMessage(getString(R.string.settings_arrival_tone_removed))
    }

    /** El nombre del archivo sin la extensión, que es como lo reconoce quien lo eligió. */
    private fun nameOf(uri: Uri): String {
        val name = requireContext().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: getString(R.string.settings_arrival_tone_custom)
    }

    @StringRes
    private fun labelOf(tone: ArrivalTone): Int = when (tone) {
        ArrivalTone.BELL -> R.string.settings_arrival_tone_bell
        ArrivalTone.CHIME -> R.string.settings_arrival_tone_chime
        ArrivalTone.BEEP -> R.string.settings_arrival_tone_beep
        ArrivalTone.SIREN -> R.string.settings_arrival_tone_siren
        ArrivalTone.MELODY -> R.string.settings_arrival_tone_melody
    }

    private fun showMessage(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    /** Hace vibrar y sonar el aviso tal como quedó configurado. */
    private fun testArrival() {
        val vibrate = settings.arrivalVibration
        val sound = settings.arrivalSound
        ArrivalSignal.play(requireContext(), vibrate = vibrate, sound = sound)
        if (settings.arrivalVoice) {
            ArrivalVoice.speak(
                requireContext(),
                getString(R.string.arrival_arrived, getString(R.string.settings_arrival_voice_sample)),
                delayMs = if (sound) ArrivalAlarmService.VOICE_AFTER_SOUND_MS else 0
            )
        }

        val message = if (vibrate || sound || settings.arrivalVoice) {
            getString(R.string.settings_arrival_test_done, settings.arrivalDistanceMeters)
        } else {
            getString(R.string.settings_arrival_test_silent)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Muestra los colores del tema activo, para ver el efecto del cambio. */
    private fun renderPalette() {
        val context = requireContext()
        val swatches = listOf(
            // El amarillo de marca y su versión suave; el negro ya sale al final.
            binding.swatchPrimary to com.google.android.material.R.attr.colorTertiary,
            binding.swatchTertiary to com.google.android.material.R.attr.colorPrimaryContainer,
            binding.swatchSecondary to com.google.android.material.R.attr.colorSecondary,
            binding.swatchNeutral to com.google.android.material.R.attr.colorOnSurfaceVariant,
            binding.swatchSurface to com.google.android.material.R.attr.colorSurfaceVariant,
            binding.swatchInk to com.google.android.material.R.attr.colorOnSurface
        )

        swatches.forEach { (view, attr) ->
            view.backgroundTintList = ColorStateList.valueOf(context.themeColor(attr))
        }
    }

    override fun onDestroyView() {
        // Un sonido de prueba no sigue sonando en otra pantalla.
        ArrivalSignal.stop()
        _binding = null
        super.onDestroyView()
    }
}

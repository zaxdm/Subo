package com.peru.expresdm.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Por dónde tiene que sonar lo que dice o hace sonar la app.
 *
 * Android saca las alarmas por el altavoz del teléfono aunque haya audífonos
 * puestos (para que nadie se quede dormido por tenerlos sobre la mesa). Para
 * un aviso de bajada eso es justo lo contrario de lo que se quiere: quien va
 * en el micro con audífonos tiene que oírlo ahí, y los demás pasajeros, no.
 * Así que con audífonos todo suena como música, que sí va solo por ellos.
 */
object AudioRoute {

    private val HEADPHONES = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
    }

    /** Hay audífonos puestos: de cable, USB o Bluetooth. */
    fun headphonesConnected(context: Context): Boolean = try {
        context.applicationContext.getSystemService<AudioManager>()
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type in HEADPHONES } == true
    } catch (_: RuntimeException) {
        false
    }

    /**
     * Cómo debe sonar algo. [urgent] es lo que tiene que oírse aunque el
     * teléfono esté en silencio (el aviso de bajada): suena como alarma, salvo
     * con audífonos, que suena por ellos.
     */
    fun attributes(context: Context, urgent: Boolean, speech: Boolean): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(
                if (urgent && !headphonesConnected(context)) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
            )
            .setContentType(
                if (speech) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_SONIFICATION
            )
            .build()
}

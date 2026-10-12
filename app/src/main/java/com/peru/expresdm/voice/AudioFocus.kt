package com.peru.expresdm.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService

/**
 * El turno para sonar.
 *
 * Android no calla a nadie por su cuenta: si la app habla sin pedir el turno,
 * la música de otra app sigue sonando encima y no se entiende nada. Pidiéndolo,
 * la música se pausa mientras la app habla o suena el aviso de bajada, y sigue
 * sola al terminar.
 *
 * El aviso de bajada es un tono y, detrás, una voz: cada uno pide el turno con
 * su nombre y no se devuelve hasta que lo sueltan los dos, para que la música
 * no vuelva un instante entre uno y otra.
 */
object AudioFocus {

    private val holders = mutableSetOf<String>()
    private var manager: AudioManager? = null
    private var request: AudioFocusRequest? = null

    // Quien quite el turno a la app (una llamada) no necesita respuesta: lo que
    // suena aquí son frases y tonos de unos segundos.
    private val listener = AudioManager.OnAudioFocusChangeListener { }

    /** [who] va a sonar, con [attributes]: lo demás se pausa hasta que lo suelte. */
    @Synchronized
    fun hold(context: Context, who: String, attributes: AudioAttributes) {
        val first = holders.isEmpty()
        holders += who
        if (!first) return
        val audio = context.applicationContext.getSystemService<AudioManager>() ?: return
        manager = audio
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val wanted = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                request = wanted
                audio.requestAudioFocus(wanted)
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: RuntimeException) {
            // Sin turno se suena igual, encima de lo que haya.
        }
    }

    /** [who] ya terminó. Cuando no queda nadie sonando, lo demás sigue. */
    @Synchronized
    fun release(who: String) {
        if (!holders.remove(who) || holders.isNotEmpty()) return
        val audio = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                request?.let(audio::abandonAudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(listener)
            }
        } catch (_: RuntimeException) {
            // Nada que devolver.
        }
        request = null
    }
}

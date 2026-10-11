package com.peru.expresdm.tracking

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Lee en voz alta el aviso de bajada.
 *
 * Para quien no puede mirar la pantalla —porque no ve bien o porque va de pie
 * agarrado al pasamanos—, un sonido dice "algo pasa" pero no qué. La voz dice
 * a dónde se está llegando.
 */
object ArrivalVoice {

    private const val TAG = "ArrivalVoice"
    private const val UTTERANCE_ID = "aviso_bajada"

    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    /** Dice [text] pasado [delayMs], para no pisarse con el tono del aviso. */
    fun speak(context: Context, text: String, delayMs: Long = 0) {
        val appContext = context.applicationContext
        handler.postDelayed({ start(appContext, text) }, delayMs)
    }

    private fun start(context: Context, text: String) {
        release()
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            val tts = created
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Log.w(TAG, "El teléfono no tiene voz disponible")
                release()
                return@TextToSpeech
            }
            // La voz, en el idioma de la app; si el teléfono no lo tiene, usa el suyo.
            tts.setLanguage(Locale.getDefault())
            // Como alarma: se oye aunque el teléfono esté en silencio, igual que el tono.
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = handler.post { release() }.let { }

                @Deprecated("Obligatorio antes de API 21")
                override fun onError(utteranceId: String?) = handler.post { release() }.let { }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
        engine = created
    }

    private fun release() {
        engine?.shutdown()
        engine = null
    }
}

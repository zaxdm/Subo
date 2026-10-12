package com.peru.expresdm.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * La voz de la app: lee en alto el aviso de bajada, la guía del viaje y las
 * respuestas del asistente, con la voz que tenga el teléfono.
 *
 * Para quien no puede mirar la pantalla —porque no ve bien o porque va de pie
 * agarrado al pasamanos—, un sonido dice "algo pasa" pero no qué. La voz sí.
 *
 * Habla de una cosa cada vez: lo nuevo corta lo que se estuviera diciendo.
 */
object Speaker {

    private const val TAG = "Speaker"
    private const val UTTERANCE_ID = "subo_voz"

    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var pending: Runnable? = null

    /**
     * Dice [text] pasado [delayMs] (para no pisarse con un tono).
     *
     * Con [asAlarm] suena como una alarma: se oye aunque el teléfono esté en
     * silencio, que es lo que hace falta para no pasarse de parada. Sin él
     * suena con el volumen de la música, como cualquier asistente.
     *
     * [onDone] se llama al terminar de hablar, también si no se pudo.
     */
    fun speak(
        context: Context,
        text: String,
        asAlarm: Boolean = true,
        delayMs: Long = 0,
        onDone: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        pending?.let(handler::removeCallbacks)
        val job = Runnable { start(appContext, text, asAlarm, onDone) }
        pending = job
        handler.postDelayed(job, delayMs)
    }

    /** Calla lo que se esté diciendo o fuera a decirse. No avisa a su `onDone`. */
    fun stop() {
        pending?.let(handler::removeCallbacks)
        pending = null
        release()
    }

    private fun start(context: Context, text: String, asAlarm: Boolean, onDone: (() -> Unit)?) {
        release()
        var created: TextToSpeech? = null
        // Solo termina lo que sigue siendo lo último pedido: si otra frase lo
        // cortó, su final ya no le importa a nadie.
        fun finish() = handler.post {
            if (engine === created) {
                release()
                onDone?.invoke()
            }
        }
        created = TextToSpeech(context) { status ->
            val tts = created
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Log.w(TAG, "El teléfono no tiene voz disponible")
                finish()
                return@TextToSpeech
            }
            // La voz, en el idioma de la app; si el teléfono no lo tiene, usa el suyo.
            tts.setLanguage(Locale.getDefault())
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (asAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    finish()
                }

                @Deprecated("Obligatorio antes de API 21")
                override fun onError(utteranceId: String?) {
                    finish()
                }
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) != TextToSpeech.SUCCESS) finish()
        }
        engine = created
    }

    private fun release() {
        engine?.shutdown()
        engine = null
    }
}

package com.peru.expresdm.voice

import android.content.Context
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

    /** Con qué nombre pide la voz el turno para sonar: ver [AudioFocus]. */
    private const val FOCUS = "voz"

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
     * Con audífonos puestos suena siempre por ellos: ver [AudioRoute].
     *
     * Mientras habla, la música de otras apps se pausa: ver [AudioFocus].
     *
     * [onWord] avisa de cada palabra que se va diciendo (Android 8 en
     * adelante), para mover algo al ritmo de la voz. [onDone] se llama al
     * terminar de hablar, también si no se pudo.
     */
    fun speak(
        context: Context,
        text: String,
        asAlarm: Boolean = true,
        delayMs: Long = 0,
        onWord: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        pending?.let(handler::removeCallbacks)
        // El turno se pide ya, no al empezar a hablar: si antes suena un tono,
        // la música no vuelve en el hueco entre los dos.
        AudioFocus.hold(appContext, FOCUS, AudioRoute.attributes(appContext, urgent = asAlarm, speech = true))
        val job = Runnable { start(appContext, text, asAlarm, onWord, onDone) }
        pending = job
        handler.postDelayed(job, delayMs)
    }

    /** Calla lo que se esté diciendo o fuera a decirse. No avisa a su `onDone`. */
    fun stop() {
        pending?.let(handler::removeCallbacks)
        pending = null
        release()
        AudioFocus.release(FOCUS)
    }

    private fun start(
        context: Context,
        text: String,
        asAlarm: Boolean,
        onWord: (() -> Unit)?,
        onDone: (() -> Unit)?
    ) {
        release()
        var created: TextToSpeech? = null
        // Solo termina lo que sigue siendo lo último pedido: si otra frase lo
        // cortó, su final ya no le importa a nadie.
        fun finish() = handler.post {
            if (engine === created) {
                release()
                AudioFocus.release(FOCUS)
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
            tts.setAudioAttributes(AudioRoute.attributes(context, urgent = asAlarm, speech = true))
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    if (onWord != null) handler.post { if (engine === created) onWord() }
                }

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

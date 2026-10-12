package com.peru.expresdm.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Escucha lo que dice el usuario y lo devuelve escrito, con el reconocimiento
 * de voz del propio teléfono: no manda el audio al servidor de Subo.
 *
 * Hay que usarlo siempre desde el hilo principal, que es lo que pide Android.
 */
class VoiceInput(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Cambia con cada escucha y cada vez que se cancela: así no se atiende a una vieja. */
    private var session = 0

    /** `true` mientras se espera a que el usuario hable o termine de hablar. */
    var isListening = false
        private set

    /**
     * Escucha una frase. [onPartial] va recibiendo lo que se entiende mientras
     * habla y [onLevel] lo fuerte que suena la voz, de 0 a 1; [onResult] recibe
     * la frase entera, o `null` si no se oyó nada o no se pudo escuchar
     * ([failed] distingue el fallo del silencio).
     *
     * El teléfono suele dar varias formas de escribir lo que oyó: [pick] elige
     * cuál vale (por defecto la primera, la más probable).
     */
    fun listen(
        onPartial: (String) -> Unit = {},
        onLevel: (Float) -> Unit = {},
        pick: (List<String>) -> String? = { it.firstOrNull() },
        onResult: (text: String?, failed: Boolean) -> Unit
    ) = start(onPartial, onLevel, pick, onResult, retries = CONNECTION_RETRIES)

    private fun start(
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        pick: (List<String>) -> String?,
        onResult: (text: String?, failed: Boolean) -> Unit,
        retries: Int
    ) {
        // Cada escucha tiene su número: lo que llegue de una anterior se ignora.
        val mine = ++session
        val speech = if (canListen(appContext)) recognizer ?: create().also { recognizer = it } else null
        if (speech == null) {
            isListening = false
            onResult(null, true)
            return
        }
        isListening = true
        fun deliver(text: String?, failed: Boolean) {
            if (mine != session) return
            session++
            isListening = false
            onResult(text?.trim()?.takeIf { it.isNotEmpty() }, failed)
        }

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit

            // El teléfono lo da en decibelios, más o menos de -2 (silencio) a 10 (voz fuerte).
            override fun onRmsChanged(rmsdB: Float) {
                if (mine == session) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if (mine == session) pick(heard(partialResults))?.takeIf { it.isNotBlank() }?.let(onPartial)
            }

            override fun onResults(results: Bundle?) = deliver(pick(heard(results)), false)

            override fun onError(error: Int) {
                if (mine != session) return
                when {
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        deliver(null, false)

                    // Se cortó la conexión con el servicio de voz del teléfono
                    // (pasa al reabrirlo muy seguido): se suelta, se vuelve a
                    // conectar y se escucha de nuevo, sin molestar al usuario.
                    error in CONNECTION_LOST && retries > 0 -> {
                        Log.i(TAG, "El servicio de voz se cortó (código $error): se reintenta")
                        destroy()
                        val retry = ++session
                        handler.postDelayed({
                            if (retry == session) start(onPartial, onLevel, pick, onResult, retries - 1)
                        }, RETRY_DELAY_MS)
                    }

                    else -> {
                        Log.i(TAG, "El reconocimiento de voz falló con el código $error")
                        deliver(null, true)
                    }
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        // "Subo" no es una palabra que el teléfono espere oír sola: se le da de pista.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, arrayListOf("Subo", "oye Subo"))
        }
        try {
            speech.startListening(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo empezar a escuchar", e)
            deliver(null, true)
        }
    }

    /** Deja de escuchar sin entregar nada. La conexión con el servicio de voz se conserva. */
    fun cancel() {
        session++
        isListening = false
        try {
            recognizer?.cancel()
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo parar el reconocimiento de voz", e)
        }
    }

    /** Deja de escuchar y suelta el servicio de voz: al terminar de usarlo. */
    fun release() {
        cancel()
        destroy()
    }

    private fun destroy() {
        recognizer?.let { speech ->
            try {
                speech.setRecognitionListener(null)
                speech.destroy()
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se pudo soltar el reconocimiento de voz", e)
            }
        }
        recognizer = null
    }

    private fun create(): SpeechRecognizer? = try {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    } catch (e: RuntimeException) {
        Log.w(TAG, "El teléfono no dejó crear el reconocimiento de voz", e)
        null
    }

    private fun heard(results: Bundle?): List<String> =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

    /** El idioma de la app; el español sin país se entiende como el de Perú. */
    private fun language(): String {
        val locale = Locale.getDefault()
        return if (locale.language == "es" && locale.country.isEmpty()) "es-PE" else locale.toLanguageTag()
    }

    companion object {
        private const val TAG = "VoiceInput"

        /** Los códigos con los que el teléfono dice que perdió su servicio de voz, o que aún está ocupado. */
        private val CONNECTION_LOST = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            11 // ERROR_SERVER_DISCONNECTED, de Android 12 en adelante
        )
        private const val CONNECTION_RETRIES = 2
        private const val MAX_ALTERNATIVES = 5
        private const val RETRY_DELAY_MS = 300L

        /** El teléfono tiene reconocimiento de voz (casi todos, con la app de Google). */
        fun isAvailable(context: Context): Boolean = try {
            SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
        } catch (_: RuntimeException) {
            false
        }

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        fun canListen(context: Context): Boolean = isAvailable(context) && hasPermission(context)
    }
}

package com.peru.expresdm.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    /** `true` mientras se espera a que el usuario hable o termine de hablar. */
    var isListening = false
        private set

    /**
     * Escucha una frase. [onPartial] va recibiendo lo que se entiende mientras
     * habla; [onResult] recibe la frase entera, o `null` si no se oyó nada o
     * no se pudo escuchar ([failed] distingue el fallo del silencio).
     */
    fun listen(onPartial: (String) -> Unit = {}, onResult: (text: String?, failed: Boolean) -> Unit) {
        if (!canListen(appContext)) {
            onResult(null, true)
            return
        }
        cancel()
        val speech = create()
        if (speech == null) {
            onResult(null, true)
            return
        }
        recognizer = speech
        isListening = true
        var delivered = false
        fun deliver(text: String?, failed: Boolean) {
            if (delivered) return
            delivered = true
            isListening = false
            onResult(text?.trim()?.takeIf { it.isNotEmpty() }, failed)
        }

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.takeIf { it.isNotBlank() }?.let(onPartial)
            }

            override fun onResults(results: Bundle?) = deliver(best(results), false)

            override fun onError(error: Int) {
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                // Algunos teléfonos mandan un segundo error tras el silencio: ese ya no cuenta.
                if (!silence && !delivered) Log.i(TAG, "El reconocimiento de voz falló con el código $error")
                deliver(null, !silence)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        try {
            speech.startListening(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo empezar a escuchar", e)
            deliver(null, true)
        }
    }

    /** Deja de escuchar sin entregar nada. */
    fun cancel() {
        isListening = false
        recognizer?.let { speech ->
            try {
                speech.setRecognitionListener(null)
                speech.cancel()
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

    private fun best(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    /** El idioma de la app; el español sin país se entiende como el de Perú. */
    private fun language(): String {
        val locale = Locale.getDefault()
        return if (locale.language == "es" && locale.country.isEmpty()) "es-PE" else locale.toLanguageTag()
    }

    companion object {
        private const val TAG = "VoiceInput"

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

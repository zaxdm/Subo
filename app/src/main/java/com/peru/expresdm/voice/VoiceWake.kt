package com.peru.expresdm.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.Normalizer

/** Reconoce la palabra con la que se llama al asistente. Lógica pura, para probarla. */
object WakeWord {

    /**
     * Cómo suele escribir el teléfono "Subo" cuando lo oye: a veces lo parte
     * o le cambia una letra.
     */
    private val WORDS = listOf("subo", "zubo", "suvo", "su vo", "su bo")

    private val GREETINGS = setOf("oye", "hey", "ey", "hola", "ok", "okay")

    /**
     * Si en [heard] se llamó al asistente, devuelve lo que se dijo después
     * ("oye subo, qué micro va al centro" → "qué micro va al centro"; solo
     * "subo" → cadena vacía). `null` si no se le llamó.
     *
     * Solo vale al principio de la frase: "ahí me subo yo" no es llamarlo.
     */
    fun match(heard: String): String? {
        val words = heard.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val plain = words.map(::plain)
        var start = 0
        if (plain.getOrNull(0) in GREETINGS) start = 1
        for (word in WORDS) {
            val parts = word.split(' ')
            if (plain.drop(start).take(parts.size) == parts) {
                return words.drop(start + parts.size).joinToString(" ").trim(' ', ',', '.', ':', '¿', '¡')
            }
        }
        return null
    }

    /** En minúsculas, sin tildes ni signos: "¡Subo!," → "subo". */
    private fun plain(word: String): String =
        Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
}

/**
 * Activación por voz (Premium): con el mapa a la vista, escucha hasta oír
 * «Subo» y entonces avisa con lo que se haya dicho después.
 *
 * Solo escucha mientras está encendida ([start]) y nada de lo que oye sale del
 * reconocimiento de voz del teléfono: lo que no empieza por «Subo» se tira.
 * Android enseña su indicador de micrófono mientras tanto.
 */
class VoiceWake(
    context: Context,
    /** Mientras devuelva `true` no se escucha (el asistente abierto, por ejemplo). */
    private val isBusy: () -> Boolean,
    private val onWake: (said: String) -> Unit
) {

    private val input = VoiceInput(context)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var failures = 0

    private val cycle = Runnable { listenOnce() }

    fun start() {
        if (running) return
        running = true
        failures = 0
        handler.post(cycle)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(cycle)
        input.cancel()
    }

    private fun listenOnce() {
        if (!running) return
        if (isBusy()) {
            handler.postDelayed(cycle, BUSY_RETRY_MS)
            return
        }
        input.listen { text, failed ->
            if (!running) return@listen
            val said = text?.let(WakeWord::match)
            if (said != null) {
                failures = 0
                onWake(said)
                // Quien recibe el aviso abre el asistente: se vuelve a escuchar cuando lo cierre.
                handler.postDelayed(cycle, BUSY_RETRY_MS)
                return@listen
            }
            failures = if (failed) failures + 1 else 0
            // Si el reconocimiento falla una y otra vez (sin internet, sin
            // servicio), se le da un respiro en vez de insistir sin parar.
            handler.postDelayed(cycle, if (failures >= MAX_FAILURES) BACKOFF_MS else RESTART_MS)
        }
    }

    private companion object {
        const val RESTART_MS = 350L
        const val BUSY_RETRY_MS = 1_500L
        const val MAX_FAILURES = 4
        const val BACKOFF_MS = 30_000L
    }
}

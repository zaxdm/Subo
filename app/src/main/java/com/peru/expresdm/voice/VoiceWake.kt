package com.peru.expresdm.voice

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import java.text.Normalizer

/** Reconoce la palabra con la que se llama al asistente. Lógica pura, para probarla. */
object WakeWord {

    /**
     * Cómo suele escribir el teléfono "Subo" cuando lo oye: a veces lo parte
     * o le cambia una letra.
     */
    private val WORDS = listOf(
        "subo", "zubo", "suvo", "subu", "sugo", "tsubo", "supo", "tubo", "su vo", "su bo", "su voz", "su bus"
    )

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
    /** Ya se oyó «Subo» y se sigue escuchando el resto: [soFar] es lo que va después. */
    private val onHearing: (soFar: String) -> Unit = {},
    /** Lo fuerte que suena la voz, de 0 a 1, desde que se oyó «Subo». */
    private val onLevel: (Float) -> Unit = {},
    private val onWake: (said: String) -> Unit
) {

    private val appContext = context.applicationContext
    private val input = VoiceInput(context)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var failures = 0

    private val cycle = Runnable { listenOnce() }

    fun start() {
        if (running) return
        running = true
        failures = 0
        quietBeeps(true)
        handler.post(cycle)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(cycle)
        input.release()
        quietBeeps(false)
    }

    /**
     * El reconocimiento de voz del teléfono pita cada vez que empieza y termina
     * de escuchar, y aquí lo hace cada pocos segundos: mientras se espera a
     * «Subo» se silencia ese pitido (suena como una notificación).
     *
     * Se apunta que el silencio lo puso la app, para quitarlo aunque se cerrara
     * a medias, y no tocar el de quien ya tenía el teléfono callado.
     */
    private fun quietBeeps(quiet: Boolean) {
        val audio = appContext.getSystemService<AudioManager>() ?: return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            if (quiet) {
                if (prefs.getBoolean(KEY_MUTED, false) || audio.isStreamMute(BEEP_STREAM)) return
                audio.adjustStreamVolume(BEEP_STREAM, AudioManager.ADJUST_MUTE, 0)
                prefs.edit().putBoolean(KEY_MUTED, true).apply()
            } else if (prefs.getBoolean(KEY_MUTED, false)) {
                audio.adjustStreamVolume(BEEP_STREAM, AudioManager.ADJUST_UNMUTE, 0)
                prefs.edit().putBoolean(KEY_MUTED, false).apply()
            }
        } catch (e: SecurityException) {
            // Con "No molestar" el teléfono no deja tocar ese volumen: se queda el pitido.
            Log.i(TAG, "No se pudo silenciar el pitido del reconocimiento de voz", e)
        }
    }

    private fun listenOnce() {
        if (!running) return
        if (isBusy()) {
            handler.postDelayed(cycle, BUSY_RETRY_MS)
            return
        }
        // Lo que va detrás de «Subo» según lo último que se entendió a medias:
        // si el teléfono no llega a dar la frase entera, vale esto.
        var soFar: String? = null
        input.listen(
            onPartial = { heard ->
                WakeWord.match(heard)?.let {
                    soFar = it
                    if (running) onHearing(it)
                }
            },
            onLevel = { level -> if (soFar != null && running) onLevel(level) },
            // De las formas en que el teléfono escribe lo que oyó, la que nombre a Subo.
            pick = { heard -> heard.firstOrNull { WakeWord.match(it) != null } ?: heard.firstOrNull() },
            // «Subo» a secas es muy corto: con el modo de frases largas el teléfono lo descarta.
            shortPhrases = true
        ) { text, failed ->
            if (!running) return@listen
            val said = text?.let(WakeWord::match) ?: soFar
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
        const val TAG = "VoiceWake"
        const val PREFS = "voice_wake"
        const val KEY_MUTED = "beeps_muted"

        /** Por dónde suena el pitido del reconocimiento de voz de Google. */
        const val BEEP_STREAM = AudioManager.STREAM_NOTIFICATION

        const val RESTART_MS = 350L
        const val BUSY_RETRY_MS = 1_500L
        const val MAX_FAILURES = 4
        const val BACKOFF_MS = 10_000L
    }
}

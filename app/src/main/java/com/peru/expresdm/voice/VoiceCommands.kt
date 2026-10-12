package com.peru.expresdm.voice

import java.text.Normalizer

/** Lo que el usuario pidió hablando. */
sealed interface VoiceCommand {
    /** Ir a un lugar: se busca y se enseña cómo llegar. */
    data class GoTo(val place: String) : VoiceCommand

    /** Activar el aviso de llegada del viaje que se ve en el mapa. */
    data object ArrivalOn : VoiceCommand

    data object ArrivalOff : VoiceCommand

    /** Compartir el viaje (en vivo, si se puede). */
    data object ShareTrip : VoiceCommand

    data object StopSharing : VoiceCommand

    /** Decir en qué calle está y centrar el mapa ahí. */
    data object WhereAmI : VoiceCommand

    /** Callar a la app y cerrar el asistente. */
    data object Close : VoiceCommand

    /** Cualquier otra cosa: una pregunta para el asistente. */
    data class Ask(val question: String) : VoiceCommand
}

/**
 * Entiende las órdenes que se dan hablando. Lógica pura, para probarla.
 *
 * Son pocas y fijas a propósito: lo que no encaja en ninguna se le pregunta
 * al asistente, que es quien entiende frases libres.
 */
object VoiceCommands {

    /** Con qué se pide ir a un sitio; lo que viene después es el sitio. */
    private val GO_TO = listOf(
        "llevame a", "llevame al", "quiero ir a", "quiero ir al", "vamos a", "vamos al",
        "voy a", "voy al", "ir a", "ir al", "como llegar a", "como llegar al"
    )

    private val ARRIVAL_ON = listOf(
        "avisame al llegar", "avisame cuando llegue", "avisame para bajar", "avisame", "activa el aviso",
        "activar aviso", "pon el aviso", "inicia la guia", "guiame"
    )

    private val ARRIVAL_OFF = listOf(
        "quita el aviso", "cancela el aviso", "desactiva el aviso", "apaga el aviso", "ya no me avises",
        "cancelar aviso", "quitar aviso"
    )

    private val STOP_SHARING = listOf(
        "deja de compartir", "dejar de compartir", "ya no compartas", "para de compartir", "termina de compartir"
    )

    private val SHARE = listOf(
        "comparte mi viaje", "compartir mi viaje", "comparte el viaje", "compartir viaje", "comparte mi ubicacion",
        "comparte en vivo"
    )

    private val WHERE = listOf("donde estoy", "en que calle estoy", "cual es mi ubicacion", "mi ubicacion")

    private val CLOSE = listOf(
        "cierra", "cerrar", "silencio", "callate", "calla", "eso es todo", "nada mas", "gracias", "muchas gracias",
        "adios", "chau", "ya esta", "para"
    )

    fun parse(said: String): VoiceCommand {
        val words = said.trim()
        val plain = plain(words)

        GO_TO.firstOrNull { plain.startsWith("$it ") }?.let { start ->
            // El sitio se toma de la frase original, con sus tildes y mayúsculas.
            val place = words.split(Regex("\\s+")).drop(start.split(' ').size).joinToString(" ")
                .trim(' ', '.', ',', '?', '¿', '!', '¡')
            if (place.isNotEmpty()) return VoiceCommand.GoTo(place)
        }
        return when {
            // "Deja de compartir" antes que "comparte", y "quita el aviso" antes que "avísame".
            STOP_SHARING.any { it in plain } -> VoiceCommand.StopSharing
            ARRIVAL_OFF.any { it in plain } -> VoiceCommand.ArrivalOff
            SHARE.any { it in plain } -> VoiceCommand.ShareTrip
            ARRIVAL_ON.any { plain == it || plain.startsWith("$it ") } -> VoiceCommand.ArrivalOn
            WHERE.any { it in plain } -> VoiceCommand.WhereAmI
            // Solo si es toda la frase: "para ir al centro…" no es mandar callar.
            plain in CLOSE -> VoiceCommand.Close
            else -> VoiceCommand.Ask(words)
        }
    }

    /**
     * Lo que se contesta a "¿a dónde quieres ir?": además de las órdenes de
     * siempre, un lugar dicho a secas ("Huanchaco", "al Mall Plaza") es a donde
     * se quiere ir. Una pregunta sigue siendo para el asistente.
     */
    fun parseDestination(said: String): VoiceCommand {
        val command = parse(said)
        if (command !is VoiceCommand.Ask) return command
        val words = plain(said).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_PLACE_WORDS || '?' in said || words.first() in QUESTION_STARTS) {
            return command
        }
        val skip = if (words.first() in TOWARDS) 1 else 0
        val place = said.trim().split(Regex("\\s+")).drop(skip).joinToString(" ")
            .trim(' ', '.', ',', '?', '¿', '!', '¡')
        return if (place.isEmpty()) command else VoiceCommand.GoTo(place)
    }

    /** Un lugar se dice en pocas palabras; una frase más larga es otra cosa. */
    private const val MAX_PLACE_WORDS = 6

    private val TOWARDS = setOf("a", "al", "hacia", "hasta", "para")

    private val QUESTION_STARTS = setOf(
        "que", "cual", "cuales", "como", "cuanto", "cuanta", "cuantos", "cuantas", "donde", "cuando", "quien",
        "por", "hay", "puedo", "puedes", "sabes", "dime", "necesito", "quiero", "pasa", "pasan", "es", "si", "no"
    )

    /** En minúsculas, sin tildes ni signos y con un solo espacio entre palabras. */
    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")
}

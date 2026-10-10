package com.peru.expresdm.ui

import java.util.Locale

/**
 * Las iniciales que se dibujan en el círculo del perfil.
 *
 * Es el retrato del usuario mientras no haya fotos: una o dos letras de su
 * nombre. Si no hay nombre todavía, un signo de interrogación deja claro que
 * nadie ha iniciado sesión.
 */
object Initials {

    const val PLACEHOLDER = "?"

    fun of(name: String?): String {
        val words = name.orEmpty().trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val letters = words.take(2).mapNotNull { word ->
            // Un nombre que empieza por emoji o puntuación no da inicial:
            // mejor saltárselo que pintar un símbolo raro.
            word.firstOrNull { it.isLetterOrDigit() }
        }
        return if (letters.isEmpty()) {
            PLACEHOLDER
        } else {
            letters.joinToString("").uppercase(Locale.getDefault())
        }
    }

    private val WHITESPACE = Regex("\\s+")
}

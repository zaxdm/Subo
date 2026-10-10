package com.peru.expresdm.backend.auth

/**
 * El nombre con el que se ve a un usuario.
 *
 * Es lo único suyo que llega a los demás (quién publicó una alerta, quién grabó
 * una ruta), así que se limpia antes de guardarlo: sin espacios de sobra y con
 * un largo razonable, para que nadie ocupe media pantalla con su nombre.
 */
object DisplayNames {

    const val MAX_LENGTH = 40

    private val EXTRA_SPACES = Regex("\\s+")

    fun clean(raw: String): String = raw.trim().replace(EXTRA_SPACES, " ")

    /** Motivo por el que un nombre no sirve; `null` si está bien. */
    fun problem(raw: String): DisplayNameProblem? {
        val name = clean(raw)
        return when {
            name.isEmpty() -> DisplayNameProblem.EMPTY
            name.length > MAX_LENGTH -> DisplayNameProblem.TOO_LONG
            else -> null
        }
    }
}

enum class DisplayNameProblem { EMPTY, TOO_LONG }

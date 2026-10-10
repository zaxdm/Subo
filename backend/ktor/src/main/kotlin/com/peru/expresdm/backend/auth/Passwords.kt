package com.peru.expresdm.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Contraseñas.
 *
 * Nunca se guarda la contraseña: se guarda su huella con bcrypt, que es lenta a
 * propósito. Si alguien se llevara la base de datos, no tendría las contraseñas.
 */
object Passwords {

    /** Coste de bcrypt: más alto es más seguro y más lento de comprobar. */
    private const val COST = 12

    const val MIN_LENGTH = 8

    fun hash(plain: String): String = BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun matches(plain: String, hash: String): Boolean =
        runCatching { BCrypt.verifyer().verify(plain.toCharArray(), hash).verified }.getOrDefault(false)

    /** Motivo por el que una contraseña no sirve; `null` si está bien. */
    fun problem(plain: String): PasswordProblem? = when {
        plain.length < MIN_LENGTH -> PasswordProblem.TOO_SHORT
        plain.isBlank() -> PasswordProblem.TOO_SHORT
        else -> null
    }
}

enum class PasswordProblem { TOO_SHORT }

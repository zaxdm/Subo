package com.peru.expresdm.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Tokens de sesión (JWT).
 *
 * Al iniciar sesión el servidor entrega un token firmado; la app lo manda en
 * cada petición. El servidor solo comprueba la firma, sin guardar sesiones.
 */
class Tokens(secret: String) {

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    fun issue(userId: String, displayName: String, now: Long = System.currentTimeMillis()): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId)
            .withClaim(CLAIM_NAME, displayName)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + LIFETIME_MS))
            .sign(algorithm)

    /** Id del usuario si el token es válido; `null` si está manipulado o caducado. */
    fun userIdOf(token: String): String? =
        runCatching { verifier.verify(token).subject }.getOrNull()

    companion object {
        const val ISSUER = "ruta-norte"
        const val CLAIM_NAME = "name"

        /** Un mes: el usuario no tiene que volver a entrar en cada viaje. */
        const val LIFETIME_MS = 30L * 24 * 60 * 60 * 1000
    }
}

package com.peru.expresdm.data

import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.Session

/**
 * Subo Premium: lo que la cuenta de pago tiene de más.
 *
 * Quién es Premium lo dice el servidor, que es donde se activa al confirmar el
 * pago; aquí solo se lee para abrir o cerrar funciones. Sin sesión, o con la
 * fecha vencida, la cuenta es gratuita.
 */
object Premium {

    /** Rutas con mapa sin conexión que guarda una cuenta gratuita. */
    const val FREE_SAVED_ROUTES = 1

    /** Las que guarda una cuenta Premium. */
    const val PREMIUM_SAVED_ROUTES = SavedRoutes.MAX_SAVED

    fun isActive(session: Session?, now: Long = System.currentTimeMillis()): Boolean =
        (session?.premiumUntil ?: 0L) > now

    /** La cuenta con la sesión abierta es Premium ahora mismo. */
    val active: Boolean get() = isActive(AccountRepository.session.value)

    /** Cuántas rutas puede guardar sin conexión esa cuenta. */
    fun savedRoutesLimit(premium: Boolean = active): Int =
        if (premium) PREMIUM_SAVED_ROUTES else FREE_SAVED_ROUTES
}

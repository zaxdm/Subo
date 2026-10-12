package com.peru.expresdm

import com.peru.expresdm.data.Premium
import com.peru.expresdm.data.net.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumTest {

    private val now = 1_000_000L
    private fun session(premiumUntil: Long?) =
        Session(token = "t", userId = "u", email = "ana@ejemplo.pe", displayName = "Ana", premiumUntil = premiumUntil)

    @Test
    fun `sin sesion o sin fecha la cuenta es gratuita`() {
        assertFalse(Premium.isActive(null, now))
        assertFalse(Premium.isActive(session(null), now))
    }

    @Test
    fun `premium vale hasta su fecha y ni un momento mas`() {
        assertTrue(Premium.isActive(session(now + 1), now))
        assertFalse(Premium.isActive(session(now), now))
        assertFalse(Premium.isActive(session(now - 1), now))
    }

    @Test
    fun `la cuenta gratuita guarda menos rutas sin conexion que la premium`() {
        assertEquals(Premium.FREE_SAVED_ROUTES, Premium.savedRoutesLimit(premium = false))
        assertEquals(Premium.PREMIUM_SAVED_ROUTES, Premium.savedRoutesLimit(premium = true))
        assertTrue(Premium.FREE_SAVED_ROUTES < Premium.PREMIUM_SAVED_ROUTES)
    }

    @Test
    fun `la cuenta gratuita tiene pocos lugares favoritos y la premium no tiene tope`() {
        assertEquals(Premium.FREE_FAVORITE_PLACES, Premium.favoritePlacesLimit(premium = false))
        assertEquals(Int.MAX_VALUE, Premium.favoritePlacesLimit(premium = true))
    }
}

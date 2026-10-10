package com.peru.expresdm.backend

import com.peru.expresdm.backend.alerts.AlertPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertPolicyTest {

    private val ahora = 1_757_800_000_000L
    private val minuto = 60_000L

    @Test
    fun `un micro lleno caduca en una hora`() {
        assertEquals(ahora + 60 * minuto, AlertPolicy.expiresAt("CROWDED", ahora, ahora))
    }

    @Test
    fun `unas obras duran un dia`() {
        assertEquals(ahora + 24 * 60 * minuto, AlertPolicy.expiresAt("ROADWORK", ahora, ahora))
    }

    @Test
    fun `confirmar que sigue ahi alarga la vida de la alerta`() {
        val sinConfirmar = AlertPolicy.expiresAt("TRAFFIC", ahora, ahora)
        val confirmadaMasTarde = AlertPolicy.expiresAt("TRAFFIC", ahora, ahora + 110 * minuto)
        assertTrue(confirmadaMasTarde > sinConfirmar)
    }

    @Test
    fun `un tipo desconocido no rompe el calculo`() {
        assertEquals(ahora + 120 * minuto, AlertPolicy.expiresAt("LO_QUE_SEA", ahora, ahora))
        assertFalse(AlertPolicy.isKnownType("LO_QUE_SEA"))
        assertTrue(AlertPolicy.isKnownType("TRAFFIC"))
    }

    @Test
    fun `dos votos de que ya no esta la ocultan`() {
        assertFalse(AlertPolicy.isHidden(confirmations = 0, dismissals = 1))
        assertTrue(AlertPolicy.isHidden(confirmations = 0, dismissals = 2))
        // Si otros confirman que sigue ahí, no se oculta.
        assertFalse(AlertPolicy.isHidden(confirmations = 1, dismissals = 2))
    }

    @Test
    fun `el comentario se limpia y se recorta`() {
        assertNull(AlertPolicy.cleanComment("   "))
        assertNull(AlertPolicy.cleanComment(null))
        assertEquals("Cola larga", AlertPolicy.cleanComment("  Cola larga  "))
        assertEquals(AlertPolicy.MAX_COMMENT_LENGTH, AlertPolicy.cleanComment("a".repeat(500))!!.length)
    }
}

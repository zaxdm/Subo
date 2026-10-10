package com.peru.expresdm

import com.peru.expresdm.data.AlertRules
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.AlertVote
import com.peru.expresdm.data.RoadAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val minute = 60_000L
    private val now = 1_757_800_000_000L

    private fun alert(type: AlertType = AlertType.TRAFFIC, createdAt: Long = now) = RoadAlert(
        id = "a1",
        type = type,
        latitude = -8.112,
        longitude = -79.0288,
        placeName = "Av. España",
        comment = null,
        createdAt = createdAt
    )

    @Test
    fun `una alerta recien creada esta vigente`() {
        assertTrue(AlertRules.isActive(alert(), now))
    }

    @Test
    fun `caduca cuando se cumple la vida de su tipo`() {
        val traffic = alert(AlertType.TRAFFIC)
        assertTrue(AlertRules.isActive(traffic, now + 119 * minute))
        assertFalse(AlertRules.isActive(traffic, now + 121 * minute))
    }

    @Test
    fun `una obra dura mucho mas que un micro lleno`() {
        val crowded = alert(AlertType.CROWDED)
        val roadwork = alert(AlertType.ROADWORK)
        val later = now + 5 * 60 * minute

        assertFalse(AlertRules.isActive(crowded, later))
        assertTrue(AlertRules.isActive(roadwork, later))
    }

    @Test
    fun `confirmar que sigue ahi alarga su vigencia`() {
        val created = alert(AlertType.TRAFFIC, createdAt = now - 110 * minute)
        val confirmed = AlertRules.vote(created, AlertVote.STILL_THERE, now)

        assertEquals(1, confirmed.confirmations)
        assertFalse(AlertRules.isActive(created, now + 20 * minute))
        assertTrue(AlertRules.isActive(confirmed, now + 20 * minute))
    }

    @Test
    fun `quien dice que ya no esta deja de verla`() {
        val dismissed = AlertRules.vote(alert(), AlertVote.GONE, now)
        assertEquals(1, dismissed.dismissals)
        assertFalse(AlertRules.isActive(dismissed, now))
    }

    @Test
    fun `cambiar de voto no suma dos veces`() {
        val first = AlertRules.vote(alert(), AlertVote.STILL_THERE, now)
        val changed = AlertRules.vote(first, AlertVote.GONE, now)

        assertEquals(0, changed.confirmations)
        assertEquals(1, changed.dismissals)
        assertEquals(AlertVote.GONE, changed.myVote)
    }

    @Test
    fun `repetir el mismo voto no cambia nada`() {
        val once = AlertRules.vote(alert(), AlertVote.STILL_THERE, now)
        assertEquals(once, AlertRules.vote(once, AlertVote.STILL_THERE, now + minute))
    }

    @Test
    fun `muchos votos de ya no la ocultan para todos`() {
        val disputed = alert().copy(confirmations = 1, dismissals = 3)
        assertFalse(AlertRules.isActive(disputed, now))
    }

    @Test
    fun `la lista de vigentes va de la mas reciente a la mas antigua`() {
        val old = alert(createdAt = now - 30 * minute).copy(id = "vieja")
        val recent = alert(createdAt = now - 5 * minute).copy(id = "nueva")
        val expired = alert(createdAt = now - 300 * minute).copy(id = "caducada")

        assertEquals(listOf("nueva", "vieja"), AlertRules.active(listOf(old, expired, recent), now).map { it.id })
    }

    @Test
    fun `los minutos transcurridos nunca son negativos`() {
        assertEquals(0, AlertRules.minutesAgo(alert(createdAt = now + minute), now))
        assertEquals(15, AlertRules.minutesAgo(alert(createdAt = now - 15 * minute), now))
    }
}

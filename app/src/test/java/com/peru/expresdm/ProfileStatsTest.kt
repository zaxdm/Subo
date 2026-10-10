package com.peru.expresdm

import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.ProfileStats
import com.peru.expresdm.data.RoadAlert
import com.peru.expresdm.data.Stop
import com.peru.expresdm.ui.Initials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El resumen del perfil: rutas grabadas, alertas vivas y rutas usadas. */
class ProfileStatsTest {

    private val now = 1_700_000_000_000L

    private fun route(id: String, mine: Boolean = false) = BusRoute(
        id = id,
        letter = id.take(1).uppercase(),
        name = "Ruta $id",
        origin = "A",
        destination = "B",
        durationMinutes = 30,
        fare = 1.5,
        schedule = "5:00 - 22:00",
        stops = listOf(Stop("Parada", -8.11, -79.02)),
        accentIndex = 0,
        createdByUser = mine
    )

    private fun alert(id: String, mine: Boolean, createdAt: Long = now) = RoadAlert(
        id = id,
        type = AlertType.TRAFFIC,
        latitude = -8.11,
        longitude = -79.02,
        placeName = null,
        comment = null,
        createdAt = createdAt,
        createdByMe = mine
    )

    @Test
    fun `cuenta solo las rutas que grabo el usuario`() {
        val routes = listOf(route("a"), route("b", mine = true), route("c", mine = true))
        val stats = ProfileStats.of(routes, emptyList(), emptyMap(), now)
        assertEquals(2, stats.recordedRoutes)
    }

    @Test
    fun `cuenta solo las alertas propias y vigentes`() {
        val alerts = listOf(
            alert("1", mine = true),
            alert("2", mine = false),
            // Publicada hace días: ya caducó y no debería seguir sumando.
            alert("3", mine = true, createdAt = now - 5L * 24 * 60 * 60 * 1000)
        )
        val stats = ProfileStats.of(emptyList(), alerts, emptyMap(), now)
        assertEquals(1, stats.activeAlerts)
    }

    @Test
    fun `una ruta borrada deja de contar como usada`() {
        val routes = listOf(route("a"))
        val uses = mapOf("a" to 4, "borrada" to 9)
        val stats = ProfileStats.of(routes, emptyList(), uses, now)
        assertEquals(1, stats.usedRoutes)
    }

    @Test
    fun `sin nada hecho el resumen esta vacio`() {
        assertTrue(ProfileStats.of(listOf(route("a")), emptyList(), emptyMap(), now).isEmpty)
        assertFalse(ProfileStats.of(listOf(route("a", mine = true)), emptyList(), emptyMap(), now).isEmpty)
    }

    @Test
    fun `las iniciales salen del nombre`() {
        assertEquals("AM", Initials.of("Ana Maria"))
        assertEquals("A", Initials.of("ana"))
        assertEquals("AM", Initials.of("  ana   maria  quispe "))
    }

    @Test
    fun `sin nombre util se usa el signo de interrogacion`() {
        assertEquals(Initials.PLACEHOLDER, Initials.of(null))
        assertEquals(Initials.PLACEHOLDER, Initials.of("   "))
        assertEquals(Initials.PLACEHOLDER, Initials.of("!!!"))
    }
}

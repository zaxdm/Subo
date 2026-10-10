package com.peru.expresdm

import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.DropOffPicker
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Elegir en el mapa dónde bajarse, tocando sobre el recorrido. */
class DropOffPickerTest {

    // Una avenida recta de oeste a este, con tres paradas encima.
    private val stops = listOf(
        Stop("Inicio", -8.1100, -79.0300),
        Stop("Medio", -8.1100, -79.0200),
        Stop("Final", -8.1100, -79.0100)
    )

    private val route = BusRoute(
        id = "r1",
        letter = "A",
        name = "Avenida",
        origin = "Inicio",
        destination = "Final",
        durationMinutes = 20,
        fare = 1.5,
        schedule = "05:00 - 22:00",
        stops = stops,
        accentIndex = 0
    )

    private fun metersBetween(a: GeoCoordinate, b: GeoCoordinate) =
        NearbyRoutes.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    @Test
    fun `el toque cae sobre el recorrido, no donde puso el dedo`() {
        // 100 m al norte de la avenida, entre dos paradas.
        val tap = GeoCoordinate(-8.1091, -79.0250)
        val pick = DropOffPicker.pick(tap, route)

        assertNotNull(pick)
        pick!!
        // El punto elegido está sobre la línea (misma latitud que la avenida).
        assertEquals(-8.1100, pick.point.latitude, 0.0005)
        assertEquals(-79.0250, pick.point.longitude, 0.0005)
        assertTrue("se movió ~100 m", pick.snappedMeters in 50.0..150.0)
    }

    @Test
    fun `si cae junto a una parada, la bajada toma su nombre`() {
        val tap = GeoCoordinate(-8.1101, -79.0201)
        val pick = DropOffPicker.pick(tap, route)
        assertEquals("Medio", pick?.stop?.name)
    }

    @Test
    fun `entre paradas no se inventa una parada`() {
        val tap = GeoCoordinate(-8.1100, -79.0250)
        val pick = DropOffPicker.pick(tap, route)
        assertNotNull(pick)
        assertNull("a medio camino no hay parada que valga", pick?.stop)
    }

    @Test
    fun `un toque lejos del recorrido no vale`() {
        // Un kilómetro al sur de la avenida.
        val tap = GeoCoordinate(-8.1190, -79.0250)
        assertNull(DropOffPicker.pick(tap, route))
    }

    @Test
    fun `se elige sobre el trazado grabado cuando lo hay`() {
        // Trazado que se desvía al norte entre las paradas de los extremos.
        val recorded = route.copy(
            path = listOf(
                GeoCoordinate(-8.1100, -79.0300),
                GeoCoordinate(-8.1050, -79.0200),
                GeoCoordinate(-8.1100, -79.0100)
            )
        )
        val tap = GeoCoordinate(-8.1050, -79.0200)
        val pick = DropOffPicker.pick(tap, recorded)

        assertNotNull(pick)
        assertTrue("el punto está en el desvío", metersBetween(pick!!.point, tap) < 20.0)
        assertNull("ninguna parada está cerca del desvío", pick.stop)
    }

    @Test
    fun `el punto elegido no se sale de los extremos de la ruta`() {
        // Mucho más al este que la última parada, pero cerca de la avenida.
        val tap = GeoCoordinate(-8.1100, -79.0090)
        val pick = DropOffPicker.pick(tap, route)
        assertNotNull(pick)
        assertEquals("Final", pick?.stop?.name)
        assertEquals(-79.0100, pick!!.point.longitude, 0.0005)
    }
}

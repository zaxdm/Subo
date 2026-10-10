package com.peru.expresdm

import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassingNearTest {

    @Test
    fun `por la Plaza de Armas pasan las rutas con parada cerca`() {
        val ids = NearbyRoutes.passingNear(-8.1118, -79.0287).map { it.route.id }

        assertTrue(ids.contains("california-porvenir"))
        assertTrue(ids.contains("huanchaco-express"))
    }

    @Test
    fun `un lugar lejos de todo no tiene micros`() {
        assertTrue(NearbyRoutes.passingNear(-12.0464, -77.0428).isEmpty())
    }

    @Test
    fun `los micros van del que tiene la parada mas cerca al mas lejano`() {
        val distances = NearbyRoutes.passingNear(-8.1118, -79.0287).map { it.distanceMeters }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `una ruta grabada cuenta aunque sus paradas queden lejos si el trazado pasa cerca`() {
        val recorded = BusRoute(
            id = "grabada",
            letter = "G",
            name = "Grabada",
            origin = "Norte",
            destination = "Sur",
            durationMinutes = 20,
            fare = 1.0,
            schedule = "",
            stops = listOf(Stop("Norte", -8.0900, -79.0300), Stop("Sur", -8.1400, -79.0300)),
            accentIndex = 0,
            path = listOf(GeoCoordinate(-8.0900, -79.0300), GeoCoordinate(-8.1150, -79.0300), GeoCoordinate(-8.1400, -79.0300))
        )
        val all: List<BusRoute> = RouteRepository.all() + recorded

        // Las paradas están a más de 2 km, pero el recorrido pasa a unos metros.
        val ids = NearbyRoutes.passingNear(-8.1150, -79.0302, routes = all).map { it.route.id }
        assertTrue(ids.contains("grabada"))
    }
}

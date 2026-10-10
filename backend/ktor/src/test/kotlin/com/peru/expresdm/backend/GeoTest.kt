package com.peru.expresdm.backend

import com.peru.expresdm.backend.geo.Geo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoTest {

    private val plazaDeArmas = Pair(-8.1120, -79.0288)
    private val avEspana = Pair(-8.1132, -79.0330)

    @Test
    fun `mide la distancia entre dos puntos de Trujillo`() {
        val metros = Geo.distanceMeters(plazaDeArmas.first, plazaDeArmas.second, avEspana.first, avEspana.second)
        // Son unas cuatro cuadras: alrededor de 480 m.
        assertTrue(metros in 400.0..550.0, "midió $metros m")
    }

    @Test
    fun `la distancia de un punto a si mismo es cero`() {
        assertEquals(0.0, Geo.distanceMeters(-8.11, -79.03, -8.11, -79.03), 0.001)
    }

    @Test
    fun `la caja contiene los puntos dentro del radio`() {
        val caja = Geo.boundingBox(-8.1120, -79.0288, radiusMeters = 1_000.0)
        // Un punto a unos 500 m al norte tiene que caer dentro.
        val latitudCercana = -8.1120 + 500 / 110_540.0
        assertTrue(latitudCercana in caja.minLatitude..caja.maxLatitude)
        // Uno a 5 km, fuera.
        val latitudLejana = -8.1120 + 5_000 / 110_540.0
        assertFalse(latitudLejana in caja.minLatitude..caja.maxLatitude)
    }

    @Test
    fun `rechaza coordenadas imposibles`() {
        assertTrue(Geo.isValidCoordinate(-8.11, -79.03))
        assertFalse(Geo.isValidCoordinate(120.0, -79.03))
        assertFalse(Geo.isValidCoordinate(-8.11, 200.0))
        assertFalse(Geo.isValidCoordinate(Double.NaN, -79.03))
    }
}

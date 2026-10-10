package com.peru.expresdm

import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.PathCleaner
import com.peru.expresdm.data.RecordingPoint
import com.peru.expresdm.data.RouteSnapper
import com.peru.expresdm.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PathCleanerTest {

    private val baseLat = -8.1100
    private val baseLon = -79.0300
    private val start = 1_757_800_000_000L
    private val metersPerDegreeLon = 111_320.0 * cos(Math.toRadians(baseLat))

    /** Un punto a tantos metros al norte y al este del origen, a tantos segundos del inicio. */
    private fun at(north: Double, east: Double, second: Long, accuracy: Float? = 8f) = RecordingPoint(
        latitude = baseLat + north / 110_540.0,
        longitude = baseLon + east / metersPerDegreeLon,
        timestamp = start + second * 1000,
        accuracy = accuracy
    )

    /** Calle recta hacia el norte: un punto cada 20 m y 4 s. */
    private fun straightRoad(east: (Int) -> Double = { 0.0 }, accuracy: (Int) -> Float? = { 8f }) =
        (0..50).map { i -> at(north = i * 20.0, east = east(i), second = i * 4L, accuracy = accuracy(i)) }

    /** Lo más que se aparta el recorrido de la calle, en metros. */
    private fun maxSideways(path: List<GeoCoordinate>) = path.maxOf { abs((it.longitude - baseLon) * metersPerDegreeLon) }

    private fun rawLength(points: List<RecordingPoint>) =
        PathCleaner.lengthMeters(points.map { GeoCoordinate(it.latitude, it.longitude) })

    @Test
    fun `el zigzag a los lados de una calle recta desaparece`() {
        val raw = straightRoad(east = { i -> if (i == 0 || i == 50) 0.0 else if (i % 2 == 0) 8.0 else -8.0 })
        val clean = PathCleaner.clean(raw)

        assertTrue("el rastro en bruto mide ${rawLength(raw)}", rawLength(raw) > 1_200)
        assertTrue("se aparta ${maxSideways(clean)} m", maxSideways(clean) <= 4.0)
        assertEquals(1_000.0, PathCleaner.lengthMeters(clean), 30.0)
        assertTrue("quedaron ${clean.size} puntos", clean.size <= 3)
    }

    @Test
    fun `estando quieto los puntos esparcidos se funden en uno`() {
        val drivingIn = (0..20).map { i -> at(i * 20.0, 0.0, i * 5L) }
        // Diez minutos parado en el paradero, con el GPS bailando ±12 m.
        val waiting = (1..60).map { i -> at(400 + 12 * sin(i * 1.7), 12 * cos(i * 2.3), 100 + i * 10L) }
        val drivingOut = (21..50).map { i -> at(i * 20.0, 0.0, 700 + (i - 20) * 5L) }
        val raw = drivingIn + waiting + drivingOut

        val clean = PathCleaner.clean(raw)

        assertTrue("el rastro en bruto mide ${rawLength(raw)}", rawLength(raw) > 2_000)
        assertEquals(1_000.0, PathCleaner.lengthMeters(clean), 60.0)
        assertTrue("se aparta ${maxSideways(clean)} m", maxSideways(clean) <= 6.0)
    }

    @Test
    fun `el recorrido llega hasta el ultimo punto grabado`() {
        val raw = straightRoad() + (1..10).map { i -> at(1_000 + 5 * sin(i.toDouble()), 5 * cos(i.toDouble()), 200 + i * 10L) }
        val clean = PathCleaner.clean(raw)

        assertEquals(raw.last().latitude, clean.last().latitude, 1e-9)
        assertEquals(raw.last().longitude, clean.last().longitude, 1e-9)
    }

    @Test
    fun `un salto imposible del GPS se ignora`() {
        val raw = straightRoad(east = { i -> if (i == 25) 900.0 else 0.0 })
        assertTrue(maxSideways(PathCleaner.clean(raw)) < 5.0)
    }

    @Test
    fun `un pico hacia un lado se quita`() {
        val raw = straightRoad(east = { i -> if (i == 25) 90.0 else 0.0 })
        assertTrue(maxSideways(PathCleaner.clean(raw)) < 5.0)
    }

    @Test
    fun `las lecturas imprecisas se descartan`() {
        val raw = straightRoad(
            east = { i -> if (i % 3 == 1) 60.0 else 0.0 },
            accuracy = { i -> if (i % 3 == 1) 80f else 8f }
        )
        assertTrue(maxSideways(PathCleaner.clean(raw)) < 5.0)
    }

    @Test
    fun `si toda la grabacion fue imprecisa se usa igualmente`() {
        val clean = PathCleaner.clean(straightRoad(accuracy = { 50f }))
        assertTrue(clean.size >= 2)
        assertEquals(1_000.0, PathCleaner.lengthMeters(clean), 60.0)
    }

    @Test
    fun `una ruta guardada solo con su forma tambien se limpia`() {
        val zigzag = straightRoad(east = { i -> if (i == 0 || i == 50) 0.0 else if (i % 2 == 0) 8.0 else -8.0 })
            .map { GeoCoordinate(it.latitude, it.longitude) }
        val clean = PathCleaner.cleanGeometry(zigzag)

        assertTrue(clean.size < zigzag.size)
        assertTrue(maxSideways(clean) <= 4.0)
    }

    @Test
    fun `una parada cercana se lleva al recorrido`() {
        val path = listOf(at(0.0, 0.0, 0), at(1_000.0, 0.0, 200)).map { GeoCoordinate(it.latitude, it.longitude) }
        val stop = at(500.0, 30.0, 0).let { GeoCoordinate(it.latitude, it.longitude) }

        val snapped = PathCleaner.snapToPath(stop, path)

        assertTrue(maxSideways(listOf(snapped)) < 0.5)
        assertEquals(stop.latitude, snapped.latitude, 1e-5)
    }

    @Test
    fun `al ajustar una ruta sus paradas quedan sobre el recorrido`() {
        val path = listOf(at(0.0, 0.0, 0), at(1_000.0, 0.0, 200)).map { GeoCoordinate(it.latitude, it.longitude) }
        val stopNearby = at(500.0, 25.0, 0)
        val route = BusRoute(
            "usuario-1", "N", "Nueva", "A", "B", 10, 1.0, "",
            listOf(Stop("Paradero", stopNearby.latitude, stopNearby.longitude)), 0,
            path = listOf(GeoCoordinate(0.0, 0.0), GeoCoordinate(0.1, 0.1)),
            createdByUser = true
        )

        val snapped = RouteSnapper.withPath(route, path)

        assertTrue(snapped.pathSnapped)
        assertEquals(path, snapped.path)
        assertEquals("Paradero", snapped.stops.single().name)
        assertTrue(maxSideways(listOf(GeoCoordinate(snapped.stops.single().latitude, snapped.stops.single().longitude))) < 0.5)
    }

    @Test
    fun `una parada lejos del recorrido no se mueve`() {
        val path = listOf(at(0.0, 0.0, 0), at(1_000.0, 0.0, 200)).map { GeoCoordinate(it.latitude, it.longitude) }
        val stop = at(500.0, 200.0, 0).let { GeoCoordinate(it.latitude, it.longitude) }
        assertEquals(stop, PathCleaner.snapToPath(stop, path))
    }
}

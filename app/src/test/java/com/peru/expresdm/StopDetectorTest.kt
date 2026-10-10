package com.peru.expresdm

import com.peru.expresdm.data.RecordingPoint
import com.peru.expresdm.data.StopDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopDetectorTest {

    private val start = 1_757_800_000_000L

    /** Unos 111 m por cada milésima de grado de latitud. */
    private fun point(latOffsetThousandths: Double, secondsFromStart: Long) = RecordingPoint(
        latitude = -8.1000 - latOffsetThousandths / 1000.0,
        longitude = -79.0300,
        timestamp = start + secondsFromStart * 1000
    )

    /** Un tramo en movimiento: un punto cada 10 s avanzando ~111 m. */
    private fun driving(fromThousandths: Int, toThousandths: Int, startSecond: Long): List<RecordingPoint> =
        (fromThousandths..toThousandths).mapIndexed { i, lat -> point(lat.toDouble(), startSecond + i * 10L) }

    /** Quieto en un sitio durante [seconds], con un punto cada 10 s. */
    private fun waiting(atThousandths: Double, startSecond: Long, seconds: Long): List<RecordingPoint> =
        (0..seconds / 10).map { i -> point(atThousandths, startSecond + i * 10) }

    @Test
    fun `sin puntos no hay paradas`() {
        assertTrue(StopDetector.detect(emptyList()).isEmpty())
    }

    @Test
    fun `un recorrido sin detenerse solo tiene origen y destino`() {
        val points = driving(0, 20, 0)
        val stops = StopDetector.detect(points)

        assertEquals(2, stops.size)
        assertEquals(points.first().latitude, stops.first().latitude, 1e-9)
        assertEquals(points.last().latitude, stops.last().latitude, 1e-9)
    }

    @Test
    fun `detenerse medio minuto marca una parada intermedia`() {
        val points = driving(0, 10, 0) + waiting(10.0, 110, 60) + driving(11, 20, 180)
        val stops = StopDetector.detect(points)

        assertEquals(3, stops.size)
        // La parada queda donde se detuvo: a unos 1,1 km del origen.
        assertEquals(-8.1100, stops[1].latitude, 0.0005)
    }

    @Test
    fun `un semaforo corto no es una parada`() {
        // El micro llega al semáforo a los 100 s (último punto del tramo) y
        // arranca a los 130 s: 20 s quieto, por debajo de los 30 s de una parada.
        val points = driving(0, 10, 0) + waiting(10.0, 110, 10) + driving(11, 20, 130)
        assertEquals(2, StopDetector.detect(points).size)
    }

    @Test
    fun `dos esperas muy juntas cuentan como una sola parada`() {
        val points = driving(0, 10, 0) +
            waiting(10.0, 110, 40) +
            waiting(10.5, 160, 40) +
            driving(11, 20, 210)
        assertEquals(3, StopDetector.detect(points).size)
    }

    @Test
    fun `el destino es siempre el ultimo punto grabado`() {
        val points = driving(0, 10, 0) + waiting(10.0, 110, 60)
        val stops = StopDetector.detect(points)

        assertEquals(points.last().latitude, stops.last().latitude, 1e-9)
        assertEquals(2, stops.size)
    }
}

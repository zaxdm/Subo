package com.peru.expresdm.data

import kotlin.math.cos
import kotlin.math.hypot

/**
 * El punto donde el usuario decide bajarse, elegido tocando el mapa.
 *
 * [point] no es donde tocó el dedo, sino el punto del recorrido más cercano a
 * ese toque: la bajada siempre cae sobre la ruta, aunque el dedo tape media
 * manzana. [stop] es la parada oficial si el punto cayó junto a una, y sirve
 * para llamar a la bajada por su nombre en lugar de por sus coordenadas.
 */
data class DropOff(
    val point: GeoCoordinate,
    val stop: Stop?,
    /** Cuánto se movió el toque hasta el recorrido. */
    val snappedMeters: Double
)

/**
 * Convierte un toque en el mapa en un punto de bajada sobre el recorrido.
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object DropOffPicker {

    /**
     * Más lejos del recorrido que esto, el toque no era para esta ruta: mejor
     * decírselo al usuario que dejarle un aviso en un sitio por el que el
     * micro no pasa.
     */
    const val MAX_TAP_METERS = 400.0

    /** Si el punto cae a menos de esto de una parada, se usa el nombre de la parada. */
    const val STOP_MATCH_METERS = 150.0

    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * El recorrido sobre el que se elige: el trazado grabado si lo hay, y si
     * no la línea que une las paradas, que es lo que la app dibuja.
     */
    fun lineOf(route: BusRoute): List<GeoCoordinate> {
        val path = route.path
        if (path != null && path.size >= 2) return path
        return route.stops.map { GeoCoordinate(it.latitude, it.longitude) }
    }

    /** `null` si el toque quedó demasiado lejos del recorrido. */
    fun pick(tap: GeoCoordinate, route: BusRoute): DropOff? {
        val line = lineOf(route)
        if (line.isEmpty()) return null

        val (point, meters) = nearestOnLine(tap, line)
        if (meters > MAX_TAP_METERS) return null

        val stop = route.stops.minByOrNull { distance(point, GeoCoordinate(it.latitude, it.longitude)) }
            ?.takeIf { distance(point, GeoCoordinate(it.latitude, it.longitude)) <= STOP_MATCH_METERS }

        return DropOff(point, stop, meters)
    }

    /** El punto de [line] más cercano a [tap], y a cuántos metros quedó. */
    internal fun nearestOnLine(tap: GeoCoordinate, line: List<GeoCoordinate>): Pair<GeoCoordinate, Double> {
        if (line.size == 1) return line.first() to distance(tap, line.first())

        // Proyección plana centrada en el toque: a esta escala (metros dentro de
        // una ciudad) la diferencia con la esfera es despreciable.
        val latitudeScale = EARTH_RADIUS_METERS * Math.PI / 180.0
        val longitudeScale = latitudeScale * cos(Math.toRadians(tap.latitude))
        fun x(c: GeoCoordinate) = (c.longitude - tap.longitude) * longitudeScale
        fun y(c: GeoCoordinate) = (c.latitude - tap.latitude) * latitudeScale

        var best = line.first()
        var bestDistance = Double.MAX_VALUE
        line.zipWithNext { a, b ->
            val ax = x(a)
            val ay = y(a)
            val dx = x(b) - ax
            val dy = y(b) - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val qx = ax + t * dx
            val qy = ay + t * dy
            val d = hypot(qx, qy)
            if (d < bestDistance) {
                bestDistance = d
                best = GeoCoordinate(
                    latitude = tap.latitude + qy / latitudeScale,
                    longitude = tap.longitude + qx / longitudeScale
                )
            }
        }
        return best to bestDistance
    }

    private fun distance(a: GeoCoordinate, b: GeoCoordinate): Double =
        NearbyRoutes.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}

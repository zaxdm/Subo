package com.peru.expresdm.data

/**
 * Detecta las paradas de una ruta grabada: los sitios donde el micro se quedó
 * quieto un rato.
 *
 * El grabador guarda un punto cada pocos segundos aunque no haya movimiento, así
 * que una parada aparece como varios puntos seguidos muy juntos que abarcan
 * bastante tiempo. El origen y el destino siempre cuentan como paradas.
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object StopDetector {

    /** Tiempo quieto para considerar que fue una parada y no un semáforo corto. */
    const val MIN_DWELL_MS = 30_000L

    /** Radio en el que el micro "sigue en el mismo sitio" pese al error del GPS. */
    const val DWELL_RADIUS_METERS = 30.0

    /** Dos paradas más cerca que esto son la misma (el micro avanzó unos metros). */
    const val MIN_METERS_BETWEEN_STOPS = 150.0

    fun detect(points: List<RecordingPoint>): List<GeoCoordinate> {
        if (points.isEmpty()) return emptyList()

        val dwells = mutableListOf<GeoCoordinate>()
        var start = 0
        while (start < points.size) {
            val anchor = points[start]
            var end = start
            while (end + 1 < points.size && distance(anchor, points[end + 1]) <= DWELL_RADIUS_METERS) end++

            if (points[end].timestamp - anchor.timestamp >= MIN_DWELL_MS) {
                val cluster = points.subList(start, end + 1)
                dwells += GeoCoordinate(
                    latitude = cluster.sumOf { it.latitude } / cluster.size,
                    longitude = cluster.sumOf { it.longitude } / cluster.size
                )
                start = end + 1
            } else {
                start++
            }
        }

        val first = points.first().toCoordinate()
        val last = points.last().toCoordinate()
        val candidates = listOf(first) + dwells + last

        // Juntamos las que caen casi en el mismo sitio, quedándonos con la primera.
        val stops = mutableListOf<GeoCoordinate>()
        candidates.forEach { candidate ->
            if (stops.none { distance(it, candidate) < MIN_METERS_BETWEEN_STOPS }) stops += candidate
        }

        // Si el destino se fundió con una parada anterior, que el último punto siga siendo el final.
        if (points.size > 1 && stops.last() != last && distance(stops.last(), last) < MIN_METERS_BETWEEN_STOPS) {
            stops[stops.lastIndex] = last
        }
        return stops
    }

    private fun RecordingPoint.toCoordinate() = GeoCoordinate(latitude, longitude)

    private fun distance(a: RecordingPoint, b: RecordingPoint) =
        NearbyRoutes.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun distance(a: GeoCoordinate, b: GeoCoordinate) =
        NearbyRoutes.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}

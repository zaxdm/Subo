package com.peru.expresdm.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Limpia el recorrido grabado con el GPS antes de dibujarlo o guardarlo.
 *
 * El GPS de un teléfono tiene varios metros de error y ese error cambia en cada
 * lectura. Sin limpiar, la ruta sale "rayada": estando quieto en un paradero
 * los puntos se esparcen alrededor, y en marcha zigzaguean a los lados de la
 * calle. Aquí se quita todo eso en pasos:
 *
 * 1. Se descartan las lecturas imprecisas y los saltos imposibles.
 * 2. Los puntos que caen casi en el mismo sitio (el micro parado o avanzando
 *    muy despacio) se funden en uno.
 * 3. Se quitan los picos: un punto que se va a un lado y vuelve.
 * 4. Se suaviza la línea y se simplifica, quitando los puntos que no cambian la forma.
 *
 * Después, si hay conexión, el resultado se ajusta a las calles con
 * [StreetRouter.matchTrace].
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object PathCleaner {

    /** Lecturas con más error que esto se descartan. */
    const val MAX_ACCURACY_METERS = 30f

    /** Unos 110 km/h: un micro en ciudad no va más rápido; si "lo hace", es un salto del GPS. */
    const val MAX_SPEED_METERS_PER_SECOND = 30.0

    /** Distancia mínima entre dos puntos del recorrido; lo que queda más cerca es ruido. */
    const val MIN_STEP_METERS = 20.0

    /** Un pico es un tramo corto que se va y vuelve casi en sentido contrario. */
    const val SPIKE_MAX_LEG_METERS = 120.0
    const val SPIKE_MIN_TURN_DEGREES = 140.0

    /** Los puntos que se apartan menos que esto de la línea no aportan forma. */
    const val SIMPLIFY_TOLERANCE_METERS = 6.0

    /** Tras varios saltos seguidos "imposibles", se asume que el imposible era el punto anterior. */
    private const val MAX_CONSECUTIVE_JUMPS = 3

    /** Una parada más lejos que esto del recorrido no se mueve: el recorrido podría estar mal. */
    private const val MAX_STOP_SNAP_METERS = 60.0

    private const val METERS_PER_DEGREE_LAT = 110_540.0
    private const val METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0

    /** Recorrido limpio y listo para dibujar a partir de los puntos grabados. */
    fun clean(points: List<RecordingPoint>): List<GeoCoordinate> = finish(filterTrace(points))

    /** Limpia un recorrido del que solo se conoce la forma (rutas guardadas con versiones anteriores). */
    fun cleanGeometry(path: List<GeoCoordinate>): List<GeoCoordinate> =
        finish(removeSpikes(collapse(path.map { Sample(it, accuracy = null) })))

    /**
     * Filtrado sin suavizar: la entrada adecuada para ajustar el recorrido a las
     * calles, que prefiere puntos reales a puntos promediados.
     */
    fun filterTrace(points: List<RecordingPoint>): List<GeoCoordinate> {
        if (points.isEmpty()) return emptyList()

        // Si toda la grabación fue imprecisa, mejor un recorrido aproximado que ninguno.
        val precise = points.filter { it.accuracy == null || it.accuracy <= MAX_ACCURACY_METERS }.ifEmpty { points }

        val plausible = mutableListOf(precise.first())
        var rejected = 0
        for (point in precise.drop(1)) {
            val last = plausible.last()
            val meters = distance(last.latitude, last.longitude, point.latitude, point.longitude)
            val seconds = (point.timestamp - last.timestamp) / 1000.0
            val impossible = meters > MIN_STEP_METERS && (seconds <= 0 || meters / seconds > MAX_SPEED_METERS_PER_SECOND)
            if (impossible && rejected < MAX_CONSECUTIVE_JUMPS) {
                rejected++
                continue
            }
            rejected = 0
            plausible += point
        }

        return removeSpikes(collapse(plausible.map { Sample(GeoCoordinate(it.latitude, it.longitude), it.accuracy) }))
    }

    /** Lleva una parada al punto más cercano del recorrido, si está cerca. */
    fun snapToPath(point: GeoCoordinate, path: List<GeoCoordinate>): GeoCoordinate {
        if (path.size < 2) return point
        val projection = Projection(point)
        val p = projection.toMeters(point)

        var best = point
        var bestDistance = Double.MAX_VALUE
        path.zipWithNext { a, b ->
            val pa = projection.toMeters(a)
            val pb = projection.toMeters(b)
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((p.x - pa.x) * dx + (p.y - pa.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val qx = pa.x + t * dx
            val qy = pa.y + t * dy
            val d = hypot(p.x - qx, p.y - qy)
            if (d < bestDistance) {
                bestDistance = d
                best = projection.toCoordinate(Meters(qx, qy))
            }
        }
        return if (bestDistance <= MAX_STOP_SNAP_METERS) best else point
    }

    fun lengthMeters(path: List<GeoCoordinate>): Double =
        path.zipWithNext { a, b -> distance(a.latitude, a.longitude, b.latitude, b.longitude) }.sum()

    // --- Pasos ---

    private data class Sample(val coordinate: GeoCoordinate, val accuracy: Float?)

    /**
     * Funde los puntos seguidos que caen dentro del radio del primero del
     * grupo (el error del GPS, como mínimo [MIN_STEP_METERS]) en su centro.
     */
    private fun collapse(samples: List<Sample>): List<GeoCoordinate> {
        if (samples.isEmpty()) return emptyList()
        val result = mutableListOf<GeoCoordinate>()
        var group = mutableListOf(samples.first())

        fun flush() {
            result += GeoCoordinate(
                latitude = group.sumOf { it.coordinate.latitude } / group.size,
                longitude = group.sumOf { it.coordinate.longitude } / group.size
            )
        }

        for (sample in samples.drop(1)) {
            val anchor = group.first()
            val radius = maxOf(MIN_STEP_METERS, (anchor.accuracy ?: 0f).toDouble(), (sample.accuracy ?: 0f).toDouble())
            if (distance(anchor.coordinate, sample.coordinate) < radius) {
                group += sample
            } else {
                flush()
                group = mutableListOf(sample)
            }
        }
        flush()

        // El último punto grabado marca el destino: que el recorrido llegue hasta él.
        val lastReal = samples.last().coordinate
        if (result.size >= 2 && result.last() != lastReal) result[result.lastIndex] = lastReal
        return result
    }

    /** Quita los puntos que se van a un lado y vuelven, repitiendo hasta que no quede ninguno. */
    private fun removeSpikes(path: List<GeoCoordinate>): List<GeoCoordinate> {
        val points = path.toMutableList()
        var changed = true
        while (changed && points.size >= 3) {
            changed = false
            var i = 1
            while (i < points.lastIndex) {
                val a = points[i - 1]
                val b = points[i]
                val c = points[i + 1]
                val shortLegs = minOf(distance(a, b), distance(b, c)) <= SPIKE_MAX_LEG_METERS
                if (shortLegs && turnDegrees(a, b, c) >= SPIKE_MIN_TURN_DEGREES) {
                    points.removeAt(i)
                    changed = true
                } else {
                    i++
                }
            }
        }
        return points
    }

    private fun finish(path: List<GeoCoordinate>): List<GeoCoordinate> = simplify(smooth(path))

    /** Media ponderada 1-2-1 con los vecinos; el origen y el destino no se mueven. */
    private fun smooth(path: List<GeoCoordinate>): List<GeoCoordinate> {
        if (path.size < 3) return path
        return path.indices.map { i ->
            if (i == 0 || i == path.lastIndex) {
                path[i]
            } else {
                GeoCoordinate(
                    latitude = (path[i - 1].latitude + 2 * path[i].latitude + path[i + 1].latitude) / 4,
                    longitude = (path[i - 1].longitude + 2 * path[i].longitude + path[i + 1].longitude) / 4
                )
            }
        }
    }

    /** Douglas-Peucker: conserva solo los puntos que se apartan de la recta más que la tolerancia. */
    private fun simplify(path: List<GeoCoordinate>): List<GeoCoordinate> {
        if (path.size < 3) return path
        val projection = Projection(path.first())
        val meters = path.map { projection.toMeters(it) }
        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.lastIndex] = true

        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(0 to path.lastIndex)
        while (pending.isNotEmpty()) {
            val (start, end) = pending.removeLast()
            var farthest = -1
            var farthestDistance = 0.0
            for (i in start + 1 until end) {
                val d = distanceToSegment(meters[i], meters[start], meters[end])
                if (d > farthestDistance) {
                    farthestDistance = d
                    farthest = i
                }
            }
            if (farthest != -1 && farthestDistance > SIMPLIFY_TOLERANCE_METERS) {
                keep[farthest] = true
                pending.addLast(start to farthest)
                pending.addLast(farthest to end)
            }
        }
        return path.filterIndexed { i, _ -> keep[i] }
    }

    // --- Geometría ---

    private data class Meters(val x: Double, val y: Double)

    /** Proyección plana local: a la escala de una ciudad el error es despreciable. */
    private class Projection(origin: GeoCoordinate) {
        private val lat0 = origin.latitude
        private val lon0 = origin.longitude
        private val metersPerDegreeLon = METERS_PER_DEGREE_LON_AT_EQUATOR * cos(Math.toRadians(lat0))

        fun toMeters(c: GeoCoordinate) =
            Meters((c.longitude - lon0) * metersPerDegreeLon, (c.latitude - lat0) * METERS_PER_DEGREE_LAT)

        fun toCoordinate(m: Meters) =
            GeoCoordinate(lat0 + m.y / METERS_PER_DEGREE_LAT, lon0 + m.x / metersPerDegreeLon)
    }

    private fun distanceToSegment(p: Meters, a: Meters, b: Meters): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Cuánto gira el camino en [b]: 0° sigue recto, 180° da media vuelta. */
    private fun turnDegrees(a: GeoCoordinate, b: GeoCoordinate, c: GeoCoordinate): Double {
        val projection = Projection(b)
        val pa = projection.toMeters(a)
        val pc = projection.toMeters(c)
        // b es el origen de la proyección.
        val inX = -pa.x
        val inY = -pa.y
        val outX = pc.x
        val outY = pc.y
        val norms = sqrt(inX * inX + inY * inY) * sqrt(outX * outX + outY * outY)
        if (norms == 0.0) return 0.0
        val cosine = ((inX * outX + inY * outY) / norms).coerceIn(-1.0, 1.0)
        return abs(Math.toDegrees(acos(cosine)))
    }

    private fun distance(a: GeoCoordinate, b: GeoCoordinate) =
        distance(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        NearbyRoutes.distanceMeters(lat1, lon1, lat2, lon2)
}

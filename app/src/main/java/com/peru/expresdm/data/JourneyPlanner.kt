package com.peru.expresdm.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos

/** Un tramo del viaje: o se camina, o se va en micro. */
sealed interface JourneyLeg {

    /** A pie: hasta una parada, hasta el micro siguiente o hasta el destino. */
    data class Walk(val meters: Double, val minutes: Int) : JourneyLeg

    /**
     * En micro, desde donde se sube hasta donde se baja.
     *
     * Los micros paran en cualquier punto de su recorrido, así que [from] y
     * [to] no siempre son paradas del catálogo: [fromKind] y [toKind] dicen si
     * lo son, y si no, el nombre es el de la parada conocida más cercana.
     */
    data class Ride(
        val route: BusRoute,
        val from: Stop,
        val to: Stop,
        val minutes: Int,
        /** El recorrido del micro entre los dos puntos, para dibujarlo. */
        val line: List<GeoCoordinate> = emptyList(),
        val fromKind: RidePoint = RidePoint.STOP,
        val toKind: RidePoint = RidePoint.STOP
    ) : JourneyLeg
}

/** Qué es el punto donde se sube o se baja de un micro. */
enum class RidePoint {
    /** Una parada del catálogo. */
    STOP,

    /** Un punto del recorrido a poca distancia de una parada conocida. */
    NEAR_STOP,

    /** Un punto del recorrido lejos de cualquier parada conocida. */
    ON_ROUTE
}

/** Una forma de llegar: los tramos en orden, con su tiempo y su costo. */
data class Journey(
    val legs: List<JourneyLeg>,
    val totalMinutes: Int,
    val walkMeters: Double,
    val fare: Double
) {
    val rides: List<JourneyLeg.Ride> get() = legs.filterIsInstance<JourneyLeg.Ride>()

    /** Cuántas veces hay que cambiar de micro. */
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)

    /** Un viaje sin micros: se llega andando. */
    val walkingOnly: Boolean get() = rides.isEmpty()

    /**
     * Los micros que se toman, en orden. Identifica el viaje aunque, al moverse
     * el usuario, cambien sus metros y sus minutos.
     */
    val key: List<String> get() = rides.map { it.route.id }
}

/**
 * Calcula cómo llegar de un punto a otro con los micros del catálogo.
 *
 * No hay horarios reales (nadie publica los de Trujillo), así que los tiempos
 * son estimaciones: lo que dura la ruta completa se reparte proporcionalmente
 * entre sus paradas, y se suman unos minutos de espera por cada micro. Sirve
 * para comparar opciones, no para prometer una hora de llegada.
 *
 * Los micros se recorren en los dos sentidos: en la práctica la misma línea
 * vuelve por el mismo corredor, y el catálogo solo guarda una dirección.
 *
 * Se sube y se baja en cualquier punto del recorrido, no solo en las paradas:
 * en Trujillo los micros paran donde se les pide, y el catálogo trae pocas
 * paradas por línea. Si el punto cae junto a una parada conocida se usa esa,
 * que tiene nombre.
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object JourneyPlanner {

    /** Lo más que se espera que alguien camine hasta el paradero. */
    const val MAX_WALK_TO_STOP_METERS = 900.0

    /** Lo que se acepta caminar cuando ningún micro pasa más cerca. */
    const val MAX_LONG_WALK_METERS = 2_000.0

    /** Cuánto más lenta que la mejor puede ser una opción para seguir enseñándola. */
    private const val MAX_SLOWER_FACTOR = 1.5
    private const val MAX_SLOWER_MINUTES = 5

    /** Lo más que se camina al cambiar de micro. */
    const val MAX_TRANSFER_WALK_METERS = 500.0

    /** Si el destino está más cerca que esto, se propone ir andando. */
    const val WALK_ONLY_METERS = 1_500.0

    /** Espera típica del primer micro y de cada transbordo. */
    const val WAIT_MINUTES = 5

    /** Un tramo en micro más corto que esto no compensa: se camina. */
    const val MIN_RIDE_METERS = 400.0

    const val MAX_RESULTS = 4

    /** Un punto del recorrido a menos de esto de una parada se da por la parada. */
    const val SNAP_TO_STOP_METERS = 200.0

    /** Hasta aquí tiene sentido decir "cerca de" una parada. */
    const val NEAR_STOP_METERS = 700.0

    /**
     * Cuántos micros se prueban a cada extremo de un transbordo: los que dejan
     * más cerca. Con el catálogo entero de una ciudad, probarlos todos contra
     * todos tarda tanto que el teléfono se queda parado.
     */
    private const val MAX_TRANSFER_CANDIDATES = 10

    /** Cada cuánto se prueba a cambiar de micro a lo largo de un recorrido. */
    private const val TRANSFER_STEP_METERS = 250.0

    /** Los puntos del recorrido más juntos que esto no aportan al calcular. */
    private const val TRACK_MIN_GAP_METERS = 40.0

    fun plan(
        origin: UserPosition,
        destination: GeoCoordinate,
        routes: List<BusRoute> = RouteRepository.all()
    ): List<Journey> {
        val usable = routes.filter { it.stops.size >= 2 }
        val walking = walkingOption(origin, destination)

        // Si ningún micro pasa a una distancia cómoda, se enseña el que deja más
        // cerca aunque toque caminar bastante: es mejor que no decir nada.
        val rides = ridingOptions(origin, destination, usable, MAX_WALK_TO_STOP_METERS)
            .ifEmpty { ridingOptions(origin, destination, usable, MAX_LONG_WALK_METERS) }

        return (listOfNotNull(walking) + rides)
            .sortedWith(compareBy({ it.totalMinutes }, { it.transfers }, { it.walkMeters }))
            .take(MAX_RESULTS)
    }

    /** Los viajes en micro, sin los que no compensan frente a otro mejor. */
    private fun ridingOptions(
        origin: UserPosition,
        destination: GeoCoordinate,
        routes: List<BusRoute>,
        maxWalk: Double
    ): List<Journey> {
        val direct = directOptions(origin, destination, routes, maxWalk)
        val directRoutes = direct.flatMap { it.key }.toSet()
        val transfers = transferOptions(origin, destination, routes, maxWalk)
            // Cambiar de micro para acabar en uno que ya llevaba solo no tiene sentido.
            .filter { journey -> journey.key.none { it in directRoutes } }

        val journeys = (direct + transfers)
            // Entre dos viajes con los mismos micros, se queda el más corto.
            .groupBy { it.key }
            .map { (_, sameRoutes) -> sameRoutes.minBy { it.totalMinutes } }
        val fastest = journeys.minOfOrNull { it.totalMinutes } ?: return emptyList()
        // Una opción mucho más lenta que la mejor solo estorba.
        return journeys.filter { it.totalMinutes <= fastest * MAX_SLOWER_FACTOR + MAX_SLOWER_MINUTES }
    }

    /**
     * El trazado del micro entre dos paradas, para dibujarlo en el mapa. Si la
     * ruta tiene recorrido grabado se recorta ese; si no, se unen las paradas.
     */
    fun segmentLine(route: BusRoute, from: Stop, to: Stop): List<GeoCoordinate> {
        val fromIndex = route.stops.indexOf(from)
        val toIndex = route.stops.indexOf(to)
        if (fromIndex < 0 || toIndex < 0) return emptyList()

        val path = route.path
        if (path == null || path.size < 2) {
            val range = if (fromIndex <= toIndex) fromIndex..toIndex else toIndex..fromIndex
            val stops = range.map { GeoCoordinate(route.stops[it].latitude, route.stops[it].longitude) }
            return if (fromIndex <= toIndex) stops else stops.reversed()
        }

        val start = nearestPathIndex(path, from)
        val end = nearestPathIndex(path, to)
        val slice = if (start <= end) path.subList(start, end + 1) else path.subList(end, start + 1).reversed()
        return slice.toList()
    }

    // --- Opciones ---

    private fun walkingOption(origin: UserPosition, destination: GeoCoordinate): Journey? {
        val meters = distance(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
        if (meters > WALK_ONLY_METERS) return null
        val minutes = walkMinutes(meters)
        return Journey(
            legs = listOf(JourneyLeg.Walk(meters, minutes)),
            totalMinutes = minutes,
            walkMeters = meters,
            fare = 0.0
        )
    }

    /** Un solo micro: se sube cerca del origen y se baja cerca del destino. */
    private fun directOptions(
        origin: UserPosition,
        destination: GeoCoordinate,
        routes: List<BusRoute>,
        maxWalk: Double
    ): List<Journey> = routes.mapNotNull { route ->
        val track = trackOf(route)
        val board = track.access(origin.latitude, origin.longitude, maxWalk) ?: return@mapNotNull null
        val exit = track.access(destination.latitude, destination.longitude, maxWalk)
            ?: return@mapNotNull null
        buildJourney(origin, destination, listOf(Hop(track, board, exit)), maxWalk)
    }

    /** Dos micros, cambiando donde sus recorridos pasan cerca uno del otro. */
    private fun transferOptions(
        origin: UserPosition,
        destination: GeoCoordinate,
        routes: List<BusRoute>,
        maxWalk: Double
    ): List<Journey> {
        val tracks = routes.map { trackOf(it) }

        /** Los micros que pasan cerca de un punto, primero los que menos hacen caminar. */
        fun candidates(latitude: Double, longitude: Double) = tracks
            .mapNotNull { track -> track.access(latitude, longitude, maxWalk)?.let { track to it } }
            .sortedBy { (track, access) ->
                val point = track.pointAt(access.along)
                distance(latitude, longitude, point.latitude, point.longitude)
            }
            .take(MAX_TRANSFER_CANDIDATES)

        val fromHome = candidates(origin.latitude, origin.longitude)
        val toTarget = candidates(destination.latitude, destination.longitude)

        val journeys = mutableListOf<Journey>()
        for ((first, board) in fromHome) {
            for ((second, exit) in toTarget) {
                if (first.route.id == second.route.id) continue
                // Dos recorridos que no se acercan no tienen dónde enlazar.
                if (!first.passesNear(second, MAX_TRANSFER_WALK_METERS)) continue

                // Primero entre paradas, que tienen nombre. Se evalúan todos los
                // enlaces: el más cercano puede estar detrás del origen.
                val betweenStops = mutableListOf<Journey>()
                for (leave in first.route.stops) {
                    for (take in second.route.stops) {
                        val meters = distance(leave.latitude, leave.longitude, take.latitude, take.longitude)
                        if (meters > MAX_TRANSFER_WALK_METERS) continue
                        buildJourney(
                            origin, destination,
                            listOf(Hop(first, board, first.atStop(leave)), Hop(second, second.atStop(take), exit)),
                            maxWalk
                        )?.let { betweenStops += it }
                    }
                }
                if (betweenStops.isNotEmpty()) {
                    journeys += betweenStops
                    continue
                }

                // Sin paradas que enlacen, donde los dos recorridos se acerquen.
                var along = 0.0
                while (along <= first.length) {
                    val point = first.pointAt(along)
                    val take = second.access(point.latitude, point.longitude, MAX_TRANSFER_WALK_METERS)
                    if (take != null) {
                        val leave = first.accessAt(along)
                        buildJourney(origin, destination, listOf(Hop(first, board, leave), Hop(second, take, exit)), maxWalk)
                            ?.let { journeys += it }
                    }
                    along += TRANSFER_STEP_METERS
                }
            }
        }
        return journeys
    }

    // --- Construcción ---

    private class Hop(val track: Track, val from: Access, val to: Access)

    private fun buildJourney(
        origin: UserPosition,
        destination: GeoCoordinate,
        hops: List<Hop>,
        maxWalk: Double
    ): Journey? {
        val legs = mutableListOf<JourneyLeg>()
        var minutes = 0
        var walkMeters = 0.0
        var fare = 0.0
        var currentLat = origin.latitude
        var currentLon = origin.longitude

        for (hop in hops) {
            val route = hop.track.route
            val from = hop.from.stop
            val to = hop.to.stop
            if (!route.bidirectional && hop.to.along <= hop.from.along) return null
            val rideMeters = abs(hop.to.along - hop.from.along)
            if (rideMeters < MIN_RIDE_METERS) return null

            // Subirse a un micro que aleja del destino no es una opción.
            val before = distance(from.latitude, from.longitude, destination.latitude, destination.longitude)
            val after = distance(to.latitude, to.longitude, destination.latitude, destination.longitude)
            if (after >= before) return null

            val walk = distance(currentLat, currentLon, from.latitude, from.longitude)
            if (walk > maxWalk) return null
            if (walk > 0) {
                val walkTime = walkMinutes(walk)
                legs += JourneyLeg.Walk(walk, walkTime)
                minutes += walkTime
                walkMeters += walk
            }

            val rideTime = hop.track.rideMinutes(rideMeters)
            legs += JourneyLeg.Ride(
                route = route,
                from = from,
                to = to,
                minutes = rideTime,
                line = hop.track.slice(hop.from.along, hop.to.along),
                fromKind = hop.from.kind,
                toKind = hop.to.kind
            )
            minutes += rideTime + WAIT_MINUTES
            fare += route.fare
            currentLat = to.latitude
            currentLon = to.longitude
        }

        val lastWalk = distance(currentLat, currentLon, destination.latitude, destination.longitude)
        if (lastWalk > maxWalk) return null
        if (lastWalk > 0) {
            val walkTime = walkMinutes(lastWalk)
            legs += JourneyLeg.Walk(lastWalk, walkTime)
            minutes += walkTime
            walkMeters += lastWalk
        }

        if (legs.isEmpty()) return null
        return Journey(legs, minutes, walkMeters, fare)
    }

    // --- Recorridos ---

    /** Dónde se sube o se baja: el punto, a qué altura del recorrido cae y qué es. */
    private class Access(val stop: Stop, val along: Double, val kind: RidePoint)

    /**
     * El recorrido de una ruta como línea medible: su trazado grabado si lo
     * tiene y, si no, la unión de sus paradas.
     */
    private class Track(val route: BusRoute) {

        private val points: List<GeoCoordinate>

        /** Metros recorridos desde el principio hasta cada punto. */
        private val alongs: DoubleArray

        val length: Double

        /** El rectángulo que encierra el recorrido. */
        private val south: Double
        private val north: Double
        private val west: Double
        private val east: Double

        init {
            val raw = route.path?.takeIf { it.size >= 2 }
                ?: route.stops.map { GeoCoordinate(it.latitude, it.longitude) }
            // Un trazado grabado trae miles de puntos casi pegados: para medir
            // sobran, y calcular con todos sería lento.
            val kept = mutableListOf<GeoCoordinate>()
            raw.forEachIndexed { index, point ->
                val last = kept.lastOrNull()
                val far = last == null ||
                    distance(last.latitude, last.longitude, point.latitude, point.longitude) >= TRACK_MIN_GAP_METERS
                if (far || index == raw.lastIndex) kept += point
            }
            points = kept
            alongs = DoubleArray(points.size)
            for (i in 1 until points.size) {
                alongs[i] = alongs[i - 1] + distance(
                    points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude
                )
            }
            length = alongs.lastOrNull() ?: 0.0
            south = points.minOf { it.latitude }
            north = points.maxOf { it.latitude }
            west = points.minOf { it.longitude }
            east = points.maxOf { it.longitude }
        }

        /** Si los rectángulos de los dos recorridos quedan a menos de [meters]: una criba barata. */
        fun passesNear(other: Track, meters: Double): Boolean {
            val margin = meters / 111_000.0
            return south - margin <= other.north && other.south - margin <= north &&
                west - margin <= other.east && other.west - margin <= east
        }

        /** Por dónde subir o bajar para un punto dado; `null` si el recorrido queda a más de [limit]. */
        fun access(latitude: Double, longitude: Double, limit: Double): Access? {
            val projection = project(latitude, longitude) ?: return null
            if (projection.meters > limit) return null

            val nearest = nearestStop(projection.point) ?: return null
            // Junto a una parada conocida se usa la parada, salvo que por ello
            // haya que caminar más de la cuenta.
            if (nearest.meters <= SNAP_TO_STOP_METERS &&
                distance(nearest.stop.latitude, nearest.stop.longitude, latitude, longitude) <= limit
            ) {
                return atStop(nearest.stop)
            }
            return onRoute(projection, nearest)
        }

        /** El punto del recorrido a [along] metros del principio. */
        fun accessAt(along: Double): Access {
            val point = pointAt(along)
            val projection = Projection(point, along.coerceIn(0.0, length), 0.0)
            val nearest = nearestStop(point) ?: return Access(Stop(route.name, point.latitude, point.longitude), along, RidePoint.ON_ROUTE)
            return if (nearest.meters <= SNAP_TO_STOP_METERS) atStop(nearest.stop) else onRoute(projection, nearest)
        }

        fun atStop(stop: Stop): Access {
            val along = project(stop.latitude, stop.longitude)?.along ?: 0.0
            return Access(stop, along, RidePoint.STOP)
        }

        private fun onRoute(projection: Projection, nearest: StopDistance) = Access(
            stop = Stop(nearest.stop.name, projection.point.latitude, projection.point.longitude),
            along = projection.along,
            kind = if (nearest.meters <= NEAR_STOP_METERS) RidePoint.NEAR_STOP else RidePoint.ON_ROUTE
        )

        private fun nearestStop(point: GeoCoordinate): StopDistance? = route.stops
            .map { StopDistance(it, distance(it.latitude, it.longitude, point.latitude, point.longitude)) }
            .minByOrNull { it.meters }

        fun pointAt(along: Double): GeoCoordinate {
            if (points.isEmpty()) return GeoCoordinate(0.0, 0.0)
            val target = along.coerceIn(0.0, length)
            val next = (1 until points.size).firstOrNull { alongs[it] >= target } ?: return points.last()
            val span = alongs[next] - alongs[next - 1]
            val t = if (span <= 0) 0.0 else (target - alongs[next - 1]) / span
            return between(points[next - 1], points[next], t)
        }

        /** El tramo del recorrido entre dos alturas, en el sentido en que se viaja. */
        fun slice(from: Double, to: Double): List<GeoCoordinate> {
            val low = minOf(from, to)
            val high = maxOf(from, to)
            val line = buildList {
                add(pointAt(low))
                points.forEachIndexed { index, point -> if (alongs[index] > low && alongs[index] < high) add(point) }
                add(pointAt(high))
            }
            return if (from <= to) line else line.reversed()
        }

        /** El tiempo de la ruta completa, repartido según lo que se recorre de ella. */
        fun rideMinutes(rideMeters: Double): Int {
            if (length <= 0) return route.durationMinutes.coerceAtLeast(1)
            return ceil(route.durationMinutes * rideMeters / length).toInt().coerceAtLeast(1)
        }

        /** El punto del recorrido más cercano a una posición. */
        private fun project(latitude: Double, longitude: Double): Projection? {
            if (points.size < 2) return null
            // A esta escala la tierra es plana: se trabaja en metros alrededor del punto.
            val metersPerLon = METERS_PER_DEGREE_LON * cos(Math.toRadians(latitude))
            var best: Projection? = null
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val ax = (a.longitude - longitude) * metersPerLon
                val ay = (a.latitude - latitude) * METERS_PER_DEGREE_LAT
                val bx = (b.longitude - longitude) * metersPerLon
                val by = (b.latitude - latitude) * METERS_PER_DEGREE_LAT
                val dx = bx - ax
                val dy = by - ay
                val lengthSquared = dx * dx + dy * dy
                val t = if (lengthSquared <= 0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
                val point = between(a, b, t)
                val meters = distance(point.latitude, point.longitude, latitude, longitude)
                val current = best
            if (current == null || meters < current.meters) {
                    best = Projection(point, alongs[i - 1] + (alongs[i] - alongs[i - 1]) * t, meters)
                }
            }
            return best
        }

        private fun between(a: GeoCoordinate, b: GeoCoordinate, t: Double) = GeoCoordinate(
            a.latitude + (b.latitude - a.latitude) * t,
            a.longitude + (b.longitude - a.longitude) * t
        )
    }

    private class Projection(val point: GeoCoordinate, val along: Double, val meters: Double)

    /** Preparar un recorrido cuesta: se guarda mientras la ruta no cambie. */
    private val tracks = HashMap<String, Track>()

    private fun trackOf(route: BusRoute): Track = synchronized(tracks) {
        tracks[route.id]?.takeIf { it.route === route }
            ?: Track(route).also { tracks[route.id] = it }
    }

    // --- Medidas ---

    private data class StopDistance(val stop: Stop, val meters: Double)

    private fun walkMinutes(meters: Double): Int =
        ceil(meters / NearbyRoutes.WALKING_METERS_PER_MINUTE).toInt().coerceAtLeast(1)

    private fun nearestPathIndex(path: List<GeoCoordinate>, stop: Stop): Int {
        var best = 0
        var bestMeters = Double.MAX_VALUE
        path.forEachIndexed { index, point ->
            val meters = distance(point.latitude, point.longitude, stop.latitude, stop.longitude)
            if (meters < bestMeters) {
                bestMeters = meters
                best = index
            }
        }
        return best
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        NearbyRoutes.distanceMeters(lat1, lon1, lat2, lon2)

    private const val METERS_PER_DEGREE_LAT = 110_540.0
    private const val METERS_PER_DEGREE_LON = 111_320.0
}

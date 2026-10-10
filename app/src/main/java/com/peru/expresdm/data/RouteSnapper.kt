package com.peru.expresdm.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajusta a las calles los recorridos grabados, en segundo plano.
 *
 * El servidor público de rutas atiende despacio las peticiones seguidas (unos
 * 9 s cada una), así que ajustar un recorrido de 10 km lleva un par de minutos.
 * El trabajo vive aquí, fuera de las pantallas: el usuario puede guardar la
 * ruta sin esperar y el ajuste se aplica cuando termine.
 */
object RouteSnapper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ajustes lanzados, por clave, para no pedir dos veces el mismo en una sesión. */
    private val jobs = mutableMapOf<String, Deferred<List<GeoCoordinate>?>>()

    /** Empieza (o reutiliza) el ajuste de [trace]; el resultado es `null` si no se pudo. */
    fun snap(router: StreetRouter, key: String, trace: List<GeoCoordinate>): Deferred<List<GeoCoordinate>?> =
        synchronized(jobs) {
            jobs.getOrPut(key) { scope.async { router.matchTrace(trace)?.takeIf { it.size >= 2 } } }
        }

    /** Ajusta una ruta grabada que aún no sigue las calles. */
    fun snapRoute(router: StreetRouter, route: BusRoute) {
        val path = route.path ?: return
        if (!route.createdByUser || route.pathSnapped) return
        applyWhenReady(route.id, snap(router, route.id, path))
    }

    /** Cuando termine [snapping], guarda el recorrido ajustado en la ruta [routeId]. */
    fun applyWhenReady(routeId: String, snapping: Deferred<List<GeoCoordinate>?>) {
        scope.launch {
            val matched = snapping.await() ?: return@launch
            // El repositorio se toca desde el hilo principal, como el resto de la app.
            withContext(Dispatchers.Main) {
                val current = RouteRepository.byId(routeId) ?: return@withContext
                if (!current.pathSnapped) RouteRepository.add(withPath(current, matched))
            }
        }
    }

    /** La ruta con el recorrido ajustado y sus paradas llevadas encima de él. */
    fun withPath(route: BusRoute, path: List<GeoCoordinate>): BusRoute = route.copy(
        path = path,
        pathSnapped = true,
        stops = route.stops.map { stop ->
            val onPath = PathCleaner.snapToPath(GeoCoordinate(stop.latitude, stop.longitude), path)
            stop.copy(latitude = onPath.latitude, longitude = onPath.longitude)
        }
    )
}

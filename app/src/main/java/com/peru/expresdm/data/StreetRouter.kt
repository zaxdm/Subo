package com.peru.expresdm.data

import android.content.Context
import android.util.Log
import com.peru.expresdm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Un punto del mapa, sin depender de osmdroid. */
data class GeoCoordinate(val latitude: Double, val longitude: Double)

/** Un camino por calles: los puntos para dibujarlo, cuánto mide y cuánto se tarda. */
data class StreetPath(
    val points: List<GeoCoordinate>,
    val meters: Double,
    val seconds: Double
)

/**
 * Calcula caminos que siguen las calles con el servicio OSRM público de FOSSGIS
 * (routing.openstreetmap.de), basado en OpenStreetMap: gratuito y sin clave.
 *
 * Sus condiciones piden identificarse con un user agent propio, no pasar de una
 * petición por segundo, evitar el uso intensivo y mostrar la atribución. Por eso
 * las peticiones se espacian y los trazados de las rutas, que no cambian, se
 * guardan en disco y solo se piden una vez.
 *
 * Si el servicio no responde (sin conexión, saturado) devuelve `null` y la app
 * dibuja líneas rectas entre paradas.
 */
class StreetRouter private constructor(context: Context) {

    private val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR).apply { mkdirs() }

    private val memory = object : LinkedHashMap<String, StreetPath>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreetPath>?) =
            size > MAX_MEMORY_ENTRIES
    }

    private val throttle = Mutex()
    private var lastRequestAt = 0L

    /** Trazado de una ruta de bus pasando por todas sus paradas, en orden. */
    suspend fun busPath(route: BusRoute): StreetPath? = withContext(Dispatchers.IO) {
        // El hash de las paradas invalida la caché si cambia el catálogo.
        val key = "bus_${route.id}_${route.stops.hashCode()}"
        cached(key, fromDisk = true) ?: request(
            PROFILE_CAR,
            route.stops.map { GeoCoordinate(it.latitude, it.longitude) }
        )?.also { store(key, it, persist = true) }
    }

    /** Camino a pie desde el usuario hasta una parada. */
    suspend fun walkingPath(from: UserPosition, to: Stop): StreetPath? = withContext(Dispatchers.IO) {
        // Con 4 decimales (unos 10 m) reutilizamos el camino si el usuario apenas se movió.
        val key = String.format(
            Locale.US, "walk_%.4f_%.4f_%.5f_%.5f", from.latitude, from.longitude, to.latitude, to.longitude
        )
        val ends = listOf(GeoCoordinate(from.latitude, from.longitude), GeoCoordinate(to.latitude, to.longitude))
        val straight = NearbyRoutes.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        // El mapa no deja caminar por algunas vías grandes (una vía de
        // evitamiento, una carretera) aunque la gente sí lo haga por su berma,
        // y entonces manda a dar un rodeo absurdo. Si pasa, se prueba el camino
        // de los carros, que sí va por ellas; y si también rodea, no se devuelve
        // ninguno y se dibuja la línea recta.
        cached(key, fromDisk = false)
            ?: (request(PROFILE_FOOT, ends)?.takeUnless { isDetour(it.meters, straight) }
                ?: request(PROFILE_CAR, ends)?.takeUnless { isDetour(it.meters, straight) })
                ?.also { store(key, it, persist = false) }
    }

    private fun cached(key: String, fromDisk: Boolean): StreetPath? {
        synchronized(memory) { memory[key] }?.let { return it }
        if (!fromDisk) return null

        val file = File(cacheDir, "$key.json")
        if (!file.exists()) return null
        val path = runCatching { decode(file.readText()) }.getOrNull() ?: return null
        synchronized(memory) { memory[key] = path }
        return path
    }

    private fun store(key: String, path: StreetPath, persist: Boolean) {
        synchronized(memory) { memory[key] = path }
        if (persist) {
            runCatching { File(cacheDir, "$key.json").writeText(encode(path)) }
                .onFailure { Log.w(TAG, "No se pudo guardar el trazado en caché", it) }
        }
    }

    /**
     * Ajusta un rastro GPS a las calles por donde pasó, con el servicio "match"
     * de OSRM: el recorrido deja de verse rayado y sigue las calles de verdad.
     *
     * El servicio admite un número limitado de puntos por petición, así que un
     * recorrido largo se ajusta por tramos. Un tramo que no se pueda ajustar, o
     * que salga con un rodeo absurdo, se queda con el rastro limpio. Devuelve
     * `null` si no se ajustó ningún tramo (sin conexión, por ejemplo).
     */
    suspend fun matchTrace(trace: List<GeoCoordinate>): List<GeoCoordinate>? = withContext(Dispatchers.IO) {
        // Un recorrido muy largo se muestrea más espaciado, para no pasar de
        // MAX_MATCH_REQUESTS peticiones: el servicio es público y pide no abusar.
        val spacing = maxOf(
            MATCH_SPACING_METERS,
            PathCleaner.lengthMeters(trace) / (MAX_MATCH_REQUESTS * (MATCH_MAX_POINTS - 1))
        )
        val sampled = sampleForMatching(trace, spacing)
        if (sampled.size < 2) return@withContext null

        val chunks = (0 until sampled.lastIndex step MATCH_MAX_POINTS - 1).map { start ->
            sampled.subList(start, minOf(start + MATCH_MAX_POINTS, sampled.size))
        }

        val result = mutableListOf<GeoCoordinate>()
        var anyMatched = false
        for (chunk in chunks) {
            val matched = throttled(buildMatchUrl(chunk)) { parseMatch(it) }?.points
            val plausible = matched != null &&
                PathCleaner.lengthMeters(matched) <= PathCleaner.lengthMeters(chunk) * MAX_DETOUR_RATIO + MAX_DETOUR_EXTRA_METERS
            val piece = if (plausible) matched!! else chunk
            if (plausible) anyMatched = true
            // Los tramos comparten un punto: no se repite al unirlos.
            result += if (result.isEmpty()) piece else piece.drop(1)
        }
        if (anyMatched) result else null
    }

    private suspend fun request(profile: String, points: List<GeoCoordinate>): StreetPath? =
        throttled(buildUrl(profile, points)) { parseRoute(it) }

    /** Respeta el límite del servicio: como mucho una petición por segundo. */
    private suspend fun throttled(url: String, parse: (String) -> StreetPath?): StreetPath? =
        throttle.withLock {
            val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            fetch(url, parse)
        }

    private fun fetch(url: String, parse: (String) -> StreetPath?): StreetPath? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "El servicio de rutas respondió ${connection.responseCode}")
                null
            } else {
                parse(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } catch (e: IOException) {
            Log.w(TAG, "Sin conexión con el servicio de rutas", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "StreetRouter"
        private const val BASE_URL = "https://routing.openstreetmap.de"
        internal const val PROFILE_CAR = "routed-car"

        /** Un camino a pie tan largo frente a la línea recta es un rodeo, no un camino. */
        internal fun isDetour(pathMeters: Double, straightMeters: Double): Boolean =
            pathMeters > straightMeters * 1.7 + 120.0
        internal const val PROFILE_FOOT = "routed-foot"

        private const val CACHE_DIR = "street_paths"
        private const val MAX_MEMORY_ENTRIES = 40
        private const val MIN_INTERVAL_MS = 1_100L
        private const val TIMEOUT_MS = 15_000

        private val USER_AGENT =
            "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (Subo, Trujillo)"

        /** Puntos por petición de ajuste: el servidor de FOSSGIS rechaza más de 10 ("TooBig"). */
        private const val MATCH_MAX_POINTS = 10

        /**
         * Separación entre los puntos que se envían. El servicio sigue las calles
         * entre un punto y el siguiente, así que no hacen falta más densos.
         */
        private const val MATCH_SPACING_METERS = 70.0

        /** Tope de peticiones por recorrido (con 1 por segundo, medio minuto). */
        private const val MAX_MATCH_REQUESTS = 30

        /** Hasta dónde puede estar la calle real de cada punto grabado. */
        private const val MATCH_RADIUS_METERS = 30

        /** Un ajuste mucho más largo que el rastro es un rodeo inventado. */
        private const val MAX_DETOUR_RATIO = 1.5
        private const val MAX_DETOUR_EXTRA_METERS = 200.0

        /** Deja un punto cada [spacingMeters] como mínimo, conservando siempre el primero y el último. */
        internal fun sampleForMatching(
            trace: List<GeoCoordinate>,
            spacingMeters: Double = MATCH_SPACING_METERS
        ): List<GeoCoordinate> {
            if (trace.size < 2) return trace
            val sampled = mutableListOf(trace.first())
            for (point in trace.drop(1).dropLast(1)) {
                val last = sampled.last()
                if (NearbyRoutes.distanceMeters(last.latitude, last.longitude, point.latitude, point.longitude) >= spacingMeters) {
                    sampled += point
                }
            }
            sampled += trace.last()
            return sampled
        }

        /**
         * URL del servicio "match". `gaps=ignore` evita que se corte en tramos por
         * un hueco de señal y `tidy` quita puntos redundantes del rastro.
         */
        fun buildMatchUrl(points: List<GeoCoordinate>): String {
            val coordinates = points.joinToString(";") {
                String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
            }
            val radiuses = points.joinToString(";") { MATCH_RADIUS_METERS.toString() }
            return "$BASE_URL/$PROFILE_CAR/match/v1/driving/$coordinates" +
                "?overview=full&geometries=geojson&gaps=ignore&tidy=true&radiuses=$radiuses"
        }

        /** Lee la respuesta de "match", uniendo en orden todos los tramos ajustados. */
        fun parseMatch(json: String): StreetPath? = try {
            val root = JSONObject(json)
            val matchings = root.optJSONArray("matchings")
            if (root.optString("code") != "Ok" || matchings == null) {
                null
            } else {
                val points = mutableListOf<GeoCoordinate>()
                var meters = 0.0
                var seconds = 0.0
                for (m in 0 until matchings.length()) {
                    val matching = matchings.getJSONObject(m)
                    val coordinates = matching.getJSONObject("geometry").getJSONArray("coordinates")
                    for (i in 0 until coordinates.length()) {
                        val pair = coordinates.getJSONArray(i)
                        points += GeoCoordinate(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
                    }
                    meters += matching.optDouble("distance", 0.0)
                    seconds += matching.optDouble("duration", 0.0)
                }
                if (points.size < 2) null else StreetPath(points, meters, seconds)
            }
        } catch (e: JSONException) {
            null
        }

        /**
         * URL de OSRM. Las coordenadas van como longitud,latitud separadas por
         * punto y coma, y siempre con punto decimal: en un teléfono en español
         * el formato por defecto usaría coma y la petición fallaría.
         */
        fun buildUrl(profile: String, points: List<GeoCoordinate>): String {
            val coordinates = points.joinToString(";") {
                String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
            }
            return "$BASE_URL/$profile/route/v1/driving/$coordinates?overview=full&geometries=geojson"
        }

        /** Lee la respuesta de OSRM; `null` si no trae un camino usable. */
        fun parseRoute(json: String): StreetPath? = try {
            val root = JSONObject(json)
            if (root.optString("code") != "Ok") {
                null
            } else {
                val route = root.getJSONArray("routes").getJSONObject(0)
                val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = (0 until coordinates.length()).map { i ->
                    val pair = coordinates.getJSONArray(i)
                    // GeoJSON guarda [longitud, latitud].
                    GeoCoordinate(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
                }
                if (points.size < 2) {
                    null
                } else {
                    StreetPath(points, route.optDouble("distance", 0.0), route.optDouble("duration", 0.0))
                }
            }
        } catch (e: JSONException) {
            null
        }

        fun encode(path: StreetPath): String = JSONObject()
            .put("meters", path.meters)
            .put("seconds", path.seconds)
            .put(
                "points",
                JSONArray().apply {
                    path.points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                }
            )
            .toString()

        fun decode(json: String): StreetPath? = try {
            val root = JSONObject(json)
            val array = root.getJSONArray("points")
            val points = (0 until array.length()).map { i ->
                val pair = array.getJSONArray(i)
                GeoCoordinate(pair.getDouble(0), pair.getDouble(1))
            }
            if (points.size < 2) null else StreetPath(points, root.getDouble("meters"), root.getDouble("seconds"))
        } catch (e: JSONException) {
            null
        }

        @Volatile
        private var instance: StreetRouter? = null

        fun get(context: Context): StreetRouter =
            instance ?: synchronized(this) {
                instance ?: StreetRouter(context).also { instance = it }
            }
    }
}

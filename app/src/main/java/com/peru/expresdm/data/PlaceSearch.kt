package com.peru.expresdm.data

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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Un lugar encontrado en el mapa. */
data class Place(
    val name: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Busca lugares de Trujillo y traduce posiciones a nombres de calle, con
 * Nominatim, el buscador de OpenStreetMap: gratuito y sin clave.
 *
 * Sus condiciones son estrictas y las respetamos aquí:
 * - como mucho una petición por segundo, sumando a todos los usuarios;
 * - nada de buscar mientras se escribe: solo al pulsar "Buscar";
 * - identificarse con un user agent propio y guardar en caché lo ya buscado.
 *
 * Para una app con muchos usuarios habría que usar un buscador propio.
 */
object PlaceSearch {

    private const val TAG = "PlaceSearch"
    private const val BASE_URL = "https://nominatim.openstreetmap.org"

    /** Área metropolitana de Trujillo: oeste, norte, este, sur. */
    const val TRUJILLO_VIEWBOX = "-79.20,-7.95,-78.90,-8.25"

    private const val MAX_RESULTS = 8
    private const val MIN_INTERVAL_MS = 1_100L
    private const val TIMEOUT_MS = 15_000
    private const val MAX_CACHE_ENTRIES = 50

    private val USER_AGENT =
        "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (Subo, Trujillo)"

    /** Nombres de la dirección que no aportan nada en Trujillo. */
    private val NOISE = setOf("Perú", "Peru", "La Libertad", "Trujillo")

    private val throttle = Mutex()
    private var lastRequestAt = 0L
    private val searchCache = lruCache<List<Place>>()
    private val reverseCache = lruCache<String>()

    /** Lugares que coinciden con [query]; `null` si no hubo conexión con el servicio. */
    suspend fun search(query: String): List<Place>? = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase(Locale.ROOT)
        if (key.length < 2) return@withContext emptyList()

        synchronized(searchCache) { searchCache[key] }?.let { return@withContext it }

        request(buildSearchUrl(query))
            ?.let { runCatching { parseSearch(it) }.getOrNull() }
            ?.also { places -> synchronized(searchCache) { searchCache[key] = places } }
    }

    /** Nombre de la calle o del lugar en una posición; `null` si no se pudo saber. */
    suspend fun streetName(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        // Con 4 decimales (unos 10 m) no repetimos la consulta por moverse un paso.
        val key = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
        synchronized(reverseCache) { reverseCache[key] }?.let { return@withContext it }

        request(buildReverseUrl(latitude, longitude))
            ?.let { parseReverse(it) }
            ?.also { name -> synchronized(reverseCache) { reverseCache[key] = name } }
    }

    private suspend fun request(url: String): String? = throttle.withLock {
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()
        fetch(url)
    }

    private fun fetch(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "es")
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "El buscador respondió ${connection.responseCode}")
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Sin conexión con el buscador de lugares", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun buildSearchUrl(query: String): String =
        "$BASE_URL/search?format=jsonv2&limit=$MAX_RESULTS&countrycodes=pe&bounded=1" +
            "&viewbox=$TRUJILLO_VIEWBOX&q=" + URLEncoder.encode(query.trim(), "UTF-8")

    fun buildReverseUrl(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%s/reverse?format=jsonv2&zoom=17&lat=%.6f&lon=%.6f", BASE_URL, latitude, longitude)

    fun parseSearch(json: String): List<Place> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val latitude = obj.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val longitude = obj.optString("lon").toDoubleOrNull() ?: return@mapNotNull null

            val parts = obj.optString("display_name").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val name = obj.optString("name").trim().ifEmpty { parts.firstOrNull().orEmpty() }
            if (name.isEmpty()) return@mapNotNull null

            val detail = parts
                .filterNot { it == name || it in NOISE || it.all(Char::isDigit) }
                .take(2)
                .joinToString(", ")

            Place(name, detail, latitude, longitude)
        }
    }

    fun parseReverse(json: String): String? = try {
        val root = JSONObject(json)
        val address = root.optJSONObject("address")
        listOf("road", "pedestrian", "neighbourhood", "suburb")
            .firstNotNullOfOrNull { key -> address?.optString(key)?.takeIf { it.isNotBlank() } }
            ?: root.optString("name").takeIf { it.isNotBlank() }
    } catch (e: JSONException) {
        null
    }

    private fun <T> lruCache(): LinkedHashMap<String, T> =
        object : LinkedHashMap<String, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) =
                size > MAX_CACHE_ENTRIES
        }
}

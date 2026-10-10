package com.peru.expresdm.data

import android.util.Log
import com.peru.expresdm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Sugerencias de lugares del mapa mientras se escribe.
 *
 * Usa Photon (de Komoot), un buscador sobre los datos de OpenStreetMap hecho
 * para sugerir con cada letra, cosa que Nominatim ([PlaceSearch]) prohíbe.
 * Es gratuito y sin clave, con la condición de un uso razonable: por eso quien
 * llama espera a que el usuario pare de escribir, y aquí se guarda lo ya
 * buscado.
 */
object PlaceSuggest {

    private const val TAG = "PlaceSuggest"
    private const val BASE_URL = "https://photon.komoot.io/api/"

    /** Área metropolitana de Trujillo: oeste, sur, este, norte. */
    private const val TRUJILLO_BBOX = "-79.20,-8.25,-78.90,-7.95"

    private const val MAX_RESULTS = 8
    private const val TIMEOUT_MS = 10_000
    private const val MAX_CACHE_ENTRIES = 80

    /** Dos resultados con el mismo nombre a menos de esto son el mismo sitio. */
    private const val SAME_PLACE_METERS = 150.0

    private val USER_AGENT =
        "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (Ruta Norte, Trujillo)"

    private val cache = object : LinkedHashMap<String, List<Place>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) =
            size > MAX_CACHE_ENTRIES
    }

    /**
     * Lugares cuyo nombre empieza o se parece a [query], con los cercanos a
     * [near] primero; `null` si no hubo conexión con el servicio.
     */
    suspend fun suggest(query: String, near: UserPosition?): List<Place>? = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.length < 2) return@withContext emptyList()
        val key = text.lowercase(Locale.ROOT)
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        fetch(buildUrl(text, near))
            ?.let { runCatching { parse(it) }.getOrNull() }
            ?.also { places -> synchronized(cache) { cache[key] = places } }
    }

    fun buildUrl(query: String, near: UserPosition?): String {
        val bias = near?.let { String.format(Locale.US, "&lat=%.5f&lon=%.5f", it.latitude, it.longitude) }.orEmpty()
        return "$BASE_URL?limit=$MAX_RESULTS&bbox=$TRUJILLO_BBOX$bias&q=" + URLEncoder.encode(query.trim(), "UTF-8")
    }

    fun parse(json: String): List<Place> {
        val features = try {
            JSONObject(json).optJSONArray("features") ?: return emptyList()
        } catch (_: JSONException) {
            return emptyList()
        }

        val places = mutableListOf<Place>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val longitude = coordinates.optDouble(0)
            val latitude = coordinates.optDouble(1)
            if (latitude.isNaN() || longitude.isNaN()) continue

            val properties = feature.optJSONObject("properties") ?: continue
            fun text(key: String) = properties.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
            val name = text("name") ?: text("street") ?: continue

            // La calle y el barrio dicen cuál de varios sitios con el mismo nombre es.
            val detail = listOfNotNull(text("street"), text("district") ?: text("locality"), text("city"))
                .filter { it != name }
                .distinct()
                .take(2)
                .joinToString(", ")

            val repeated = places.any {
                it.name.equals(name, ignoreCase = true) &&
                    NearbyRoutes.distanceMeters(it.latitude, it.longitude, latitude, longitude) < SAME_PLACE_METERS
            }
            if (!repeated) places += Place(name, detail, latitude, longitude)
        }
        return places
    }

    private fun fetch(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "El buscador de sugerencias respondió ${connection.responseCode}")
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Sin conexión con el buscador de sugerencias", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}

package com.peru.expresdm.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

/**
 * Los sitios a los que la gente va y nombra como destino: universidades,
 * hospitales, mercados, centros comerciales, terminales, colegios, barrios.
 *
 * Vienen con la app (assets/lugares.json, sacado de OpenStreetMap), así que
 * responden al instante y sin conexión, y entienden cómo les dice la gente:
 * "UPAO", "Mall Plaza", "Hospital Regional".
 */
object KnownPlaces {

    private const val TAG = "KnownPlaces"
    private const val ASSET = "lugares.json"

    /** Un sitio con las palabras por las que se le puede encontrar. */
    internal class Entry(
        val place: Place,
        /** Nombre y apodos, ya sin tildes ni mayúsculas. */
        val names: List<String>,
        /** Lo que es: universidad, mercado… También se puede buscar por ello. */
        val kind: String,
        /** Los más buscados primero cuando dos coinciden igual de bien. */
        val rank: Int
    )

    private val KIND_ORDER = listOf(
        "Universidad", "Centro comercial", "Hospital", "Terminal", "Aeropuerto", "Mercado", "Instituto",
        "Clínica", "Supermercado", "Tienda por departamento", "Estadio", "Lugar turístico", "Museo"
    )

    @Volatile
    private var entries: List<Entry> = emptyList()

    /** Lee los sitios que trae la app. Tarda poco, pero conviene llamarlo fuera del hilo de la pantalla. */
    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        try {
            val json = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
            entries = parse(json)
        } catch (e: IOException) {
            Log.w(TAG, "No se pudieron leer los lugares conocidos", e)
        }
    }

    internal fun parse(json: String): List<Entry> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("n").trim()
            val kind = obj.optString("k").trim()
            val latitude = obj.optDouble("lat")
            val longitude = obj.optDouble("lon")
            if (name.isEmpty() || latitude.isNaN() || longitude.isNaN()) return@mapNotNull null

            val aliases = obj.optJSONArray("a")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val street = obj.optString("s").trim()
            Entry(
                place = Place(name, listOf(kind, street).filter { it.isNotEmpty() }.joinToString(" · "), latitude, longitude),
                names = (listOf(name) + aliases).map { normalize(it) },
                kind = normalize(kind),
                rank = KIND_ORDER.indexOf(kind).let { if (it < 0) KIND_ORDER.size else it }
            )
        }
    } catch (e: JSONException) {
        Log.w(TAG, "Lugares conocidos ilegibles", e)
        emptyList()
    }

    /**
     * Los sitios que coinciden con [query], los que mejor coinciden primero y,
     * a igualdad, los más buscados y los más cercanos a [near].
     */
    fun search(query: String, near: UserPosition?, limit: Int = MAX_RESULTS): List<Place> =
        search(entries, query, near, limit)

    internal fun search(all: List<Entry>, query: String, near: UserPosition?, limit: Int): List<Place> {
        val wanted = normalize(query)
        if (wanted.length < 2) return emptyList()
        val words = wanted.split(' ').filter { it.isNotEmpty() }

        return all.mapNotNull { entry -> score(entry, wanted, words)?.let { entry to it } }
            .sortedWith(
                compareBy<Pair<Entry, Int>>({ it.second }, { it.first.rank }).thenBy { (entry, _) ->
                    near?.let {
                        NearbyRoutes.distanceMeters(it.latitude, it.longitude, entry.place.latitude, entry.place.longitude)
                    } ?: 0.0
                }
            )
            .take(limit)
            .map { it.first.place }
    }

    /** Qué tan bien coincide: 0 es el nombre tal cual; `null`, que no coincide. */
    private fun score(entry: Entry, wanted: String, words: List<String>): Int? {
        if (entry.names.any { it == wanted }) return 0
        if (entry.names.any { it.startsWith(wanted) }) return 1
        if (entry.names.any { name -> name.contains(wanted) }) return 2
        // "belen hospital" o "mercado mayorista": todas las palabras, en el orden que sea.
        val everything = entry.names.joinToString(" ") + " " + entry.kind
        return if (words.size > 1 && words.all { everything.contains(it) }) 3 else null
    }

    /** Sin tildes ni mayúsculas, y con un solo espacio entre palabras. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(SPACES, " ")
            .trim()

    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private const val MAX_RESULTS = 8
}

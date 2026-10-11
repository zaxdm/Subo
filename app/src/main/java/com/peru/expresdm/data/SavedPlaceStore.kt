package com.peru.expresdm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Los destinos favoritos —casa, trabajo, la pareja…—, para ir a ellos con un
 * toque en lugar de buscarlos cada vez. Son pocos a propósito: los de todos
 * los días.
 *
 * Se guardan en el teléfono y no salen de él.
 */
class SavedPlaceStore private constructor(context: Context) {

    /** Qué es el lugar para el usuario: decide el nombre y el icono con que se muestra. */
    enum class Kind { HOME, WORK, STUDY, PARTNER, FAMILY, GYM, OTHER }

    data class Favorite(val kind: Kind, val place: Place)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(load())

    /** Los favoritos, en el orden en que se añadieron. */
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    /**
     * Guarda [place] como [kind]. Con [replacing] ocupa el sitio de ese
     * favorito; sin él se añade al final, si queda hueco.
     *
     * @return `false` si ya hay [MAX_FAVORITES] y no se reemplaza ninguno.
     */
    fun save(kind: Kind, place: Place, replacing: Favorite? = null): Boolean {
        val current = _favorites.value
        val index = current.indexOf(replacing)
        val next = when {
            index >= 0 -> current.toMutableList().apply { set(index, Favorite(kind, place)) }
            current.size < MAX_FAVORITES -> current + Favorite(kind, place)
            else -> return false
        }
        store(next)
        return true
    }

    fun remove(favorite: Favorite) = store(_favorites.value - favorite)

    private fun store(next: List<Favorite>) {
        _favorites.value = next
        prefs.edit().putString(KEY_FAVORITES, encode(next)).apply()
    }

    private fun load(): List<Favorite> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                // Un tipo que esta versión no conoce no se pierde: pasa a "otro".
                val kind = Kind.entries.firstOrNull { it.name == item.optString("kind") } ?: Kind.OTHER
                Favorite(
                    kind,
                    Place(
                        name = item.getString("name"),
                        detail = item.optString("detail"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude")
                    )
                )
            }.take(MAX_FAVORITES)
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun encode(favorites: List<Favorite>): String = JSONArray().apply {
        favorites.forEach { favorite ->
            put(
                JSONObject()
                    .put("kind", favorite.kind.name)
                    .put("name", favorite.place.name)
                    .put("detail", favorite.place.detail)
                    .put("latitude", favorite.place.latitude)
                    .put("longitude", favorite.place.longitude)
            )
        }
    }.toString()

    companion object {
        /** Más que estos dejan de ser "los de siempre" y vuelven a ser una lista que recorrer. */
        const val MAX_FAVORITES = 4

        private const val PREFS_NAME = "saved_places"
        private const val KEY_FAVORITES = "favorites"

        @Volatile
        private var instance: SavedPlaceStore? = null

        fun get(context: Context): SavedPlaceStore =
            instance ?: synchronized(this) {
                instance ?: SavedPlaceStore(context).also { instance = it }
            }
    }
}

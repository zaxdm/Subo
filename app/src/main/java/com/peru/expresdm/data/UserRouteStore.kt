package com.peru.expresdm.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Guarda en el teléfono las rutas que graban los usuarios.
 *
 * Un JSON en el almacenamiento privado de la app: suficiente mientras no haya
 * servidor, y fácil de subir cuando lo haya.
 */
class UserRouteStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(): List<BusRoute> =
        if (!file.exists()) {
            emptyList()
        } else {
            runCatching { decodeRoutes(file.readText()) }
                .onFailure { Log.w(TAG, "No se pudieron leer las rutas del usuario", it) }
                .getOrDefault(emptyList())
        }

    fun saveAll(routes: List<BusRoute>) {
        runCatching { file.writeText(encodeRoutes(routes)) }
            .onFailure { Log.e(TAG, "No se pudieron guardar las rutas del usuario", it) }
    }

    companion object {
        private const val TAG = "UserRouteStore"
        private const val FILE_NAME = "rutas_usuario.json"

        /** 2: el trazado se guarda ya limpio. */
        private const val PATH_VERSION = 2

        fun encodeRoutes(routes: List<BusRoute>): String {
            val array = JSONArray()
            routes.forEach { route ->
                array.put(
                    JSONObject()
                        .put("id", route.id)
                        .put("letter", route.letter)
                        .put("name", route.name)
                        .put("origin", route.origin)
                        .put("destination", route.destination)
                        .put("durationMinutes", route.durationMinutes)
                        .put("fare", route.fare)
                        .put("schedule", route.schedule)
                        .put("accentIndex", route.accentIndex)
                        .put("pathVersion", PATH_VERSION)
                        .put("pathSnapped", route.pathSnapped)
                        .put("vehicleName", route.vehicleName)
                        .put("imageUrl", route.imageUrl ?: JSONObject.NULL)
                        .put("description", route.description)
                        .put("bidirectional", route.bidirectional)
                        .put(
                            "stops",
                            JSONArray().apply {
                                route.stops.forEach { stop ->
                                    put(
                                        JSONObject()
                                            .put("name", stop.name)
                                            .put("lat", stop.latitude)
                                            .put("lon", stop.longitude)
                                    )
                                }
                            }
                        )
                        .put(
                            "path",
                            JSONArray().apply {
                                route.path.orEmpty().forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                            }
                        )
                )
            }
            return array.toString()
        }

        /** Lee las rutas; las que estén dañadas se saltan en lugar de perderlas todas. */
        fun decodeRoutes(json: String): List<BusRoute> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                try {
                    decodeRoute(array.getJSONObject(i))
                } catch (e: JSONException) {
                    Log.w(TAG, "Ruta dañada en el archivo, se omite", e)
                    null
                }
            }
        }

        private fun decodeRoute(obj: JSONObject): BusRoute {
            val stopsArray = obj.getJSONArray("stops")
            val stops = (0 until stopsArray.length()).map { i ->
                val stop = stopsArray.getJSONObject(i)
                Stop(stop.getString("name"), stop.getDouble("lat"), stop.getDouble("lon"))
            }
            val pathArray = obj.optJSONArray("path") ?: JSONArray()
            val stored = (0 until pathArray.length()).map { i ->
                val pair = pathArray.getJSONArray(i)
                GeoCoordinate(pair.getDouble(0), pair.getDouble(1))
            }
            // Las rutas de la primera versión guardaban el GPS en bruto, con
            // todo su ruido: se limpian al leerlas.
            val path = if (obj.optInt("pathVersion", 1) < PATH_VERSION) PathCleaner.cleanGeometry(stored) else stored
            return BusRoute(
                id = obj.getString("id"),
                letter = obj.getString("letter"),
                name = obj.getString("name"),
                origin = obj.getString("origin"),
                destination = obj.getString("destination"),
                durationMinutes = obj.getInt("durationMinutes"),
                fare = obj.getDouble("fare"),
                schedule = obj.getString("schedule"),
                stops = stops,
                accentIndex = obj.getInt("accentIndex"),
                path = path.takeIf { it.size >= 2 },
                pathSnapped = obj.optBoolean("pathSnapped"),
                createdByUser = true,
                vehicleName = obj.optString("vehicleName"),
                imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                description = obj.optString("description"),
                bidirectional = obj.optBoolean("bidirectional", true)
            )
        }
    }
}

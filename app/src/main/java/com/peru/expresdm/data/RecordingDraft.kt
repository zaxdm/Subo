package com.peru.expresdm.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** Un punto registrado mientras se graba una ruta. */
data class RecordingPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    /** Error estimado de la lectura, en metros; `null` si el GPS no lo dio. */
    val accuracy: Float? = null
)

/** Grabación de una ruta nueva, en curso o pendiente de guardar. */
data class RecordingDraft(
    val startedAt: Long,
    val points: List<RecordingPoint>
) {
    val distanceMeters: Double
        get() = points.zipWithNext { a, b ->
            NearbyRoutes.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }.sum()

    /** Minutos grabados hasta el último punto (o hasta [now], si se indica). */
    fun durationMinutes(now: Long = points.lastOrNull()?.timestamp ?: startedAt): Int =
        ((now - startedAt) / 60_000L).toInt().coerceAtLeast(0)
}

/**
 * Guarda en disco la grabación mientras se hace.
 *
 * Si Android cierra la app a mitad de camino, al volver a abrirla la grabación
 * sigue ahí y se puede terminar de guardar en lugar de perder todo el trayecto.
 */
class RecordingDraftStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(): RecordingDraft? =
        if (!file.exists()) {
            null
        } else {
            runCatching { decode(file.readText()) }
                .onFailure { Log.w(TAG, "No se pudo leer la grabación en curso", it) }
                .getOrNull()
        }

    fun save(draft: RecordingDraft) {
        runCatching { file.writeText(encode(draft)) }
            .onFailure { Log.e(TAG, "No se pudo guardar la grabación en curso", it) }
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    companion object {
        private const val TAG = "RecordingDraftStore"
        private const val FILE_NAME = "grabacion_en_curso.json"

        fun encode(draft: RecordingDraft): String = JSONObject()
            .put("startedAt", draft.startedAt)
            .put(
                "points",
                JSONArray().apply {
                    draft.points.forEach { point ->
                        val entry = JSONArray().put(point.latitude).put(point.longitude).put(point.timestamp)
                        point.accuracy?.let { entry.put(it.toDouble()) }
                        put(entry)
                    }
                }
            )
            .toString()

        fun decode(json: String): RecordingDraft? = try {
            val root = JSONObject(json)
            val array = root.getJSONArray("points")
            val points = (0 until array.length()).map { i ->
                val point = array.getJSONArray(i)
                // Las grabaciones de versiones anteriores no traen la precisión.
                val accuracy = point.optDouble(3, Double.NaN).takeUnless { it.isNaN() }?.toFloat()
                RecordingPoint(point.getDouble(0), point.getDouble(1), point.getLong(2), accuracy)
            }
            RecordingDraft(root.getLong("startedAt"), points)
        } catch (e: JSONException) {
            null
        }
    }
}

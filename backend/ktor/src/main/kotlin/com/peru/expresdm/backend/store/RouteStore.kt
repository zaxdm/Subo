package com.peru.expresdm.backend.store

import com.peru.expresdm.backend.db.Database
import com.peru.expresdm.backend.model.CoordinateDto
import com.peru.expresdm.backend.model.RouteDto
import com.peru.expresdm.backend.model.StopDto
import com.peru.expresdm.backend.model.SubmitRouteRequest
import java.sql.ResultSet
import java.util.UUID
import kotlinx.serialization.json.Json

/**
 * Catálogo de rutas: las que trae la app y las que graban los usuarios.
 *
 * Las paradas y el trazado se guardan como JSON en una columna de texto. Son
 * datos que siempre se leen enteros y nunca se consultan por partes, así que
 * una tabla aparte solo añadiría trabajo.
 */
class RouteStore(private val db: Database) {

    private val json = Json { ignoreUnknownKeys = true }

    fun all(viewerId: String?): List<RouteDto> = db.query(
        """
        SELECT * FROM routes
        WHERE status = 'APPROVED' OR author_id = ?
        ORDER BY created_by_user, created_at
        """.trimIndent(),
        viewerId
    ) { rs ->
        buildList {
            while (rs.next()) add(rs.toRoute(viewerId))
        }
    }

    fun byId(id: String, viewerId: String?): RouteDto? = db.query(
        "SELECT * FROM routes WHERE id = ?",
        id
    ) { rs -> if (rs.next()) rs.toRoute(viewerId) else null }

    fun submit(authorId: String, request: SubmitRouteRequest, now: Long = System.currentTimeMillis()): RouteDto {
        // Si la app propone un id y nadie lo usa, se respeta: así la ruta es la
        // misma en el teléfono y en el servidor.
        val proposed = request.id?.trim()?.takeIf { it.isNotEmpty() && byId(it, null) == null }
        val id = proposed ?: "usuario-${UUID.randomUUID()}"
        val accentIndex = db.query("SELECT COUNT(*) FROM routes") { rs ->
            rs.next()
            rs.getInt(1)
        }
        db.update(
            """
            INSERT INTO routes (id, letter, name, origin, destination, duration_minutes, fare, schedule,
                                accent_index, stops_json, path_json, path_snapped, created_by_user,
                                status, author_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 'APPROVED', ?, ?)
            """.trimIndent(),
            id, request.letter, request.name, request.origin, request.destination,
            request.durationMinutes, request.fare, request.schedule, accentIndex,
            json.encodeToString(request.stops),
            request.path?.let { json.encodeToString(it) },
            request.pathSnapped, authorId, now
        )
        return byId(id, authorId)!!
    }

    /** La borra su autor o un administrador; devuelve `false` si no le corresponde. */
    fun delete(id: String, userId: String, isAdmin: Boolean): Boolean {
        val deleted = if (isAdmin) {
            db.update("DELETE FROM routes WHERE id = ?", id)
        } else {
            db.update("DELETE FROM routes WHERE id = ? AND author_id = ?", id, userId)
        }
        return deleted > 0
    }

    fun setStatus(id: String, status: String): Boolean =
        db.update("UPDATE routes SET status = ? WHERE id = ?", status, id) > 0

    /** Mete el catálogo inicial la primera vez que arranca el servidor. */
    fun seedIfEmpty(catalog: List<RouteDto>, now: Long = System.currentTimeMillis()) {
        val empty = db.query("SELECT COUNT(*) FROM routes") { rs ->
            rs.next()
            rs.getInt(1) == 0
        }
        if (!empty) return

        catalog.forEach { route ->
            db.update(
                """
                INSERT INTO routes (id, letter, name, origin, destination, duration_minutes, fare, schedule,
                                    accent_index, stops_json, path_json, path_snapped, created_by_user,
                                    status, author_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 'APPROVED', NULL, ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                route.id, route.letter, route.name, route.origin, route.destination,
                route.durationMinutes, route.fare, route.schedule, route.accentIndex,
                json.encodeToString(route.stops),
                route.path?.let { json.encodeToString(it) },
                route.pathSnapped, now
            )
        }
    }

    private fun ResultSet.toRoute(viewerId: String?): RouteDto {
        val author = getString("author_id")
        return RouteDto(
            id = getString("id"),
            letter = getString("letter"),
            name = getString("name"),
            origin = getString("origin"),
            destination = getString("destination"),
            durationMinutes = getInt("duration_minutes"),
            fare = getDouble("fare"),
            schedule = getString("schedule"),
            stops = json.decodeFromString<List<StopDto>>(getString("stops_json")),
            accentIndex = getInt("accent_index"),
            path = getString("path_json")?.let { json.decodeFromString<List<CoordinateDto>>(it) },
            pathSnapped = getBoolean("path_snapped"),
            createdByUser = getBoolean("created_by_user"),
            status = getString("status"),
            createdByMe = viewerId != null && author == viewerId
        )
    }
}

package com.peru.expresdm.backend.store

import com.peru.expresdm.backend.alerts.AlertPolicy
import com.peru.expresdm.backend.db.Database
import com.peru.expresdm.backend.geo.Geo
import com.peru.expresdm.backend.model.AlertDto
import java.util.UUID

/**
 * Alertas compartidas.
 *
 * Los votos viven en su propia tabla, uno por usuario y alerta: así nadie puede
 * votar dos veces y el recuento es siempre el de verdad.
 */
class AlertStore(private val db: Database) {

    /** Alertas vigentes cerca de un punto, de la más reciente a la más antigua. */
    fun near(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        viewerId: String?,
        now: Long = System.currentTimeMillis()
    ): List<AlertDto> {
        val box = Geo.boundingBox(latitude, longitude, radiusMeters)
        val rows = db.query(
            """
            SELECT a.id, a.type, a.latitude, a.longitude, a.place_name, a.comment,
                   a.created_at, a.last_confirmed_at, a.expires_at, a.author_id,
                   COALESCE(SUM(CASE WHEN v.vote = 'STILL_THERE' THEN 1 ELSE 0 END), 0) AS confirmations,
                   COALESCE(SUM(CASE WHEN v.vote = 'GONE' THEN 1 ELSE 0 END), 0) AS dismissals,
                   MAX(CASE WHEN v.user_id = ? THEN v.vote END) AS my_vote
            FROM alerts a
            LEFT JOIN alert_votes v ON v.alert_id = a.id
            WHERE a.expires_at > ?
              AND a.latitude BETWEEN ? AND ?
              AND a.longitude BETWEEN ? AND ?
            GROUP BY a.id
            ORDER BY a.created_at DESC
            """.trimIndent(),
            viewerId, now, box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        AlertDto(
                            id = rs.getString("id"),
                            type = rs.getString("type"),
                            latitude = rs.getDouble("latitude"),
                            longitude = rs.getDouble("longitude"),
                            placeName = rs.getString("place_name"),
                            comment = rs.getString("comment"),
                            createdAt = rs.getLong("created_at"),
                            expiresAt = rs.getLong("expires_at"),
                            confirmations = rs.getInt("confirmations"),
                            dismissals = rs.getInt("dismissals"),
                            lastConfirmedAt = rs.getLong("last_confirmed_at"),
                            myVote = rs.getString("my_vote"),
                            createdByMe = viewerId != null && rs.getString("author_id") == viewerId
                        )
                    )
                }
            }
        }

        // La caja es cuadrada: el círculo de verdad se recorta aquí. También se
        // quitan las que la gente dio por terminadas.
        return rows.filter { alert ->
            Geo.distanceMeters(latitude, longitude, alert.latitude, alert.longitude) <= radiusMeters &&
                !AlertPolicy.isHidden(alert.confirmations, alert.dismissals) &&
                alert.myVote != "GONE"
        }
    }

    fun byId(id: String, viewerId: String?, now: Long = System.currentTimeMillis()): AlertDto? = db.query(
        """
        SELECT a.id, a.type, a.latitude, a.longitude, a.place_name, a.comment,
               a.created_at, a.last_confirmed_at, a.expires_at, a.author_id,
               COALESCE(SUM(CASE WHEN v.vote = 'STILL_THERE' THEN 1 ELSE 0 END), 0) AS confirmations,
               COALESCE(SUM(CASE WHEN v.vote = 'GONE' THEN 1 ELSE 0 END), 0) AS dismissals,
               MAX(CASE WHEN v.user_id = ? THEN v.vote END) AS my_vote
        FROM alerts a
        LEFT JOIN alert_votes v ON v.alert_id = a.id
        WHERE a.id = ?
        GROUP BY a.id
        """.trimIndent(),
        viewerId, id
    ) { rs ->
        if (!rs.next()) {
            null
        } else {
            AlertDto(
                id = rs.getString("id"),
                type = rs.getString("type"),
                latitude = rs.getDouble("latitude"),
                longitude = rs.getDouble("longitude"),
                placeName = rs.getString("place_name"),
                comment = rs.getString("comment"),
                createdAt = rs.getLong("created_at"),
                expiresAt = rs.getLong("expires_at"),
                confirmations = rs.getInt("confirmations"),
                dismissals = rs.getInt("dismissals"),
                lastConfirmedAt = rs.getLong("last_confirmed_at"),
                myVote = rs.getString("my_vote"),
                createdByMe = viewerId != null && rs.getString("author_id") == viewerId
            )
        }
    }

    fun publish(
        authorId: String,
        type: String,
        latitude: Double,
        longitude: Double,
        placeName: String?,
        comment: String?,
        now: Long = System.currentTimeMillis()
    ): AlertDto {
        val id = UUID.randomUUID().toString()
        val expiresAt = AlertPolicy.expiresAt(type, now, now)
        db.update(
            """
            INSERT INTO alerts (id, type, latitude, longitude, place_name, comment,
                                created_at, last_confirmed_at, expires_at, author_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, type, latitude, longitude, placeName, AlertPolicy.cleanComment(comment), now, now, expiresAt, authorId
        )
        return AlertDto(
            id = id,
            type = type,
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            comment = AlertPolicy.cleanComment(comment),
            createdAt = now,
            expiresAt = expiresAt,
            confirmations = 0,
            dismissals = 0,
            lastConfirmedAt = now,
            myVote = null,
            createdByMe = true
        )
    }

    /**
     * Guarda el voto de un usuario (cambiándolo si ya había votado) y alarga la
     * vida de la alerta cuando alguien confirma que sigue ahí.
     */
    fun vote(alertId: String, userId: String, vote: String, now: Long = System.currentTimeMillis()): AlertDto? {
        val exists = db.query("SELECT 1 FROM alerts WHERE id = ?", alertId) { it.next() }
        if (!exists) return null

        db.transaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO alert_votes (alert_id, user_id, vote, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (alert_id, user_id) DO UPDATE SET vote = EXCLUDED.vote, created_at = EXCLUDED.created_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, alertId)
                statement.setString(2, userId)
                statement.setString(3, vote)
                statement.setLong(4, now)
                statement.executeUpdate()
            }

            if (vote == "STILL_THERE") {
                connection.prepareStatement(
                    """
                    UPDATE alerts
                    SET last_confirmed_at = ?,
                        expires_at = GREATEST(expires_at, ?)
                    WHERE id = ?
                    """.trimIndent()
                ).use { statement ->
                    val type = connection.prepareStatement("SELECT type, created_at FROM alerts WHERE id = ?").use { read ->
                        read.setString(1, alertId)
                        read.executeQuery().use { rs ->
                            rs.next()
                            rs.getString("type") to rs.getLong("created_at")
                        }
                    }
                    statement.setLong(1, now)
                    statement.setLong(2, AlertPolicy.expiresAt(type.first, type.second, now))
                    statement.setString(3, alertId)
                    statement.executeUpdate()
                }
            }
        }
        return byId(alertId, userId, now)
    }

    /** Borra las caducadas: la base de datos no crece para siempre. */
    fun deleteExpired(now: Long = System.currentTimeMillis()): Int =
        db.update("DELETE FROM alerts WHERE expires_at < ?", now)
}

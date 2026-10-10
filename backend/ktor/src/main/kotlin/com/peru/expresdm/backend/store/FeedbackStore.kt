package com.peru.expresdm.backend.store

import com.peru.expresdm.backend.db.Database
import java.util.UUID

/** Buzón de sugerencias: lo que la gente escribe desde Soporte en la app. */
class FeedbackStore(private val db: Database) {

    /** Guarda la sugerencia; `null` si esa cuenta ya mandó demasiadas hoy. */
    fun add(
        userId: String,
        subject: String,
        message: String,
        appVersion: String,
        device: String,
        now: Long = System.currentTimeMillis()
    ): String? {
        val sentToday = db.query(
            "SELECT COUNT(*) FROM feedback WHERE user_id = ? AND created_at > ?",
            userId, now - DAY_MS
        ) { rows -> if (rows.next()) rows.getInt(1) else 0 }
        if (sentToday >= DAILY_LIMIT) return null

        val id = UUID.randomUUID().toString()
        db.update(
            """
            INSERT INTO feedback (id, user_id, subject, message, app_version, device, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, userId, subject, message, appVersion, device, now
        )
        return id
    }

    companion object {
        /** Cuántas sugerencias puede mandar una cuenta en un día. */
        const val DAILY_LIMIT = 10
        private const val DAY_MS = 86_400_000L
    }
}

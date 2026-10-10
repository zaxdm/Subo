package com.peru.expresdm.backend.store

import com.peru.expresdm.backend.db.Database
import java.util.Locale
import java.util.UUID

data class StoredUser(
    val id: String,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val createdAt: Long
)

/** Cuentas de los usuarios. */
class UserStore(private val db: Database) {

    fun findByEmail(email: String): StoredUser? = db.query(
        "SELECT id, email, password_hash, display_name, created_at FROM users WHERE email = ?",
        normalize(email)
    ) { rs -> if (rs.next()) rs.toUser() else null }

    fun findById(id: String): StoredUser? = db.query(
        "SELECT id, email, password_hash, display_name, created_at FROM users WHERE id = ?",
        id
    ) { rs -> if (rs.next()) rs.toUser() else null }

    /** Crea la cuenta; `null` si ese correo ya estaba registrado. */
    fun create(email: String, passwordHash: String, displayName: String, now: Long = System.currentTimeMillis()): StoredUser? {
        val user = StoredUser(UUID.randomUUID().toString(), normalize(email), passwordHash, displayName.trim(), now)
        val inserted = db.update(
            """
            INSERT INTO users (id, email, password_hash, display_name, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (email) DO NOTHING
            """.trimIndent(),
            user.id, user.email, user.passwordHash, user.displayName, user.createdAt
        )
        return if (inserted == 0) null else user
    }

    /** Cambia el nombre visible; `null` si la cuenta ya no existe. */
    fun updateDisplayName(id: String, displayName: String): StoredUser? {
        val changed = db.update("UPDATE users SET display_name = ? WHERE id = ?", displayName, id)
        return if (changed == 0) null else findById(id)
    }

    private fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)

    private fun java.sql.ResultSet.toUser() = StoredUser(
        id = getString("id"),
        email = getString("email"),
        passwordHash = getString("password_hash"),
        displayName = getString("display_name"),
        createdAt = getLong("created_at")
    )
}

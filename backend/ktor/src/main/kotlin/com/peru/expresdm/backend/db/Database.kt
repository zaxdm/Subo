package com.peru.expresdm.backend.db

import com.peru.expresdm.backend.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Conexión a PostgreSQL y creación de las tablas.
 *
 * Sin ORM: el SQL está a la vista, que para un servidor de este tamaño es más
 * fácil de leer y de depurar que una capa de magia.
 */
class Database(private val dataSource: DataSource) {

    fun <T> query(sql: String, vararg params: Any?, read: (ResultSet) -> T): T =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { read(it) }
            }
        }

    fun update(sql: String, vararg params: Any?): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }

    /** Varias sentencias que tienen que cumplirse todas o ninguna. */
    fun <T> transaction(block: (Connection) -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    /**
     * Crea las tablas si no existen. Es suficiente mientras el esquema crece
     * solo añadiendo cosas; si algún día hay que cambiar columnas existentes,
     * tocará un sistema de migraciones de verdad.
     */
    fun migrate() {
        SCHEMA.forEach { update(it) }
    }

    fun close() {
        (dataSource as? HikariDataSource)?.close()
    }

    companion object {
        fun connect(config: Config): Database {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                maximumPoolSize = 10
                poolName = "expresdm"
            }
            return Database(HikariDataSource(hikari))
        }

        private val SCHEMA = listOf(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                display_name TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                -- Se marca a mano en la base de datos: por ahora no hay pantalla de administración.
                is_admin BOOLEAN NOT NULL DEFAULT FALSE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS alerts (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                latitude DOUBLE PRECISION NOT NULL,
                longitude DOUBLE PRECISION NOT NULL,
                place_name TEXT,
                comment TEXT,
                created_at BIGINT NOT NULL,
                last_confirmed_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                author_id TEXT NOT NULL REFERENCES users(id)
            )
            """,
            // El filtro por caja de coordenadas y por vigencia es el más usado.
            "CREATE INDEX IF NOT EXISTS alerts_expires_idx ON alerts (expires_at)",
            "CREATE INDEX IF NOT EXISTS alerts_position_idx ON alerts (latitude, longitude)",
            """
            CREATE TABLE IF NOT EXISTS alert_votes (
                alert_id TEXT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
                user_id TEXT NOT NULL REFERENCES users(id),
                vote TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                PRIMARY KEY (alert_id, user_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS routes (
                id TEXT PRIMARY KEY,
                letter TEXT NOT NULL,
                name TEXT NOT NULL,
                origin TEXT NOT NULL,
                destination TEXT NOT NULL,
                duration_minutes INTEGER NOT NULL,
                fare DOUBLE PRECISION NOT NULL,
                schedule TEXT NOT NULL,
                accent_index INTEGER NOT NULL,
                stops_json TEXT NOT NULL,
                path_json TEXT,
                path_snapped BOOLEAN NOT NULL DEFAULT FALSE,
                created_by_user BOOLEAN NOT NULL DEFAULT FALSE,
                status TEXT NOT NULL DEFAULT 'APPROVED',
                author_id TEXT REFERENCES users(id),
                created_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS routes_status_idx ON routes (status)",
            """
            CREATE TABLE IF NOT EXISTS feedback (
                id TEXT PRIMARY KEY,
                -- Si la cuenta se borra, la sugerencia se queda: sigue sirviendo sin autor.
                user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
                subject TEXT NOT NULL,
                message TEXT NOT NULL,
                app_version TEXT NOT NULL DEFAULT '',
                device TEXT NOT NULL DEFAULT '',
                created_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS feedback_user_idx ON feedback (user_id)"
        )
    }
}

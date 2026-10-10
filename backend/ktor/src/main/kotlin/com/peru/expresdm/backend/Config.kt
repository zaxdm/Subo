package com.peru.expresdm.backend

import java.io.File
import java.util.Properties

/**
 * Ajustes del servidor.
 *
 * Se leen de `backend/local.properties` (que no se sube a git) y, si no está,
 * de las variables de entorno. Así el mismo código sirve en tu PC y, el día que
 * se publique, en un servidor de verdad sin tocar nada.
 */
data class Config(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val jwtSecret: String,
    val geminiApiKey: String
) {
    /** Sin clave de Gemini el asistente queda desactivado, como en la app. */
    val isAssistantEnabled: Boolean get() = geminiApiKey.isNotBlank()

    companion object {
        private const val FILE_NAME = "local.properties"

        fun load(workingDir: File = File(".")): Config {
            val file = File(workingDir, FILE_NAME)
            val properties = Properties().apply {
                if (file.exists()) file.inputStream().use { load(it) }
            }

            fun value(key: String, default: String? = null): String =
                System.getenv(key) ?: properties.getProperty(key) ?: default
                    ?: error(
                        "Falta $key. Copia backend/local.properties.example como " +
                            "backend/local.properties y rellena los valores."
                    )

            return Config(
                port = value("PORT", "8080").toInt(),
                databaseUrl = value("DB_URL"),
                databaseUser = value("DB_USER"),
                databasePassword = value("DB_PASSWORD"),
                jwtSecret = value("JWT_SECRET"),
                geminiApiKey = value("GEMINI_API_KEY", "")
            )
        }
    }
}

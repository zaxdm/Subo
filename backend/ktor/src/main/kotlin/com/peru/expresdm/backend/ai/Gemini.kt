package com.peru.expresdm.backend.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * Llamadas a Gemini, ahora desde el servidor.
 *
 * Antes la app llevaba la clave dentro del APK, de donde cualquiera podía
 * sacarla. Aquí la clave no sale del servidor y además se puede limitar el uso.
 */
class Gemini(private val apiKey: String, private val model: String = DEFAULT_MODEL) {

    private val log = LoggerFactory.getLogger(Gemini::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO)

    /**
     * Pide una respuesta en JSON siguiendo [responseSchema].
     * Devuelve `null` si el servicio falla: quien llama decide qué hacer.
     */
    suspend fun generateJson(
        systemInstruction: String,
        userText: String,
        responseSchema: JsonObject,
        maxOutputTokens: Int = 1024,
        temperature: Double = 0.2
    ): JsonObject? {
        if (apiKey.isBlank()) return null

        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", userText) }) }
                    }
                )
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxOutputTokens)
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema)
            }
        }

        return try {
            val response = client.post("$BASE_URL/$model:generateContent") {
                headers { append("x-goog-api-key", apiKey) }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }
            if (!response.status.isSuccess()) {
                log.warn("Gemini respondió {}", response.status)
                return null
            }
            val text = json.parseToJsonElement(response.bodyAsText()).jsonObject["candidates"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?: return null
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            log.warn("No se pudo hablar con Gemini", error)
            null
        }
    }

    fun close() = client.close()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

        /** Esquema del asistente: una respuesta corta y, si la hay, la ruta que sirve. */
        val ASSISTANT_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("respuesta") { put("type", "string") }
                putJsonObject("rutaId") {
                    put("type", "string")
                    put("description", "Id exacto de la ruta recomendada, o vacío si ninguna sirve")
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("respuesta")) }
        }

        /** Esquema de la moderación. */
        val MODERATION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("aceptable") { put("type", "boolean") }
                putJsonObject("motivo") {
                    put("type", "string")
                    put("description", "Por qué se rechaza; vacío si es aceptable")
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("aceptable")) }
        }
    }
}

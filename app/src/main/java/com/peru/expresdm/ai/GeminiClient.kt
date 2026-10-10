package com.peru.expresdm.ai

import com.peru.expresdm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Resultado de una llamada al modelo: o texto, o un motivo de fallo legible. */
sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Failure(val reason: Reason, val detail: String? = null) : GeminiResult

    enum class Reason {
        /** No hay clave configurada en local.properties. */
        NO_API_KEY,

        /** Sin conexión o el servidor no respondió a tiempo. */
        NETWORK,

        /** La clave fue rechazada (401/403) o se agotó la cuota (429). */
        AUTH_OR_QUOTA,

        /** El modelo está saturado o devolvió un error (5xx). */
        SERVER,

        /** El modelo cortó la respuesta por filtros de seguridad. */
        BLOCKED,

        /** Respuesta con un formato que no supimos leer. */
        MALFORMED
    }
}

/**
 * Cliente mínimo de la API de Gemini.
 *
 * Usa HttpURLConnection en lugar de un SDK: son dos llamadas sencillas y así el
 * APK no carga una dependencia entera.
 *
 * ATENCIÓN: la clave viaja dentro del APK. Cualquiera que lo descompile puede
 * extraerla y gastar la cuota del proyecto. Para producción, estas llamadas
 * deben pasar por un servidor propio que guarde la clave.
 */
object GeminiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private val TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30).toInt()

    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Envía una conversación al modelo.
     *
     * @param systemInstruction instrucciones fijas sobre cómo debe comportarse.
     * @param history turnos previos, en orden; el último debe ser del usuario.
     * @param responseSchema esquema JSON si se espera salida estructurada.
     */
    suspend fun generate(
        systemInstruction: String,
        history: List<Turn>,
        responseSchema: JSONObject? = null,
        maxOutputTokens: Int = 2048,
        temperature: Double = 0.4
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext GeminiResult.Failure(GeminiResult.Reason.NO_API_KEY)
        }

        val body = buildRequestBody(
            systemInstruction, history, responseSchema, maxOutputTokens, temperature
        )

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/${BuildConfig.GEMINI_MODEL}:generateContent")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return@withContext GeminiResult.Failure(reasonFor(code), error?.take(300))
            }

            val response = connection.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            parseText(response)
        } catch (io: java.io.IOException) {
            GeminiResult.Failure(GeminiResult.Reason.NETWORK, io.message)
        } catch (json: org.json.JSONException) {
            GeminiResult.Failure(GeminiResult.Reason.MALFORMED, json.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun reasonFor(code: Int): GeminiResult.Reason = when (code) {
        400, 401, 403, 429 -> GeminiResult.Reason.AUTH_OR_QUOTA
        in 500..599 -> GeminiResult.Reason.SERVER
        else -> GeminiResult.Reason.MALFORMED
    }

    private fun buildRequestBody(
        systemInstruction: String,
        history: List<Turn>,
        responseSchema: JSONObject?,
        maxOutputTokens: Int,
        temperature: Double
    ): JSONObject {
        val contents = JSONArray()
        history.forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }

        val generationConfig = JSONObject()
            .put("maxOutputTokens", maxOutputTokens)
            .put("temperature", temperature)

        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json")
            generationConfig.put("responseSchema", responseSchema)
        }

        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            .put("contents", contents)
            .put("generationConfig", generationConfig)
    }

    /** Extrae el texto del primer candidato de la respuesta. */
    private fun parseText(response: String): GeminiResult {
        val root = JSONObject(response)

        root.optJSONObject("promptFeedback")?.optString("blockReason")?.let { reason ->
            if (reason.isNotBlank()) {
                return GeminiResult.Failure(GeminiResult.Reason.BLOCKED, reason)
            }
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return GeminiResult.Failure(GeminiResult.Reason.MALFORMED, response.take(300))
        }

        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason")
        if (finishReason == "SAFETY" || finishReason == "PROHIBITED_CONTENT") {
            return GeminiResult.Failure(GeminiResult.Reason.BLOCKED, finishReason)
        }

        // Un modelo con razonamiento puede devolver varias partes; nos quedamos
        // con todo el texto concatenado.
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: return GeminiResult.Failure(GeminiResult.Reason.MALFORMED, finishReason)

        val text = buildString {
            for (i in 0 until parts.length()) {
                parts.optJSONObject(i)?.optString("text")?.let(::append)
            }
        }.trim()

        return if (text.isEmpty()) {
            GeminiResult.Failure(GeminiResult.Reason.MALFORMED, finishReason)
        } else {
            GeminiResult.Success(text)
        }
    }

    /** Un turno de la conversación. */
    data class Turn(val text: String, val fromUser: Boolean)
}

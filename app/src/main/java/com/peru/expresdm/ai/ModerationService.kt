package com.peru.expresdm.ai

import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Veredicto sobre un texto que un usuario quiere publicar. */
sealed interface ModerationVerdict {
    /** El texto puede publicarse. */
    data object Allowed : ModerationVerdict

    /**
     * Lo rechazó el filtro local. [word] es la palabra detectada; el texto que
     * se muestra al usuario lo compone la pantalla, para que esté traducido.
     */
    data class RejectedLocally(val word: String) : ModerationVerdict

    /** Lo rechazó el modelo, que ya redacta el motivo en el idioma de la app. */
    data class RejectedByModel(val reason: String) : ModerationVerdict
}

/**
 * Filtra los reportes antes de publicarlos.
 *
 * Funciona en dos pasos:
 *
 * 1. [ProfanityFilter], que es instantáneo, gratuito y sigue funcionando sin
 *    conexión. Detecta los insultos evidentes.
 * 2. Gemini, que atrapa lo que el filtro local no ve: insultos disfrazados,
 *    acoso, amenazas o datos personales de terceros.
 *
 * Si Gemini no está disponible (sin clave, sin red, cuota agotada) se publica
 * lo que el filtro local haya dado por bueno. Es una decisión deliberada:
 * bloquear todos los reportes porque falló un servicio externo dejaría la app
 * inservible justo cuando más se usa.
 */
object ModerationService {

    private const val SYSTEM_INSTRUCTION = """
Eres el moderador de una app de transporte público de Trujillo, Perú, donde la
gente publica reportes sobre el tráfico y el estado de las rutas.

Rechaza un texto solo si contiene:
- insultos o groserías, incluidos los disfrazados con símbolos o letras cambiadas
- ataques, acoso o amenazas a personas
- contenido sexual, discriminación o incitación al odio
- datos personales de terceros (teléfonos, direcciones, placas con nombre)
- publicidad o estafas

Permite las quejas sobre el servicio, aunque estén escritas con enfado: decir
que un micro es lento, que el conductor maneja mal o que el servicio es pésimo
es una crítica legítima, no una ofensa.

Devuelve motivo en español, corto y en segunda persona, solo cuando rechaces.
"""

    private val responseSchema: JSONObject
        get() = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("aceptable", JSONObject().put("type", "boolean"))
                    .put(
                        "motivo",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Por qué se rechaza; vacío si es aceptable")
                    )
            )
            .put("required", JSONArray().put("aceptable"))

    /**
     * Instrucción de idioma: el motivo del rechazo debe llegar en el mismo
     * idioma en el que el usuario está usando la app.
     */
    private fun languageInstruction(): String =
        if (Locale.getDefault().language == "en") {
            "\nWrite the reason in English."
        } else {
            "\nEscribe el motivo en español."
        }

    /** Revisa [text] y decide si puede publicarse. */
    suspend fun review(text: String): ModerationVerdict {
        ProfanityFilter.firstMatch(text)?.let { word ->
            return ModerationVerdict.RejectedLocally(word)
        }

        // Con servidor, la segunda revisión la hace él: la clave de Gemini se
        // queda allí. Si no hay sesión o el servidor falla, vale lo que ya dijo
        // el filtro local.
        if (ApiClient.isConfigured) return reviewOnServer(text)

        if (!GeminiClient.isConfigured) return ModerationVerdict.Allowed

        val result = GeminiClient.generate(
            systemInstruction = SYSTEM_INSTRUCTION + languageInstruction(),
            history = listOf(GeminiClient.Turn("Texto a revisar:\n$text", fromUser = true)),
            responseSchema = responseSchema,
            maxOutputTokens = 512,
            temperature = 0.0
        )

        return when (result) {
            // Si el servicio falla, no bloqueamos: ya pasó el filtro local.
            is GeminiResult.Failure -> ModerationVerdict.Allowed
            is GeminiResult.Success -> parseVerdict(result.text)
        }
    }

    private suspend fun reviewOnServer(text: String): ModerationVerdict {
        if (!AccountRepository.isSignedIn) return ModerationVerdict.Allowed

        val body = JSONObject()
            .put("text", text)
            .put("language", Locale.getDefault().language)

        return when (val result = ApiClient.post("/api/moderation", body, AccountRepository.token)) {
            is ApiResult.Failure -> ModerationVerdict.Allowed
            is ApiResult.Success -> {
                val allowed = result.data.optBoolean("allowed", true)
                val reason = result.data.optString("reason").trim()
                if (allowed || reason.isEmpty()) {
                    ModerationVerdict.Allowed
                } else {
                    ModerationVerdict.RejectedByModel(reason)
                }
            }
        }
    }

    private fun parseVerdict(json: String): ModerationVerdict = try {
        val obj = JSONObject(json)
        if (obj.optBoolean("aceptable", true)) {
            ModerationVerdict.Allowed
        } else {
            val motivo = obj.optString("motivo").trim()
            // Sin motivo no podemos explicar nada al usuario: mejor dejarlo
            // pasar que rechazarlo sin decirle por qué.
            if (motivo.isBlank()) {
                ModerationVerdict.Allowed
            } else {
                ModerationVerdict.RejectedByModel(motivo)
            }
        }
    } catch (e: org.json.JSONException) {
        // Respuesta ilegible: no es motivo para bloquear al usuario.
        ModerationVerdict.Allowed
    }
}

package com.peru.expresdm.ai

import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Lo que el asistente responde: un texto y, si aplica, la ruta recomendada. */
data class AssistantAnswer(
    val text: String,
    val suggestedRoute: BusRoute?
)

/** Resultado de una consulta al asistente, con el motivo si algo falló. */
sealed interface AssistantResult {
    data class Success(val answer: AssistantAnswer) : AssistantResult
    data class Failure(val reason: GeminiResult.Reason) : AssistantResult

    /** Hay servidor, pero hace falta una cuenta para gastar su cuota de IA. */
    data object NeedsAccount : AssistantResult
}

/**
 * Asistente de rutas.
 *
 * Con servidor configurado, la pregunta va allí: la clave de Gemini se queda en
 * el servidor y no viaja dentro del APK. Sin servidor, la app habla con Gemini
 * directamente, como antes, usando la clave de local.properties.
 *
 * En ambos casos el id de ruta que devuelve el modelo se valida contra el
 * catálogo: si no existe, se ignora y nunca se muestra una ruta inventada.
 */
object AssistantRepository {

    private const val SYSTEM_INSTRUCTION = """
Eres el asistente de Subo, una app de transporte público de Trujillo, Perú.
Ayudas a la gente a saber qué micro o combi tomar para llegar a su destino.

Reglas:
- Responde SOLO con información del catálogo de rutas que se te entrega.
- Si ninguna ruta sirve para lo que piden, dilo con claridad y no inventes rutas.
- Cuando una ruta sirva, pon su id exacto en el campo rutaId.
- Sé breve y directo: dos o tres frases como mucho.
- Menciona el pasaje y la duración cuando recomiendes una ruta.
- Si la pregunta no tiene que ver con transporte en Trujillo, dilo amablemente
  y ofrece ayudar con rutas.
"""

    /**
     * Instrucción de idioma. La app puede estar en español o en inglés, y la
     * respuesta debe llegar en el idioma que el usuario está leyendo.
     */
    private fun languageInstruction(): String =
        if (Locale.getDefault().language == "en") {
            "\n- Reply in English, informal and natural.\n"
        } else {
            "\n- Habla en español peruano, de tú, con naturalidad.\n"
        }

    /** Esquema de la respuesta: obliga al modelo a devolver algo que sepamos leer. */
    private val responseSchema: JSONObject
        get() = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "respuesta",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Respuesta para el usuario, en español")
                    )
                    .put(
                        "rutaId",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Id exacto de la ruta recomendada, o cadena vacía")
                    )
            )
            .put("required", JSONArray().put("respuesta"))

    suspend fun ask(question: String, history: List<GeminiClient.Turn>): AssistantResult =
        if (ApiClient.isConfigured) askServer(question) else askGemini(question, history)

    /** Vía servidor: la clave de Gemini no sale de allí. */
    private suspend fun askServer(question: String): AssistantResult {
        if (!AccountRepository.isSignedIn) return AssistantResult.NeedsAccount

        val body = JSONObject()
            .put("question", question)
            .put("language", Locale.getDefault().language)

        return when (val result = ApiClient.post("/api/assistant", body, AccountRepository.token)) {
            is ApiResult.Success -> {
                val text = result.data.optString("answer").trim()
                val routeId = result.data.optString("routeId").trim()
                if (text.isEmpty()) {
                    AssistantResult.Failure(GeminiResult.Reason.MALFORMED)
                } else {
                    AssistantResult.Success(AssistantAnswer(text, RouteRepository.byId(routeId)))
                }
            }

            is ApiResult.Failure -> when (result.reason) {
                ApiFailure.UNAUTHORIZED -> AssistantResult.NeedsAccount
                ApiFailure.NETWORK, ApiFailure.NOT_CONFIGURED -> AssistantResult.Failure(GeminiResult.Reason.NETWORK)
                ApiFailure.REJECTED -> AssistantResult.Failure(GeminiResult.Reason.MALFORMED)
                ApiFailure.SERVER -> AssistantResult.Failure(GeminiResult.Reason.SERVER)
            }
        }
    }

    /** Sin servidor: la app llama a Gemini con la clave del APK. */
    private suspend fun askGemini(question: String, history: List<GeminiClient.Turn>): AssistantResult {
        val turns = buildList {
            addAll(history)
            add(GeminiClient.Turn(question, fromUser = true))
        }

        val result = GeminiClient.generate(
            systemInstruction = SYSTEM_INSTRUCTION + languageInstruction() +
                "\nCatálogo de rutas:\n" + catalogAsJson(),
            history = turns,
            responseSchema = responseSchema
        )

        return when (result) {
            is GeminiResult.Failure -> AssistantResult.Failure(result.reason)
            is GeminiResult.Success -> parseAnswer(result.text)
                ?.let { AssistantResult.Success(it) }
                ?: AssistantResult.Failure(GeminiResult.Reason.MALFORMED)
        }
    }

    /** Traduce un fallo a un mensaje que el usuario pueda entender. */
    fun describeFailure(reason: GeminiResult.Reason): String = when (reason) {
        GeminiResult.Reason.NO_API_KEY ->
            "El asistente no está configurado: falta la clave de Gemini."
        GeminiResult.Reason.NETWORK ->
            "No pude conectarme. Revisa tu conexión a internet e inténtalo de nuevo."
        GeminiResult.Reason.AUTH_OR_QUOTA ->
            "El servicio rechazó la consulta. Puede ser la clave o que se agotó la cuota del día."
        GeminiResult.Reason.SERVER ->
            "El asistente está saturado ahora mismo. Prueba en unos segundos."
        GeminiResult.Reason.BLOCKED ->
            "No puedo responder a eso. Pregúntame por rutas y destinos de Trujillo."
        GeminiResult.Reason.MALFORMED ->
            "Recibí una respuesta que no supe leer. Vuelve a intentarlo."
    }

    private fun parseAnswer(json: String): AssistantAnswer? = try {
        val obj = JSONObject(json)
        val text = obj.optString("respuesta").trim()
        val routeId = obj.optString("rutaId").trim()

        if (text.isEmpty()) {
            null
        } else {
            // Solo aceptamos ids que existan de verdad en el catálogo.
            AssistantAnswer(text, RouteRepository.byId(routeId))
        }
    } catch (e: org.json.JSONException) {
        null
    }

    /** El catálogo en JSON compacto, que es lo que mejor lee el modelo. */
    private fun catalogAsJson(): String {
        val array = JSONArray()
        RouteRepository.all().forEach { route ->
            array.put(
                JSONObject()
                    .put("id", route.id)
                    .put("nombre", route.name)
                    .put("origen", route.origin)
                    .put("destino", route.destination)
                    .put("duracionMinutos", route.durationMinutes)
                    .put("pasajeSoles", route.fare)
                    .put("horario", route.schedule)
                    .put("paradas", JSONArray(route.stops.map { it.name }))
            )
        }
        return array.toString()
    }
}

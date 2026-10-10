package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.ai.Gemini
import com.peru.expresdm.backend.ai.Prompts
import com.peru.expresdm.backend.model.AssistantRequest
import com.peru.expresdm.backend.model.AssistantResponse
import com.peru.expresdm.backend.model.ModerationRequest
import com.peru.expresdm.backend.model.ModerationResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Asistente y moderación. La clave de Gemini se queda aquí; la app solo manda
 * el texto y recibe la respuesta.
 */
fun Route.aiApi(services: Services) {
    authenticate(AUTH_JWT) {
        post("/assistant") {
            requireUser(services)
            val request = call.receive<AssistantRequest>()
            if (request.question.isBlank()) throw BadRequest("Escribe una pregunta")
            if (!services.config.isAssistantEnabled) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    com.peru.expresdm.backend.model.ErrorResponse(
                        "asistente_desactivado",
                        "El asistente no está configurado en el servidor"
                    )
                )
            }

            val routes = services.routes.all(viewerId = null)
            val answer = services.gemini.generateJson(
                systemInstruction = Prompts.assistantSystem(routes, request.language),
                userText = request.question.take(500),
                responseSchema = Gemini.ASSISTANT_SCHEMA
            )

            if (answer == null) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    com.peru.expresdm.backend.model.ErrorResponse(
                        "asistente_no_disponible",
                        "El asistente no está disponible ahora mismo"
                    )
                )
            }

            val text = answer["respuesta"]?.jsonPrimitive?.content.orEmpty()
            // El id que devuelve el modelo se comprueba contra el catálogo: así
            // la app nunca recibe una ruta inventada.
            val routeId = answer["rutaId"]?.jsonPrimitive?.content
                ?.takeIf { id -> routes.any { it.id == id } }
            call.respond(AssistantResponse(text, routeId))
        }

        post("/moderation") {
            requireUser(services)
            val request = call.receive<ModerationRequest>()
            if (request.text.isBlank()) return@post call.respond(ModerationResponse(allowed = true))

            // Sin Gemini configurado no se bloquea nada: la app tiene su propio
            // filtro de groserías, que funciona sin conexión.
            if (!services.config.isAssistantEnabled) return@post call.respond(ModerationResponse(allowed = true))

            val verdict = services.gemini.generateJson(
                systemInstruction = Prompts.moderationSystem(request.language),
                userText = "Texto a revisar:\n${request.text.take(500)}",
                responseSchema = Gemini.MODERATION_SCHEMA,
                temperature = 0.0
            ) ?: return@post call.respond(ModerationResponse(allowed = true))

            val allowed = verdict["aceptable"]?.jsonPrimitive?.booleanOrNull ?: true
            val reason = verdict["motivo"]?.jsonPrimitive?.content?.trim().orEmpty()
            // Un rechazo sin motivo no se le puede explicar a nadie: se deja pasar.
            if (!allowed && reason.isNotEmpty()) {
                call.respond(ModerationResponse(allowed = false, reason = reason))
            } else {
                call.respond(ModerationResponse(allowed = true))
            }
        }
    }
}

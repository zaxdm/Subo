package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.model.ErrorResponse
import com.peru.expresdm.backend.model.FeedbackRequest
import com.peru.expresdm.backend.model.FeedbackResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Buzón de sugerencias. Escribir pide sesión, para que nadie lo llene de basura
 * sin dar la cara. Leerlas se hace desde el panel, que habla con el servidor de
 * Supabase.
 */
fun Route.feedbackApi(services: Services) {
    authenticate(AUTH_JWT) {
        post("/feedback") {
            val user = requireUser(services)
            val request = call.receive<FeedbackRequest>()
            val subject = request.subject.trim().take(60)
            val message = request.message.trim()
            if (subject.isEmpty()) throw BadRequest("Elige un asunto")
            if (message.isEmpty()) throw BadRequest("Escribe tu mensaje")
            if (message.length > 1000) throw BadRequest("El mensaje admite hasta 1000 caracteres")

            val id = services.feedback.add(
                userId = user.id,
                subject = subject,
                message = message,
                appVersion = request.appVersion.trim().take(40),
                device = request.device.trim().take(120)
            ) ?: return@post call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse("demasiadas_sugerencias", "Ya enviaste varias sugerencias hoy. Inténtalo mañana")
            )
            call.respond(HttpStatusCode.Created, FeedbackResponse(id))
        }
    }
}

package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.Config
import com.peru.expresdm.backend.ai.Gemini
import com.peru.expresdm.backend.auth.Tokens
import com.peru.expresdm.backend.db.Database
import com.peru.expresdm.backend.model.ErrorResponse
import com.peru.expresdm.backend.store.AlertStore
import com.peru.expresdm.backend.store.FeedbackStore
import com.peru.expresdm.backend.store.RouteStore
import com.peru.expresdm.backend.store.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/** Todo lo que el servidor necesita para atender peticiones. */
class Services(
    val config: Config,
    val database: Database,
    val users: UserStore,
    val alerts: AlertStore,
    val routes: RouteStore,
    val feedback: FeedbackStore,
    val tokens: Tokens,
    val gemini: Gemini
)

const val AUTH_JWT = "auth-jwt"

/**
 * Monta el servidor: formato JSON, sesiones, manejo de errores y las rutas de
 * la API. Se separa de [com.peru.expresdm.backend.main] para que las pruebas
 * puedan levantarlo sin abrir un puerto.
 */
fun Application.module(services: Services) {
    val log = LoggerFactory.getLogger("api")

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = "Subo"
            verifier(services.tokens.verifier)
            validate { credential ->
                val id = credential.payload.subject
                if (id != null && services.users.findById(id) != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("no_autorizado", "Inicia sesión para continuar"))
            }
        }
    }

    install(StatusPages) {
        exception<BadRequest> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("peticion_invalida", cause.message ?: "Petición inválida"))
        }
        exception<Throwable> { call, cause ->
            // El detalle va al registro del servidor, no al usuario.
            log.error("Fallo atendiendo ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("error_servidor", "Algo falló en el servidor"))
        }
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {
            authApi(services)
            alertsApi(services)
            routesApi(services)
            feedbackApi(services)
            aiApi(services)
        }
    }
}

/** Petición mal formada: el mensaje sí se le muestra al usuario. */
class BadRequest(message: String) : RuntimeException(message)

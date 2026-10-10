package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.auth.DisplayNameProblem
import com.peru.expresdm.backend.auth.DisplayNames
import com.peru.expresdm.backend.auth.Passwords
import com.peru.expresdm.backend.model.ErrorResponse
import com.peru.expresdm.backend.model.LoginRequest
import com.peru.expresdm.backend.model.RegisterRequest
import com.peru.expresdm.backend.model.SessionResponse
import com.peru.expresdm.backend.model.UpdateProfileRequest
import com.peru.expresdm.backend.model.UserDto
import com.peru.expresdm.backend.store.StoredUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Registro, inicio de sesión y "quién soy". */
fun Route.authApi(services: Services) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val email = request.email.trim()
            if (!email.contains("@") || email.length < 5) throw BadRequest("Escribe un correo válido")
            DisplayNames.problem(request.displayName)?.let { throw BadRequest(nameProblem(it)) }
            if (Passwords.problem(request.password) != null) {
                throw BadRequest("La contraseña necesita al menos ${Passwords.MIN_LENGTH} caracteres")
            }

            val user = services.users.create(email, Passwords.hash(request.password), DisplayNames.clean(request.displayName))
                ?: return@post call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("correo_en_uso", "Ese correo ya tiene una cuenta")
                )

            call.respond(HttpStatusCode.Created, user.toSession(services))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = services.users.findByEmail(request.email)
            // El mismo mensaje en los dos casos: así no se puede averiguar qué
            // correos están registrados.
            if (user == null || !Passwords.matches(request.password, user.passwordHash)) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("credenciales_invalidas", "Correo o contraseña incorrectos")
                )
            }
            call.respond(user.toSession(services))
        }
    }

    authenticate(AUTH_JWT) {
        get("/me") {
            val user = requireUser(services)
            call.respond(UserDto(user.id, user.email, user.displayName))
        }

        // Cambiar el nombre visible. El token lleva el id, no el nombre, así
        // que la sesión abierta sigue valiendo después del cambio.
        post("/me") {
            val user = requireUser(services)
            val request = call.receive<UpdateProfileRequest>()
            DisplayNames.problem(request.displayName)?.let { throw BadRequest(nameProblem(it)) }

            val updated = services.users.updateDisplayName(user.id, DisplayNames.clean(request.displayName))
                ?: throw BadRequest("Sesión no válida")
            call.respond(UserDto(updated.id, updated.email, updated.displayName))
        }
    }
}

private fun nameProblem(problem: DisplayNameProblem): String = when (problem) {
    DisplayNameProblem.EMPTY -> "Escribe tu nombre"
    DisplayNameProblem.TOO_LONG -> "El nombre no puede pasar de ${DisplayNames.MAX_LENGTH} caracteres"
}

private fun StoredUser.toSession(services: Services) = SessionResponse(
    token = services.tokens.issue(id, displayName),
    user = UserDto(id, email, displayName)
)

/** El usuario de la sesión actual; solo se usa dentro de rutas autenticadas. */
fun RoutingContext.requireUser(services: Services): StoredUser {
    val id = call.principal<JWTPrincipal>()?.payload?.subject
    return id?.let { services.users.findById(it) } ?: throw BadRequest("Sesión no válida")
}

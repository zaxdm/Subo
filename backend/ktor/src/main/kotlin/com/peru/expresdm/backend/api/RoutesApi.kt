package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.geo.Geo
import com.peru.expresdm.backend.model.ErrorResponse
import com.peru.expresdm.backend.model.RoutesResponse
import com.peru.expresdm.backend.model.SubmitRouteRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Catálogo de rutas. Leerlo es público; grabar una ruta nueva pide sesión, y
 * solo su autor (o un administrador) puede borrarla.
 */
fun Route.routesApi(services: Services) {
    route("/routes") {
        // Leer el catálogo no pide cuenta; con sesión se marcan las rutas propias.
        authenticate(AUTH_JWT, optional = true) {
            get {
                val viewerId = call.principal<JWTPrincipal>()?.payload?.subject
                call.respond(RoutesResponse(services.routes.all(viewerId)))
            }

            get("/{id}") {
                val id = call.parameters["id"] ?: throw BadRequest("Falta el id de la ruta")
                val viewerId = call.principal<JWTPrincipal>()?.payload?.subject
                val route = services.routes.byId(id, viewerId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("no_encontrada", "Esa ruta no existe")
                    )
                call.respond(route)
            }
        }

        authenticate(AUTH_JWT) {
            post {
                val user = requireUser(services)
                val request = call.receive<SubmitRouteRequest>()
                validate(request)
                call.respond(HttpStatusCode.Created, services.routes.submit(user.id, request))
            }

            delete("/{id}") {
                val user = requireUser(services)
                val id = call.parameters["id"] ?: throw BadRequest("Falta el id de la ruta")
                val deleted = services.routes.delete(id, user.id, user.isAdmin(services))
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("no_permitido", "Esa ruta no es tuya"))
                }
            }
        }
    }
}

private fun validate(request: SubmitRouteRequest) {
    if (request.name.isBlank()) throw BadRequest("La ruta necesita un nombre")
    if (request.origin.isBlank() || request.destination.isBlank()) throw BadRequest("Falta el origen o el destino")
    if (request.stops.size < 2) throw BadRequest("Una ruta necesita al menos 2 paradas")
    if (request.fare <= 0 || request.fare > 50) throw BadRequest("El pasaje no parece válido")
    if (request.durationMinutes <= 0) throw BadRequest("La duración no parece válida")
    request.stops.forEach {
        if (!Geo.isValidCoordinate(it.latitude, it.longitude)) throw BadRequest("Una parada está fuera del mapa")
    }
    request.path?.forEach {
        if (!Geo.isValidCoordinate(it.latitude, it.longitude)) throw BadRequest("El recorrido sale del mapa")
    }
}

private fun com.peru.expresdm.backend.store.StoredUser.isAdmin(services: Services): Boolean =
    services.database.query("SELECT is_admin FROM users WHERE id = ?", id) { rs ->
        rs.next() && rs.getBoolean("is_admin")
    }

package com.peru.expresdm.backend.api

import com.peru.expresdm.backend.alerts.AlertPolicy
import com.peru.expresdm.backend.geo.Geo
import com.peru.expresdm.backend.model.AlertsResponse
import com.peru.expresdm.backend.model.ErrorResponse
import com.peru.expresdm.backend.model.PublishAlertRequest
import com.peru.expresdm.backend.model.VoteRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Alertas compartidas: leerlas es público (así la app las muestra sin obligar a
 * registrarse), pero publicar y votar pide sesión.
 */
fun Route.alertsApi(services: Services) {
    route("/alerts") {
        // "optional": se puede leer sin cuenta, pero si viene sesión se aprovecha
        // para marcar las alertas propias y el voto de quien pregunta.
        authenticate(AUTH_JWT, optional = true) {
            get {
            val latitude = call.parameters["lat"]?.toDoubleOrNull() ?: throw BadRequest("Falta lat")
            val longitude = call.parameters["lon"]?.toDoubleOrNull() ?: throw BadRequest("Falta lon")
            if (!Geo.isValidCoordinate(latitude, longitude)) throw BadRequest("Coordenadas fuera de rango")
            val radius = (call.parameters["radius"]?.toDoubleOrNull() ?: AlertPolicy.DEFAULT_RADIUS_METERS)
                .coerceIn(1.0, AlertPolicy.MAX_RADIUS_METERS)

            // Si viene sesión se marcan las alertas propias y el voto de cada una.
            val viewerId = call.principal<JWTPrincipal>()?.payload?.subject
            call.respond(AlertsResponse(services.alerts.near(latitude, longitude, radius, viewerId)))
            }
        }

        authenticate(AUTH_JWT) {
            post {
                val user = requireUser(services)
                val request = call.receive<PublishAlertRequest>()
                if (!AlertPolicy.isKnownType(request.type)) throw BadRequest("Tipo de alerta desconocido")
                if (!Geo.isValidCoordinate(request.latitude, request.longitude)) {
                    throw BadRequest("Coordenadas fuera de rango")
                }

                val alert = services.alerts.publish(
                    authorId = user.id,
                    type = request.type,
                    latitude = request.latitude,
                    longitude = request.longitude,
                    placeName = request.placeName?.trim()?.take(120),
                    comment = request.comment
                )
                call.respond(HttpStatusCode.Created, alert)
            }

            post("/{id}/vote") {
                val user = requireUser(services)
                val id = call.parameters["id"] ?: throw BadRequest("Falta el id de la alerta")
                val vote = call.receive<VoteRequest>().vote
                if (vote !in AlertPolicy.VOTES) throw BadRequest("Voto desconocido")

                val updated = services.alerts.vote(id, user.id, vote)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("no_encontrada", "Esa alerta ya no existe")
                    )
                call.respond(updated)
            }
        }
    }
}

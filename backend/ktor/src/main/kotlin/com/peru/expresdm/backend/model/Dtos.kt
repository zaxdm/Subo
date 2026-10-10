package com.peru.expresdm.backend.model

import kotlinx.serialization.Serializable

// Lo que viaja entre la app y el servidor. Los nombres coinciden con los de la
// app para que la traducción a sus modelos sea directa.

// --- Cuentas ---

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SessionResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String)

@Serializable
data class UpdateProfileRequest(val displayName: String)

// --- Alertas ---

@Serializable
data class AlertDto(
    val id: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val comment: String? = null,
    val createdAt: Long,
    val expiresAt: Long,
    val confirmations: Int,
    val dismissals: Int,
    val lastConfirmedAt: Long,
    /** Voto de quien pregunta: STILL_THERE, GONE o nada. */
    val myVote: String? = null,
    val createdByMe: Boolean = false
)

@Serializable
data class PublishAlertRequest(
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val comment: String? = null
)

@Serializable
data class VoteRequest(val vote: String)

@Serializable
data class AlertsResponse(val alerts: List<AlertDto>)

// --- Rutas ---

@Serializable
data class StopDto(val name: String, val latitude: Double, val longitude: Double)

@Serializable
data class CoordinateDto(val latitude: Double, val longitude: Double)

@Serializable
data class RouteDto(
    val id: String,
    val letter: String,
    val name: String,
    val origin: String,
    val destination: String,
    val durationMinutes: Int,
    val fare: Double,
    val schedule: String,
    val stops: List<StopDto>,
    val accentIndex: Int,
    val path: List<CoordinateDto>? = null,
    val pathSnapped: Boolean = false,
    val createdByUser: Boolean = false,
    /** PENDING, APPROVED o REJECTED: las grabadas se revisan antes de compartirse. */
    val status: String = "APPROVED",
    val createdByMe: Boolean = false
)

@Serializable
data class SubmitRouteRequest(
    /**
     * Id que la app ya le dio a la ruta en el teléfono. Se respeta si está
     * libre, para que la misma ruta no acabe con dos ids distintos.
     */
    val id: String? = null,
    val letter: String,
    val name: String,
    val origin: String,
    val destination: String,
    val durationMinutes: Int,
    val fare: Double,
    val schedule: String,
    val stops: List<StopDto>,
    val path: List<CoordinateDto>? = null,
    val pathSnapped: Boolean = false
)

@Serializable
data class RoutesResponse(val routes: List<RouteDto>)

// --- Asistente y moderación ---

@Serializable
data class AssistantRequest(val question: String, val language: String = "es")

@Serializable
data class AssistantResponse(val answer: String, val routeId: String? = null)

@Serializable
data class ModerationRequest(val text: String, val language: String = "es")

@Serializable
data class ModerationResponse(val allowed: Boolean, val reason: String? = null)

// --- Sugerencias ---

@Serializable
data class FeedbackRequest(
    val subject: String,
    val message: String,
    val appVersion: String = "",
    val device: String = ""
)

@Serializable
data class FeedbackResponse(val id: String)

// --- Errores ---

@Serializable
data class ErrorResponse(val error: String, val message: String)

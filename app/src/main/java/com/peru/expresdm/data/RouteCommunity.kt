package com.peru.expresdm.data

import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Una opinión escrita sobre una ruta. */
data class RouteReview(
    val stars: Int,
    val comment: String,
    val userName: String,
    val createdAt: Long
)

/** Lo que la gente dice de una ruta: su nota y los reportes de la última semana. */
data class RouteCommunity(
    /** Nota media de 1 a 5; `null` si nadie la ha calificado. */
    val ratingAverage: Double?,
    val ratingCount: Int,
    val myStars: Int?,
    val myComment: String?,
    val reviews: List<RouteReview>,
    /** Alertas sobre esta ruta en los últimos 7 días, por tipo. */
    val alertsThisWeek: Map<AlertType, Int>
)

/**
 * Calificaciones y reportes de cada ruta. Viven en el servidor: sin él, la
 * ficha del micro simplemente no enseña esta parte.
 */
object RouteCommunityRepository {

    const val MAX_COMMENT_LENGTH = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun load(routeId: String): ApiResult<RouteCommunity> =
        when (val result = ApiClient.get("/api/routes/$routeId/community", AccountRepository.token)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(parse(result.data, alerts = result.data.optJSONObject("alertsThisWeek")))
        }

    /** Guarda la calificación del usuario, o cambia la que ya tenía. */
    suspend fun rate(routeId: String, stars: Int, comment: String, previous: RouteCommunity?): ApiResult<RouteCommunity> {
        if (!AccountRepository.isSignedIn) return ApiResult.Failure(ApiFailure.UNAUTHORIZED)
        val body = JSONObject().put("stars", stars).put("comment", comment.trim().ifEmpty { JSONObject.NULL })
        return when (val result = ApiClient.post("/api/routes/$routeId/rating", body, AccountRepository.token)) {
            is ApiResult.Failure -> result
            // La respuesta trae las notas ya recalculadas, pero no los reportes: se conservan.
            is ApiResult.Success -> ApiResult.Success(
                parse(result.data, alerts = null).copy(alertsThisWeek = previous?.alertsThisWeek.orEmpty())
            )
        }
    }

    /** Avisa de que se abrió la ficha, para las estadísticas. No importa si falla. */
    fun recordView(routeId: String) {
        if (!ApiClient.isConfigured) return
        scope.launch { ApiClient.post("/api/routes/$routeId/view", JSONObject()) }
    }

    private fun parse(json: JSONObject, alerts: JSONObject?): RouteCommunity {
        val mine = json.optJSONObject("myRating")
        val reviewsArray = json.optJSONArray("reviews")
        val reviews = (0 until (reviewsArray?.length() ?: 0)).mapNotNull { index ->
            val item = reviewsArray!!.optJSONObject(index) ?: return@mapNotNull null
            val comment = item.optString("comment").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            RouteReview(item.optInt("stars"), comment, item.optString("userName"), item.optLong("createdAt"))
        }
        val counts = buildMap {
            alerts?.keys()?.forEach { key ->
                AlertType.entries.firstOrNull { it.name == key }?.let { put(it, alerts.optInt(key)) }
            }
        }
        return RouteCommunity(
            ratingAverage = json.optDouble("ratingAverage").takeUnless { it.isNaN() },
            ratingCount = json.optInt("ratingCount"),
            myStars = mine?.optInt("stars")?.takeIf { it in 1..5 },
            myComment = mine?.optString("comment")?.takeIf { it.isNotBlank() && it != "null" },
            reviews = reviews,
            alertsThisWeek = counts
        )
    }
}

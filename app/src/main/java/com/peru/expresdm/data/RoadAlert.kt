package com.peru.expresdm.data

/**
 * Tipos de alerta, cada uno con cuánto tiempo sigue teniendo sentido.
 *
 * Un micro lleno deja de importar en una hora; una obra puede durar días.
 */
enum class AlertType(val lifetimeMinutes: Long) {
    TRAFFIC(120),
    ACCIDENT(180),

    /** El cobrador pide más del pasaje: vale mientras ese micro sigue en ruta. */
    OVERCHARGE(120),
    ROADWORK(24 * 60),
    CROWDED(60),
    DANGER(360),
    OTHER(120)
}

enum class AlertVote { STILL_THERE, GONE }

/** Aviso que un usuario deja en el mapa sobre algo que pasa en la calle. */
data class RoadAlert(
    val id: String,
    val type: AlertType,
    val latitude: Double,
    val longitude: Double,
    /** Calle o lugar, resuelto a partir de la posición; puede faltar sin conexión. */
    val placeName: String?,
    val comment: String?,
    val createdAt: Long,
    val confirmations: Int = 0,
    val dismissals: Int = 0,
    val lastConfirmedAt: Long = createdAt,
    val myVote: AlertVote? = null,
    val createdByMe: Boolean = false
)

/**
 * Cuándo una alerta sigue vigente y cómo cuentan los votos.
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object AlertRules {

    /**
     * Votos de "ya no" por encima de los de "sigue ahí" a partir de los cuales
     * la alerta se oculta. Hoy solo cuenta el del propio teléfono.
     */
    const val DISMISSALS_TO_HIDE = 2

    private const val MINUTE_MS = 60_000L

    /**
     * Momento en que caduca: la vida del tipo desde que se creó, alargada si
     * alguien confirma que sigue ahí.
     */
    fun expiresAt(alert: RoadAlert): Long {
        val lifetime = alert.type.lifetimeMinutes * MINUTE_MS
        return maxOf(alert.createdAt + lifetime, alert.lastConfirmedAt + lifetime / 2)
    }

    fun isActive(alert: RoadAlert, now: Long): Boolean =
        now < expiresAt(alert) &&
            // Quien dijo "ya no" deja de verla en su teléfono.
            alert.myVote != AlertVote.GONE &&
            alert.dismissals - alert.confirmations < DISMISSALS_TO_HIDE

    /** Alertas vigentes, de la más reciente a la más antigua. */
    fun active(alerts: List<RoadAlert>, now: Long): List<RoadAlert> =
        alerts.filter { isActive(it, now) }.sortedByDescending { it.createdAt }

    /** Aplica un voto del usuario, cambiando el anterior si ya había votado. */
    fun vote(alert: RoadAlert, vote: AlertVote, now: Long): RoadAlert {
        if (alert.myVote == vote) return alert

        var confirmations = alert.confirmations
        var dismissals = alert.dismissals
        when (alert.myVote) {
            AlertVote.STILL_THERE -> confirmations--
            AlertVote.GONE -> dismissals--
            null -> Unit
        }
        when (vote) {
            AlertVote.STILL_THERE -> confirmations++
            AlertVote.GONE -> dismissals++
        }

        return alert.copy(
            confirmations = confirmations.coerceAtLeast(0),
            dismissals = dismissals.coerceAtLeast(0),
            myVote = vote,
            lastConfirmedAt = if (vote == AlertVote.STILL_THERE) now else alert.lastConfirmedAt
        )
    }

    /** Minutos desde que se creó, para mostrar "hace 5 min". */
    fun minutesAgo(alert: RoadAlert, now: Long): Long =
        ((now - alert.createdAt) / MINUTE_MS).coerceAtLeast(0)
}

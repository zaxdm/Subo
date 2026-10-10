package com.peru.expresdm.backend.alerts

/**
 * Cuánto vive una alerta y cuándo deja de mostrarse.
 *
 * Son las mismas reglas que la app aplicaba sola cuando los datos vivían en el
 * teléfono. Ahora mandan aquí: si cada usuario decidiera por su cuenta, dos
 * personas verían cosas distintas del mismo sitio.
 */
object AlertPolicy {

    /** Tipos válidos y cuánto tiempo tiene sentido cada aviso, en minutos. */
    val LIFETIME_MINUTES = mapOf(
        "TRAFFIC" to 120L,
        "ACCIDENT" to 180L,
        "OVERCHARGE" to 120L,
        "ROADWORK" to 24L * 60,
        "CROWDED" to 60L,
        "DANGER" to 360L,
        "OTHER" to 120L
    )

    val VOTES = setOf("STILL_THERE", "GONE")

    /** Con esta diferencia de votos "ya no está" sobre "sigue ahí", se oculta. */
    const val DISMISSALS_TO_HIDE = 2

    const val MAX_COMMENT_LENGTH = 140

    /** Radio por defecto y máximo al pedir las alertas de una zona. */
    const val DEFAULT_RADIUS_METERS = 5_000.0
    const val MAX_RADIUS_METERS = 50_000.0

    private const val MINUTE_MS = 60_000L

    fun isKnownType(type: String) = type in LIFETIME_MINUTES

    /**
     * Momento en que caduca: la vida del tipo desde que se creó, alargada si
     * alguien confirmó que seguía ahí.
     */
    fun expiresAt(type: String, createdAt: Long, lastConfirmedAt: Long): Long {
        val lifetime = (LIFETIME_MINUTES[type] ?: LIFETIME_MINUTES.getValue("OTHER")) * MINUTE_MS
        return maxOf(createdAt + lifetime, lastConfirmedAt + lifetime / 2)
    }

    /** Una alerta con demasiados "ya no está" desaparece para todos. */
    fun isHidden(confirmations: Int, dismissals: Int) = dismissals - confirmations >= DISMISSALS_TO_HIDE

    fun cleanComment(comment: String?): String? =
        comment?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_COMMENT_LENGTH)
}

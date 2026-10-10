package com.peru.expresdm.data

/**
 * Cuándo avisar al usuario de que está llegando a la parada donde se baja.
 *
 * Lógica pura, sin Android, para poder probarla directamente.
 */
object ArrivalRules {

    /**
     * Distancia a la parada a la que se avisa. Un micro en ciudad recorre esto
     * en unos 20-30 segundos: tiempo de guardar el teléfono y pedir la parada.
     */
    const val ARRIVAL_RADIUS_METERS = 250.0

    /** Lecturas con más error que esto no bastan para decidir que ya llegó... */
    const val MAX_ACCURACY_METERS = 100f

    enum class Reading {
        /** Está llegando: toca avisar. */
        ARRIVED,

        /** Aún lejos. */
        APPROACHING,

        /** La lectura es demasiado imprecisa para saberlo. */
        IMPRECISE
    }

    /** [radiusMeters] es la anticipación que el usuario eligió en Ajustes. */
    fun evaluate(
        distanceMeters: Double,
        accuracyMeters: Float?,
        radiusMeters: Double = ARRIVAL_RADIUS_METERS
    ): Reading {
        val accuracy = accuracyMeters?.toDouble() ?: 0.0
        return when {
            // ...salvo que, incluso contando todo el error, esté dentro del radio.
            distanceMeters + accuracy <= radiusMeters -> Reading.ARRIVED
            accuracy > MAX_ACCURACY_METERS -> Reading.IMPRECISE
            distanceMeters <= radiusMeters -> Reading.ARRIVED
            else -> Reading.APPROACHING
        }
    }
}

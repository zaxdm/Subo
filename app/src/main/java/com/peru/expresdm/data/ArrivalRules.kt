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

    /** Unas diez cuadras antes: ahí salta el preaviso de Premium. */
    const val EARLY_WARNING_METERS = 1_000.0

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

    /**
     * Toca el preaviso: ya está a unas cuadras, pero todavía no llega.
     * [cameFromFar] dice si antes se le vio más lejos: a quien activa el aviso
     * estando ya así de cerca no hay nada que anticiparle.
     */
    fun isEarlyWarning(
        distanceMeters: Double,
        cameFromFar: Boolean,
        radiusMeters: Double = ARRIVAL_RADIUS_METERS
    ): Boolean = cameFromFar && distanceMeters <= EARLY_WARNING_METERS && distanceMeters > radiusMeters
}

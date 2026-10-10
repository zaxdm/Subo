package com.peru.expresdm.ui

import android.content.Context
import com.peru.expresdm.R
import kotlin.math.roundToInt

/** "350 m" o "1,5 km", con el separador decimal del idioma activo. */
fun Context.formatDistance(meters: Double): String =
    if (meters < 1_000) {
        // Redondeamos a decenas: "347 m" aparenta una precisión que el GPS no tiene.
        val rounded = ((meters / 10).roundToInt() * 10).coerceAtLeast(10)
        getString(R.string.distance_meters, rounded)
    } else {
        getString(R.string.distance_km, meters / 1_000)
    }

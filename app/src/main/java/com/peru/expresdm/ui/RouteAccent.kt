package com.peru.expresdm.ui

import androidx.annotation.ColorRes
import com.peru.expresdm.R

/**
 * Colores de las insignias de ruta. Se recorren en ciclo para que cada ruta
 * tenga un color estable sin guardarlo en el modelo.
 *
 * Son colores semánticos con variante nocturna: en modo oscuro se resuelven a
 * tonos medios, porque los muy oscuros se perderían contra el fondo.
 */
object RouteAccent {

    @ColorRes
    private val badgeColors = intArrayOf(
        R.color.route_accent_1,
        R.color.route_accent_2,
        R.color.route_accent_3,
        R.color.route_accent_4,
        R.color.route_accent_5,
        R.color.route_accent_6
    )

    @ColorRes
    fun colorFor(index: Int): Int =
        badgeColors[((index % badgeColors.size) + badgeColors.size) % badgeColors.size]
}

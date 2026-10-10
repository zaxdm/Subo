package com.peru.expresdm.ui

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Franja vertical del mapa que queda a la vista entre los controles de arriba y
 * la hoja deslizable de abajo, en píxeles.
 */
data class VisibleBand(val top: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val center: Int get() = (top + bottom) / 2
}

/**
 * Mueve el "centro" del mapa al centro de la franja visible. Así el punto del
 * usuario, las animaciones de cámara y los encuadres quedan a la vista y no
 * debajo de la hoja.
 */
fun MapView.centerOnBand(band: VisibleBand) {
    setMapCenterOffset(0, band.center - height / 2)
    invalidate()
}

/**
 * Encuadra [points] dentro de la franja visible.
 *
 * `zoomToBoundingBox` ajusta la caja a toda la altura del mapa, pero parte de
 * esa altura queda tapada. Estiramos la caja en vertical en esa misma
 * proporción para que los puntos quepan en lo que se ve.
 */
fun MapView.frameInBand(points: List<GeoPoint>, band: VisibleBand, borderPx: Int, animated: Boolean) {
    if (points.isEmpty() || height == 0) return

    centerOnBand(band)
    controller.stopAnimation(false)

    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }

    // Un solo punto (o varios en el mismo sitio) no forma una caja: nos acercamos a él.
    if (north - south < MIN_SPAN_DEGREES && east - west < MIN_SPAN_DEGREES) {
        val zoom = maxOf(zoomLevelDouble, SINGLE_POINT_ZOOM)
        if (animated) {
            controller.animateTo(points.first(), zoom, FRAME_ANIMATION_MS)
        } else {
            controller.setZoom(zoom)
            controller.setCenter(points.first())
        }
        return
    }

    val stretch = height.toDouble() / band.height
    val latPadding = (north - south) * (stretch - 1) / 2
    val lonPadding = (east - west) * HORIZONTAL_PADDING

    zoomToBoundingBox(
        BoundingBox(north + latPadding, east + lonPadding, south - latPadding, west - lonPadding),
        animated,
        borderPx
    )
}

private const val MIN_SPAN_DEGREES = 0.0001
private const val SINGLE_POINT_ZOOM = 16.5
private const val FRAME_ANIMATION_MS = 600L
private const val HORIZONTAL_PADDING = 0.12

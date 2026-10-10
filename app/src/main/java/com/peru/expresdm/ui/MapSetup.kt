package com.peru.expresdm.ui

import android.content.Context
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Centro de Trujillo: posición por defecto cuando aún no hay ubicación. */
val TRUJILLO_CENTER = GeoPoint(-8.1120, -79.0288)

/**
 * Ajustes comunes de todos los mapas de la app.
 *
 * Mapnik es la capa estándar de OpenStreetMap: gratuita y sin clave de API.
 */
fun MapView.applyDefaults(context: Context) {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    isTilesScaledToDpi = true
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    setUseDataConnection(true)
    minZoomLevel = 5.0
    // Las teselas llegan hasta el nivel 19; osmdroid amplía las del 19 para los
    // dos niveles siguientes. Con 19 como tope el mapa se quedaba corto al
    // querer ver una esquina o una parada de cerca.
    maxZoomLevel = 21.0
    // Evita que el usuario arrastre el mapa fuera del mundo y vea gris.
    setScrollableAreaLimitLatitude(
        MapView.getTileSystem().maxLatitude,
        MapView.getTileSystem().minLatitude,
        0
    )
}

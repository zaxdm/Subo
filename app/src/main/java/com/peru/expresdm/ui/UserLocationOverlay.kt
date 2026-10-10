package com.peru.expresdm.ui

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.hypot

/**
 * Punto del usuario en el mapa que además avisa de cada posición nueva y de
 * cuándo el usuario mueve el mapa con los dedos.
 *
 * Así el mapa y las listas comparten la misma fuente de ubicación, en vez de
 * encender el GPS dos veces.
 */
class UserLocationOverlay(
    provider: IMyLocationProvider,
    mapView: MapView,
    private val onFix: (Location) -> Unit,
    private val onUserGesture: () -> Unit = {}
) : MyLocationNewOverlay(provider, mapView) {

    private val mainThread = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(mapView.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var gestureReported = false

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        super.onLocationChanged(location, source)
        // El proveedor puede entregar la posición fuera del hilo principal.
        if (location != null) mainThread.post { onFix(location) }
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    gestureReported = false
                }
                // osmdroid solo deja de seguir al usuario al arrastrar con un dedo.
                // Con un pellizco para acercar seguía recentrando el mapa en cada
                // lectura del GPS, y era imposible acercarse a otro sitio.
                MotionEvent.ACTION_POINTER_DOWN -> reportGesture()
                MotionEvent.ACTION_MOVE ->
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) reportGesture()
            }
        }
        return super.onTouchEvent(event, mapView)
    }

    private fun reportGesture() {
        if (isFollowLocationEnabled) disableFollowLocation()
        if (!gestureReported) {
            gestureReported = true
            onUserGesture()
        }
    }

    override fun onDetach(mapView: MapView?) {
        mainThread.removeCallbacksAndMessages(null)
        super.onDetach(mapView)
    }
}

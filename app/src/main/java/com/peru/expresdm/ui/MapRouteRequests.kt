package com.peru.expresdm.ui

import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.peru.expresdm.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pedidos de "muestra esto en el mapa" que llegan desde otras pantallas.
 *
 * Nada abre una pantalla de detalle: rutas y alertas se ven en el mapa del
 * inicio. Quien quiera mostrar algo deja aquí el pedido y cambia a la pestaña
 * de inicio, que lo recoge al aparecer.
 */
open class MapRequests {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(id: String) {
        _pending.value = id
    }

    /** Devuelve el pedido pendiente y lo marca como atendido. */
    fun consume(): String? {
        val id = _pending.value
        _pending.value = null
        return id
    }
}

/** Rutas que hay que trazar en el mapa. */
object MapRouteRequests : MapRequests()

/** Alertas en las que hay que centrar el mapa. */
object MapAlertRequests : MapRequests()

/** Traza [routeId] en el mapa del inicio, desde cualquier pestaña. */
fun Fragment.showRouteOnMap(routeId: String) {
    MapRouteRequests.request(routeId)
    goToMapTab()
}

/** Centra el mapa del inicio en una alerta, desde cualquier pestaña. */
fun Fragment.showAlertOnMap(alertId: String) {
    MapAlertRequests.request(alertId)
    goToMapTab()
}

private fun Fragment.goToMapTab() {
    requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.routesFragment
}

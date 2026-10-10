package com.peru.expresdm.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peru.expresdm.data.AlertRepository
import com.peru.expresdm.data.AlertRules
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.AlertVote
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.RoadAlert
import com.peru.expresdm.data.UserPosition
import com.peru.expresdm.ui.currentLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una alerta tal como se lista: con lo lejos que queda y cuánto hace que se creó. */
data class AlertItem(
    val alert: RoadAlert,
    val distanceMeters: Double?,
    val minutesAgo: Long
)

data class ReportsState(
    val items: List<AlertItem> = emptyList(),
    /** Tipo elegido en los filtros; `null` muestra todas. */
    val filter: AlertType? = null,
    /** Alertas vigentes de cada tipo, para los contadores de los filtros. */
    val counts: Map<AlertType, Int> = emptyMap(),
    val totalActive: Int = 0
)

/** Alertas vigentes, agrupadas por tipo, de la más reciente a la más antigua. */
class ReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val filter = MutableStateFlow<AlertType?>(null)
    private val position = MutableStateFlow<UserPosition?>(null)
    private var locationJob: Job? = null

    /** Cada medio minuto: "hace 5 min" avanza y las caducadas desaparecen solas. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(REFRESH_MS)
        }
    }

    val state: StateFlow<ReportsState> =
        combine(
            AlertRepository.alerts, filter, position, clock, AppSettings.get(app).alertTypes
        ) { alerts, type, here, now, wanted ->
            // Las propias se ven siempre: uno quiere saber que lo que publicó sigue ahí.
            val active = AlertRules.active(alerts, now).filter { it.type in wanted || it.createdByMe }
            ReportsState(
                items = active
                    .filter { type == null || it.type == type }
                    .map { alert ->
                        AlertItem(
                            alert = alert,
                            distanceMeters = here?.let {
                                NearbyRoutes.distanceMeters(it.latitude, it.longitude, alert.latitude, alert.longitude)
                            },
                            minutesAgo = AlertRules.minutesAgo(alert, now)
                        )
                    },
                filter = type,
                counts = active.groupingBy { it.type }.eachCount(),
                totalActive = active.size
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsState()
        )

    fun setFilter(type: AlertType?) {
        filter.value = type
    }

    fun vote(alertId: String, vote: AlertVote) = AlertRepository.vote(alertId, vote)

    /** Pide la posición una vez, para mostrar a qué distancia queda cada alerta. */
    fun refreshLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            getApplication<Application>().currentLocation()?.let {
                position.value = UserPosition(it.latitude, it.longitude)
                // Con la posición ya se pueden pedir al servidor las alertas de la zona.
                AlertRepository.refresh(it.latitude, it.longitude)
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 30_000L
    }
}

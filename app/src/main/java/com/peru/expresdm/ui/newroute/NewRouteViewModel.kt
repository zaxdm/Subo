package com.peru.expresdm.ui.newroute

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peru.expresdm.R
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.PathCleaner
import com.peru.expresdm.data.PlaceSearch
import com.peru.expresdm.data.RecordingDraft
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.RouteSnapper
import com.peru.expresdm.data.Stop
import com.peru.expresdm.data.StopDetector
import com.peru.expresdm.data.StreetRouter
import com.peru.expresdm.tracking.RecorderStatus
import com.peru.expresdm.tracking.RouteRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Parada detectada en la grabación, con el nombre que el usuario puede corregir. */
data class EditableStop(
    /** Identificador estable mientras se edita, aunque se quiten otras paradas. */
    val key: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    /** Todavía se está buscando el nombre de la calle. */
    val resolving: Boolean
)

/** El recorrido que se muestra en el formulario y se guardará. */
data class PathPreview(
    val points: List<GeoCoordinate>,
    /** Todavía se está ajustando a las calles. */
    val snapping: Boolean,
    /** Ya sigue las calles; si no, es el rastro del GPS limpio. */
    val snapped: Boolean
)

enum class NewRouteField { NAME, ORIGIN, DESTINATION, FARE, STOPS }

data class NewRouteForm(
    val name: String,
    val origin: String,
    val destination: String,
    val fare: String,
    val schedule: String
)

sealed interface SaveResult {
    data class Saved(val routeId: String) : SaveResult
    data class Invalid(val fields: Set<NewRouteField>) : SaveResult

    /** La grabación ya no existe (se descartó desde otro sitio). */
    data object NoDraft : SaveResult
}

/**
 * Formulario de una ruta recién grabada: sus datos y las paradas detectadas.
 *
 * Las paradas salen de los sitios donde el micro se quedó quieto; su nombre se
 * busca por la calle y el usuario lo puede corregir o quitar las que sobren.
 */
class NewRouteViewModel(app: Application) : AndroidViewModel(app) {

    /** La grabación terminada; `null` si no hay ninguna pendiente de guardar. */
    val draft: RecordingDraft? = RouteRecorderService.state.value
        .takeIf { it.status == RecorderStatus.FINISHED }
        ?.draft

    private var nextKey = 0L

    private val router = StreetRouter.get(app)

    /** Se muestra al instante el recorrido limpio; al llegar, el ajustado a las calles. */
    private val _path = MutableStateFlow(
        PathPreview(
            points = draft?.let { PathCleaner.clean(it.points) }.orEmpty(),
            snapping = draft != null,
            snapped = false
        )
    )
    val path: StateFlow<PathPreview> = _path.asStateFlow()

    private val _stops = MutableStateFlow(
        draft?.let { StopDetector.detect(it.points) }.orEmpty().map {
            EditableStop(key = nextKey++, latitude = it.latitude, longitude = it.longitude, name = "", resolving = true)
        }
    )
    val stops: StateFlow<List<EditableStop>> = _stops.asStateFlow()

    init {
        resolveStopNames()
        snapToStreets()
    }

    /**
     * El ajuste corre en [RouteSnapper], fuera de esta pantalla: si el usuario
     * guarda antes de que termine, sigue y se aplica a la ruta guardada.
     */
    private val snapping = draft?.let {
        RouteSnapper.snap(router, "grabacion-${it.startedAt}", PathCleaner.filterTrace(it.points))
    }

    private fun snapToStreets() {
        val snapping = snapping ?: return
        viewModelScope.launch {
            val matched = snapping.await()
            _path.update { current ->
                if (matched != null && matched.size >= 2) {
                    PathPreview(matched, snapping = false, snapped = true)
                } else {
                    current.copy(snapping = false)
                }
            }
        }
    }

    /** Busca la calle de cada parada, una a una: el buscador admite una consulta por segundo. */
    private fun resolveStopNames() {
        viewModelScope.launch {
            for (stop in _stops.value) {
                val street = PlaceSearch.streetName(stop.latitude, stop.longitude)
                _stops.update { list ->
                    list.map {
                        if (it.key != stop.key) it
                        // Si el usuario ya escribió un nombre, se respeta el suyo.
                        else it.copy(name = it.name.ifBlank { street.orEmpty() }, resolving = false)
                    }
                }
            }
        }
    }

    fun renameStop(key: Long, name: String) {
        _stops.update { list -> list.map { if (it.key == key) it.copy(name = name, resolving = false) else it } }
    }

    /** Quita una parada; devuelve `false` si con ello quedarían menos de las necesarias. */
    fun removeStop(key: Long): Boolean {
        if (_stops.value.size <= MIN_STOPS) return false
        _stops.update { list -> list.filterNot { it.key == key } }
        return true
    }

    fun save(form: NewRouteForm): SaveResult {
        val draft = draft ?: return SaveResult.NoDraft
        val app = getApplication<Application>()

        val name = form.name.trim()
        val origin = form.origin.trim()
        val destination = form.destination.trim()
        val fare = form.fare.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_FARE }
        val stops = _stops.value

        val invalid = buildSet {
            if (name.isEmpty()) add(NewRouteField.NAME)
            if (origin.isEmpty()) add(NewRouteField.ORIGIN)
            if (destination.isEmpty()) add(NewRouteField.DESTINATION)
            if (fare == null) add(NewRouteField.FARE)
            if (stops.size < MIN_STOPS) add(NewRouteField.STOPS)
        }
        if (invalid.isNotEmpty() || fare == null) return SaveResult.Invalid(invalid)

        // Si el ajuste a las calles aún no terminó, se guarda el recorrido limpio y
        // el ajuste se aplica solo en cuanto llegue.
        val preview = _path.value
        val path = preview.points.takeIf { it.size >= 2 }
            ?: draft.points.map { GeoCoordinate(it.latitude, it.longitude) }

        val route = BusRoute(
            id = RouteRepository.newUserRouteId(),
            letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: DEFAULT_LETTER,
            name = name,
            origin = origin,
            destination = destination,
            durationMinutes = draft.durationMinutes().coerceAtLeast(1),
            fare = fare,
            schedule = form.schedule.trim().ifEmpty { app.getString(R.string.new_route_schedule_unknown) },
            stops = stops.mapIndexed { index, stop ->
                // Cada parada queda sobre el recorrido, no a unos metros por el error del GPS.
                val onPath = PathCleaner.snapToPath(GeoCoordinate(stop.latitude, stop.longitude), path)
                Stop(
                    name = stop.name.trim().ifEmpty { app.getString(R.string.new_route_stop_default, index + 1) },
                    latitude = onPath.latitude,
                    longitude = onPath.longitude
                )
            },
            accentIndex = RouteRepository.nextAccentIndex(),
            path = path,
            pathSnapped = preview.snapped,
            createdByUser = true
        )

        RouteRepository.add(route)
        if (!preview.snapped) snapping?.let { RouteSnapper.applyWhenReady(route.id, it) }
        RouteRecorderService.completeSaved(app)
        return SaveResult.Saved(route.id)
    }

    companion object {
        const val MIN_STOPS = 2
        private const val MAX_FARE = 50.0
        private const val DEFAULT_LETTER = "R"
    }
}

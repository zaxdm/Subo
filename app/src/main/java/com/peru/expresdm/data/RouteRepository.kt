package com.peru.expresdm.data

import android.content.Context
import android.util.Log
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Catálogo de rutas: las que trae la app, las que graban los usuarios y, con
 * servidor configurado, las que comparten los demás.
 *
 * El servidor manda cuando responde. Lo último que se recibió queda en el
 * teléfono, así que sin conexión la app sigue mostrando rutas. Las rutas que se
 * graban se guardan primero en local (para que el usuario no pierda su trabajo
 * si falla la red) y se suben después, conservando su id.
 */
object RouteRepository {

    private const val TAG = "RouteRepository"

    private var store: UserRouteStore? = null
    private var userRoutes: List<BusRoute> = emptyList()

    /** Rutas que llegaron del servidor en la última consulta. */
    private var sharedRoutes: List<BusRoute> = emptyList()
    private var sharedFile: java.io.File? = null
    private var hasSharedCatalog = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _routes = MutableStateFlow(buildRoutes())

    /** Catálogo completo, que avisa cuando alguien añade o borra una ruta. */
    val routes: StateFlow<List<BusRoute>> = _routes.asStateFlow()

    /** Carga las rutas grabadas por el usuario. Se llama una vez al arrancar la app. */
    fun init(context: Context) {
        if (store != null) return
        val userStore = UserRouteStore(context)
        store = userStore
        userRoutes = userStore.load()
        sharedFile = java.io.File(context.applicationContext.filesDir, "rutas_compartidas.json")
        sharedFile?.takeIf { it.exists() }?.let { file ->
            runCatching { parseRoutes(JSONArray(file.readText())) }.onSuccess {
                sharedRoutes = it
                hasSharedCatalog = true
            }
        }
        publish()

        // El catálogo compartido se pide en segundo plano: la app abre con lo
        // que ya tenía y se actualiza sola.
        scope.launch { refresh() }
    }

    /** Trae del servidor el catálogo compartido. Si falla, se queda el anterior. */
    suspend fun refresh() {
        if (!ApiClient.isConfigured) return

        when (val result = ApiClient.get("/api/routes", AccountRepository.token)) {
            is ApiResult.Failure -> Log.i(TAG, "No se pudo traer el catálogo: ${result.reason}")
            is ApiResult.Success -> {
                val fromServer = parseRoutes(result.data.optJSONArray("routes"))
                val array = result.data.optJSONArray("routes") ?: return
                sharedFile?.let { file ->
                    withContext(Dispatchers.IO) {
                        runCatching { file.writeText(array.toString()) }
                            .onFailure { Log.w(TAG, "No se pudo guardar el catálogo", it) }
                    }
                }
                withContext(Dispatchers.Main) {
                    sharedRoutes = fromServer
                    hasSharedCatalog = true
                    // Una ruta propia que ya está en el servidor no se repite.
                    val sharedIds = fromServer.map { it.id }.toSet()
                    userRoutes = userRoutes.filterNot { it.id in sharedIds }
                    store?.saveAll(userRoutes)
                    publish()
                }
            }
        }
    }

    fun all(): List<BusRoute> = _routes.value

    fun byId(id: String): BusRoute? = all().firstOrNull { it.id == id }

    fun search(query: String): List<BusRoute> = all().filter { it.matches(query) }

    fun add(route: BusRoute) {
        // Si es una ruta compartida que se está actualizando (por ejemplo al
        // ajustarla a las calles), se cambia ahí mismo.
        if (sharedRoutes.any { it.id == route.id }) {
            sharedRoutes = sharedRoutes.map { if (it.id == route.id) route else it }
            publish()
            upload(route)
            return
        }

        userRoutes = if (userRoutes.any { it.id == route.id }) {
            userRoutes.map { if (it.id == route.id) route else it }
        } else {
            userRoutes + route
        }
        store?.saveAll(userRoutes)
        publish()
        upload(route)
    }

    fun remove(routeId: String) {
        userRoutes = userRoutes.filterNot { it.id == routeId }
        sharedRoutes = sharedRoutes.filterNot { it.id == routeId }
        store?.saveAll(userRoutes)
        publish()

        if (ApiClient.isConfigured && AccountRepository.isSignedIn) {
            scope.launch { ApiClient.delete("/api/routes/$routeId", AccountRepository.token) }
        }
    }

    fun newUserRouteId(): String = "usuario-${UUID.randomUUID()}"

    /** Color para una ruta nueva, distinto del de la anterior. */
    fun nextAccentIndex(): Int = all().size

    /**
     * Sube una ruta grabada para que la vean los demás. Sin sesión se queda en
     * el teléfono: la ruta no se pierde, solo no se comparte.
     */
    private fun upload(route: BusRoute) {
        if (!route.createdByUser || !ApiClient.isConfigured || !AccountRepository.isSignedIn) return

        scope.launch {
            val body = JSONObject()
                .put("id", route.id)
                .put("letter", route.letter)
                .put("name", route.name)
                .put("origin", route.origin)
                .put("destination", route.destination)
                .put("durationMinutes", route.durationMinutes)
                .put("fare", route.fare)
                .put("schedule", route.schedule)
                .put("pathSnapped", route.pathSnapped)
                .put("vehicleName", route.vehicleName)
                .put("imageUrl", route.imageUrl ?: JSONObject.NULL)
                .put("description", route.description)
                .put("bidirectional", route.bidirectional)
                .put(
                    "stops",
                    JSONArray().apply {
                        route.stops.forEach { stop ->
                            put(
                                JSONObject()
                                    .put("name", stop.name)
                                    .put("latitude", stop.latitude)
                                    .put("longitude", stop.longitude)
                            )
                        }
                    }
                )
                .put(
                    "path",
                    route.path?.let { path ->
                        JSONArray().apply {
                            path.forEach { point ->
                                put(JSONObject().put("latitude", point.latitude).put("longitude", point.longitude))
                            }
                        }
                    } ?: JSONObject.NULL
                )

            when (val result = ApiClient.post("/api/routes", body, AccountRepository.token)) {
                is ApiResult.Success -> Log.i(TAG, "Ruta ${route.id} compartida")
                is ApiResult.Failure -> Log.i(TAG, "No se pudo compartir la ruta: ${result.reason}")
            }
        }
    }

    private fun publish() {
        val base = if (hasSharedCatalog) sharedRoutes else buildRoutes()
        _routes.value = base + userRoutes
    }

    // --- Traducción de lo que manda el servidor ---

    private fun parseRoutes(array: JSONArray?): List<BusRoute> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                parseRoute(array.getJSONObject(i))
            } catch (e: JSONException) {
                Log.w(TAG, "Ruta ilegible del servidor", e)
                null
            }
        }
    }

    private fun parseRoute(json: JSONObject): BusRoute {
        val stopsArray = json.getJSONArray("stops")
        val stops = (0 until stopsArray.length()).map { i ->
            val stop = stopsArray.getJSONObject(i)
            Stop(stop.getString("name"), stop.getDouble("latitude"), stop.getDouble("longitude"))
        }
        val pathArray = json.optJSONArray("path")
        val path = pathArray?.let { array ->
            (0 until array.length()).map { i ->
                val point = array.getJSONObject(i)
                GeoCoordinate(point.getDouble("latitude"), point.getDouble("longitude"))
            }
        }

        return BusRoute(
            id = json.getString("id"),
            letter = json.getString("letter"),
            name = json.getString("name"),
            origin = json.getString("origin"),
            destination = json.getString("destination"),
            durationMinutes = json.getInt("durationMinutes"),
            fare = json.getDouble("fare"),
            schedule = json.getString("schedule"),
            stops = stops,
            accentIndex = json.optInt("accentIndex"),
            path = path?.takeIf { it.size >= 2 },
            pathSnapped = json.optBoolean("pathSnapped"),
            createdByUser = json.optBoolean("createdByUser"),
            vehicleName = json.optString("vehicleName"),
            imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
            description = json.optString("description"),
            bidirectional = json.optBoolean("bidirectional", true)
        )
    }

    private fun buildRoutes(): List<BusRoute> = listOf(
        BusRoute(
            id = "california-porvenir",
            letter = "A",
            name = "California",
            origin = "El Porvenir",
            destination = "Huanchaco",
            durationMinutes = 38,
            fare = 2.00,
            schedule = "05:00 – 22:30",
            accentIndex = 0,
            stops = listOf(
                Stop("El Porvenir", -8.0806, -78.9950),
                Stop("Av. Pumacahua", -8.0921, -79.0090),
                Stop("Av. América Sur", -8.1085, -79.0245),
                Stop("Plaza de Armas", -8.1120, -79.0288),
                Stop("Av. Mansiche", -8.1024, -79.0389),
                Stop("Óvalo Huanchaco", -8.0932, -79.0780),
                Stop("Huanchaco", -8.0797, -79.1187)
            )
        ),
        BusRoute(
            id = "california-esperanza",
            letter = "B",
            name = "California",
            origin = "La Esperanza",
            destination = "Buenos Aires",
            durationMinutes = 42,
            fare = 2.00,
            schedule = "05:15 – 22:00",
            accentIndex = 1,
            stops = listOf(
                Stop("La Esperanza", -8.0704, -79.0470),
                Stop("Av. Condorcanqui", -8.0839, -79.0432),
                Stop("Av. Mansiche", -8.1024, -79.0389),
                Stop("Av. España", -8.1132, -79.0330),
                Stop("Av. Larco", -8.1226, -79.0332),
                Stop("Buenos Aires", -8.1339, -79.0490)
            )
        ),
        BusRoute(
            id = "huanchaco-express",
            letter = "H",
            name = "Huanchaco Express",
            origin = "Centro Cívico",
            destination = "Huanchaco",
            durationMinutes = 30,
            fare = 2.50,
            schedule = "06:00 – 21:00",
            accentIndex = 2,
            stops = listOf(
                Stop("Centro Cívico", -8.1108, -79.0301),
                Stop("Av. Mansiche", -8.1024, -79.0389),
                Stop("Óvalo Huanchaco", -8.0932, -79.0780),
                Stop("Huanchaco", -8.0797, -79.1187)
            )
        ),
        BusRoute(
            id = "micro-202",
            letter = "C",
            name = "Micro 202",
            origin = "Av. Larco",
            destination = "Av. España",
            durationMinutes = 22,
            fare = 1.50,
            schedule = "05:30 – 23:00",
            accentIndex = 3,
            stops = listOf(
                Stop("Av. Larco", -8.1226, -79.0332),
                Stop("Óvalo Larco", -8.1191, -79.0345),
                Stop("Av. Húsares de Junín", -8.1155, -79.0318),
                Stop("Av. España", -8.1132, -79.0330)
            )
        ),
        BusRoute(
            id = "linea-303",
            letter = "D",
            name = "Bus 303",
            origin = "Av. Los Incas",
            destination = "Moche",
            durationMinutes = 35,
            fare = 2.00,
            schedule = "05:00 – 21:30",
            accentIndex = 4,
            stops = listOf(
                Stop("Av. Los Incas", -8.1170, -79.0210),
                Stop("Av. Pumacahua", -8.0921, -79.0090),
                Stop("Óvalo Grau", -8.1195, -79.0264),
                Stop("Moche", -8.1697, -79.0083)
            )
        ),
        BusRoute(
            id = "linea-ucv",
            letter = "E",
            name = "Universitaria",
            origin = "UPN",
            destination = "UNT",
            durationMinutes = 28,
            fare = 1.80,
            schedule = "06:00 – 22:00",
            accentIndex = 5,
            stops = listOf(
                Stop("UPN", -8.0684, -79.0575),
                Stop("Av. El Ejército", -8.1063, -79.0350),
                Stop("Av. Juan Pablo II", -8.1191, -79.0407),
                Stop("UNT", -8.1178, -79.0417)
            )
        )
    )
}

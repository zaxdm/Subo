package com.peru.expresdm.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.peru.expresdm.MainActivity
import com.peru.expresdm.R
import com.peru.expresdm.data.ArrivalRules
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Un viaje que se está compartiendo en vivo. */
data class LiveTrip(
    /** El id que le dio el servidor: con él se arma el enlace que se comparte. */
    val id: String,
    val routeName: String,
    /** Dónde se baja, si se sabe: al llegar, el viaje se termina solo. */
    val stopLatitude: Double? = null,
    val stopLongitude: Double? = null
)

/**
 * Viaje en vivo (Premium): mientras dura, manda al servidor por dónde va el
 * teléfono, y quien tiene el enlace lo ve en un mapa.
 *
 * Es un servicio en primer plano, con su notificación siempre a la vista: nadie
 * comparte su ubicación sin saberlo. Se termina al pulsar «Dejar de compartir»,
 * al llegar a la parada o, por si se olvida, al cabo de unas horas. Al terminar,
 * el servidor borra la última posición.
 */
class LiveTripService : Service(), LocationListener {

    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Cuándo se mandó la última posición, en el reloj que no salta ([SystemClock.elapsedRealtime]). */
    private var lastSentAt = 0L

    private val giveUp = Runnable {
        Log.i(TAG, "Viaje en vivo caducado")
        finish()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.toTrip()?.let { startSharing(it) } ?: finish()
            else -> finish()
        }
        // Si Android cierra el proceso a medio viaje, vuelve a entregar el pedido
        // y se sigue compartiendo: quien mira cuenta con ello.
        return START_REDELIVER_INTENT
    }

    private fun startSharing(trip: LiveTrip) {
        createChannel()
        startForegroundCompat(buildNotification(trip))

        val manager = getSystemService<LocationManager>()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager?.isProviderEnabled(it) == true }
        if (!hasLocationPermission() || manager == null || providers.isEmpty()) {
            finish()
            return
        }

        stopLocationUpdates()
        _state.value = trip
        lastSentAt = 0L
        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
            locationManager = manager
        } catch (security: SecurityException) {
            Log.e(TAG, "Permiso de ubicación revocado al compartir el viaje", security)
            finish()
            return
        }

        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, MAX_SHARE_MS)
    }

    override fun onLocationChanged(location: Location) {
        val trip = _state.value ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        // Una lectura que puede estar a varias cuadras confunde más que ayuda.
        if (accuracy != null && accuracy > ArrivalRules.MAX_ACCURACY_METERS) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSentAt < SEND_INTERVAL_MS) return
        lastSentAt = now

        val arrived = trip.stopLatitude != null && trip.stopLongitude != null &&
            NearbyRoutes.distanceMeters(
                location.latitude, location.longitude, trip.stopLatitude, trip.stopLongitude
            ) <= ARRIVED_METERS

        scope.launch {
            val body = JSONObject().put("latitude", location.latitude).put("longitude", location.longitude)
            val result = ApiClient.post("/api/trips/${trip.id}/position", body, AccountRepository.token)
            // El servidor ya no lo tiene por abierto (caducó, o se cerró la sesión):
            // no tiene sentido seguir con el GPS encendido.
            val refused = result is ApiResult.Failure &&
                (result.reason == ApiFailure.REJECTED || result.reason == ApiFailure.UNAUTHORIZED)
            if (refused || arrived) handler.post { if (_state.value?.id == trip.id) finish() }
        }
    }

    @Deprecated("Obligatorio antes de API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    /** Termina el viaje aquí y en el servidor, que borra por dónde iba. */
    private fun finish() {
        handler.removeCallbacks(giveUp)
        stopLocationUpdates()
        _state.value?.let { trip ->
            _state.value = null
            val token = AccountRepository.token
            scope.launch { ApiClient.delete("/api/trips/${trip.id}", token) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
        } catch (security: SecurityException) {
            Log.w(TAG, "No se pudieron soltar las actualizaciones de ubicación", security)
        }
        locationManager = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        stopLocationUpdates()
        super.onDestroy()
    }

    // --- Notificación ---

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.live_trip_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.live_trip_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(trip: LiveTrip): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, LiveTripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.live_trip_notification_title))
            .setContentText(getString(R.string.live_trip_notification_text, trip.routeName))
            .setSmallIcon(R.drawable.ic_my_location)
            .setContentIntent(open)
            .addAction(0, getString(R.string.live_trip_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun Intent.toTrip(): LiveTrip? {
        val id = getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() } ?: return null
        return LiveTrip(
            id = id,
            routeName = getStringExtra(EXTRA_ROUTE_NAME).orEmpty(),
            stopLatitude = getDoubleExtra(EXTRA_STOP_LAT, Double.NaN).takeUnless { it.isNaN() },
            stopLongitude = getDoubleExtra(EXTRA_STOP_LON, Double.NaN).takeUnless { it.isNaN() }
        )
    }

    companion object {
        private const val TAG = "LiveTrip"
        private const val CHANNEL_ID = "viaje_en_vivo"
        private const val NOTIFICATION_ID = 1006

        private const val ACTION_START = "com.peru.expresdm.livetrip.START"
        private const val ACTION_STOP = "com.peru.expresdm.livetrip.STOP"
        private const val EXTRA_ID = "id"
        private const val EXTRA_ROUTE_NAME = "routeName"
        private const val EXTRA_STOP_LAT = "stopLat"
        private const val EXTRA_STOP_LON = "stopLon"

        private const val UPDATE_INTERVAL_MS = 5_000L

        /** Cada cuánto se manda la posición: de sobra para seguir a un micro, y poco gasto de datos. */
        private const val SEND_INTERVAL_MS = 10_000L

        /** A esta distancia de la parada, ya llegó: el viaje se termina solo. */
        private const val ARRIVED_METERS = 80.0

        /** Lo mismo que le da el servidor: ningún trayecto por la ciudad dura más. */
        private const val MAX_SHARE_MS = 3 * 60 * 60 * 1000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _state = MutableStateFlow<LiveTrip?>(null)

        /** El viaje que se está compartiendo; `null` si no hay ninguno. */
        val state: StateFlow<LiveTrip?> = _state.asStateFlow()

        /**
         * Abre el viaje en el servidor. Devuelve su id, o el motivo por el que no
         * se pudo (sin conexión, sin Premium…).
         */
        suspend fun create(
            routeId: String,
            stopName: String?,
            stopLatitude: Double?,
            stopLongitude: Double?
        ): ApiResult<String> {
            val body = JSONObject().put("routeId", routeId)
            if (stopLatitude != null && stopLongitude != null) {
                body.put("stopName", stopName.orEmpty())
                    .put("stopLatitude", stopLatitude)
                    .put("stopLongitude", stopLongitude)
            }
            return when (val result = ApiClient.post("/api/trips", body, AccountRepository.token)) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> result.data.optString("id").takeIf { it.isNotBlank() }
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(ApiFailure.SERVER)
            }
        }

        fun start(context: Context, trip: LiveTrip) {
            val intent = Intent(context, LiveTripService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, trip.id)
                .putExtra(EXTRA_ROUTE_NAME, trip.routeName)
            if (trip.stopLatitude != null && trip.stopLongitude != null) {
                intent.putExtra(EXTRA_STOP_LAT, trip.stopLatitude).putExtra(EXTRA_STOP_LON, trip.stopLongitude)
            }
            // Se marca ya, para que la interfaz no espere a que arranque el servicio.
            _state.value = trip
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (_state.value == null) return
            context.startService(Intent(context, LiveTripService::class.java).setAction(ACTION_STOP))
        }
    }
}

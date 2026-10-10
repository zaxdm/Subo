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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.peru.expresdm.MainActivity
import com.peru.expresdm.R
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.ArrivalRules
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.ui.formatDistance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Parada donde el usuario quiere que le avisen al llegar. */
data class ArrivalTarget(
    val routeId: String,
    val routeName: String,
    val stopName: String,
    val latitude: Double,
    val longitude: Double
)

data class ArrivalState(
    /** Aviso activo; `null` si no hay ninguno. */
    val target: ArrivalTarget? = null,
    /** Distancia a la parada en la última lectura útil del GPS. */
    val distanceMeters: Double? = null,
    /** Se llegó y se avisó: la interfaz lo muestra una vez y lo limpia. */
    val arrivedAt: ArrivalTarget? = null,
    /** No se pudo activar (sin permiso o con la ubicación apagada). */
    val failed: Boolean = false
)

/**
 * Aviso de llegada: sigue la ubicación del usuario mientras va en el micro y le
 * avisa cuando está llegando a su parada: una notificación y, según Ajustes,
 * vibración y sonido.
 *
 * Es un servicio en primer plano para que funcione con la pantalla apagada y la
 * app cerrada, que es justo cuando hace falta. Se detiene solo al avisar, al
 * cancelarlo o, por si el usuario se olvida, al cabo de unas horas.
 */
class ArrivalAlarmService : Service(), LocationListener {

    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastNotifiedDistance: Double? = null

    private val giveUp = Runnable {
        Log.i(TAG, "Aviso de llegada caducado sin llegar a la parada")
        _state.value = ArrivalState()
        stopEverything()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.toTarget()?.let { startWatching(it) } ?: stopEverything()
            ACTION_CANCEL -> {
                _state.value = ArrivalState()
                stopEverything()
            }
            else -> stopEverything()
        }
        // Si Android cierra el proceso, el sistema vuelve a entregar el pedido y
        // el aviso sigue activo: el usuario cuenta con él.
        return START_REDELIVER_INTENT
    }

    private fun startWatching(target: ArrivalTarget) {
        // Android exige mostrar la notificación nada más arrancar el servicio.
        createChannels()
        startForegroundCompat(buildTrackingNotification(target, distanceMeters = null))

        val manager = getSystemService<LocationManager>()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager?.isProviderEnabled(it) == true }
        if (!hasLocationPermission() || manager == null || providers.isEmpty()) {
            _state.value = ArrivalState(failed = true)
            stopEverything()
            return
        }

        stopLocationUpdates()
        _state.value = ArrivalState(target = target)
        lastNotifiedDistance = null

        try {
            // Aquí sirven también las lecturas de la red: basta con saber que se
            // acerca, no hace falta dibujar nada con precisión.
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
            locationManager = manager
        } catch (security: SecurityException) {
            Log.e(TAG, "Permiso de ubicación revocado al activar el aviso", security)
            _state.value = ArrivalState(failed = true)
            stopEverything()
            return
        }

        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, MAX_WATCH_MS)
    }

    override fun onLocationChanged(location: Location) {
        val target = _state.value.target ?: return
        val distance = NearbyRoutes.distanceMeters(location.latitude, location.longitude, target.latitude, target.longitude)
        val accuracy = if (location.hasAccuracy()) location.accuracy else null

        val radius = AppSettings.get(this).arrivalDistanceMeters.toDouble()

        when (ArrivalRules.evaluate(distance, accuracy, radius)) {
            ArrivalRules.Reading.ARRIVED -> arrive(target)
            ArrivalRules.Reading.IMPRECISE -> Unit
            ArrivalRules.Reading.APPROACHING -> {
                _state.value = _state.value.copy(distanceMeters = distance)
                val last = lastNotifiedDistance
                // La notificación fija se actualiza cada tanto, no con cada lectura.
                if (last == null || abs(last - distance) >= NOTIFICATION_STEP_METERS) {
                    lastNotifiedDistance = distance
                    getSystemService<NotificationManager>()
                        ?.notify(TRACKING_NOTIFICATION_ID, buildTrackingNotification(target, distance))
                }
            }
        }
    }

    @Deprecated("Obligatorio antes de API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun arrive(target: ArrivalTarget) {
        stopLocationUpdates()
        postArrivalNotification(target)
        val settings = AppSettings.get(this)
        ArrivalSignal.play(this, vibrate = settings.arrivalVibration, sound = settings.arrivalSound)
        _state.value = ArrivalState(arrivedAt = target)
        stopEverything()
    }

    private fun stopEverything() {
        handler.removeCallbacks(giveUp)
        stopLocationUpdates()
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

    // --- Notificaciones ---

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.arrival_channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.arrival_channel_tracking_description)
                setShowBadge(false)
            }
        )

        // Importancia alta para que se vea encima de lo que esté haciendo. El
        // canal va mudo: la vibración y el sonido los pone ArrivalSignal, según
        // lo que el usuario eligió en Ajustes.
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.arrival_channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.arrival_channel_alert_description)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildTrackingNotification(target: ArrivalTarget, distanceMeters: Double?): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ArrivalAlarmService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (distanceMeters == null) {
            getString(R.string.arrival_notification_tracking_waiting, target.routeName)
        } else {
            getString(R.string.arrival_notification_tracking_text, target.routeName, formatDistance(distanceMeters))
        }
        return NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setContentTitle(getString(R.string.arrival_notification_tracking_title, target.stopName))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_arrival_alarm)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.arrival_action_cancel), cancel)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun postArrivalNotification(target: ArrivalTarget) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Sin permiso solo queda el aviso dentro de la app, si está abierta.
            return
        }
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.arrival_notification_title, target.stopName))
            .setContentText(getString(R.string.arrival_notification_text, target.routeName))
            .setSmallIcon(R.drawable.ic_arrival_alarm)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(ARRIVAL_NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TRACKING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(TRACKING_NOTIFICATION_ID, notification)
        }
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun Intent.toTarget(): ArrivalTarget? {
        val routeId = getStringExtra(EXTRA_ROUTE_ID) ?: return null
        return ArrivalTarget(
            routeId = routeId,
            routeName = getStringExtra(EXTRA_ROUTE_NAME).orEmpty(),
            stopName = getStringExtra(EXTRA_STOP_NAME).orEmpty(),
            latitude = getDoubleExtra(EXTRA_LAT, Double.NaN).takeUnless { it.isNaN() } ?: return null,
            longitude = getDoubleExtra(EXTRA_LON, Double.NaN).takeUnless { it.isNaN() } ?: return null
        )
    }

    companion object {
        private const val TAG = "ArrivalAlarm"
        private const val TRACKING_CHANNEL_ID = "aviso_llegada_seguimiento"
        private const val ALERT_CHANNEL_ID = "aviso_llegada"
        private const val TRACKING_NOTIFICATION_ID = 1003
        private const val ARRIVAL_NOTIFICATION_ID = 1004

        private const val ACTION_START = "com.peru.expresdm.arrival.START"
        private const val ACTION_CANCEL = "com.peru.expresdm.arrival.CANCEL"
        private const val EXTRA_ROUTE_ID = "routeId"
        private const val EXTRA_ROUTE_NAME = "routeName"
        private const val EXTRA_STOP_NAME = "stopName"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"

        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_STEP_METERS = 100.0

        /** Ningún trayecto en micro por Trujillo dura tanto: si no llegó, se olvidó del aviso. */
        private const val MAX_WATCH_MS = 3 * 60 * 60 * 1000L

        private val _state = MutableStateFlow(ArrivalState())

        /** Aviso activo y su distancia, observable desde la interfaz. */
        val state: StateFlow<ArrivalState> = _state.asStateFlow()

        fun start(context: Context, target: ArrivalTarget) {
            val intent = Intent(context, ArrivalAlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROUTE_ID, target.routeId)
                .putExtra(EXTRA_ROUTE_NAME, target.routeName)
                .putExtra(EXTRA_STOP_NAME, target.stopName)
                .putExtra(EXTRA_LAT, target.latitude)
                .putExtra(EXTRA_LON, target.longitude)
            // Se marca ya, para que la interfaz no espere a que arranque el servicio.
            _state.value = ArrivalState(target = target)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            if (_state.value.target != null) {
                context.startService(Intent(context, ArrivalAlarmService::class.java).setAction(ACTION_CANCEL))
            }
            _state.value = ArrivalState()
        }

        /** El "llegaste" o el fallo ya se mostró en la app. */
        fun consumeMessages() {
            val current = _state.value
            if (current.arrivedAt != null || current.failed) {
                _state.value = current.copy(arrivedAt = null, failed = false)
            }
        }
    }
}

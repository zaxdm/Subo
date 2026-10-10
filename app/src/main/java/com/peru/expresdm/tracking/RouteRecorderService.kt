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
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.peru.expresdm.MainActivity
import com.peru.expresdm.R
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.RecordingDraft
import com.peru.expresdm.data.RecordingDraftStore
import com.peru.expresdm.data.RecordingPoint
import com.peru.expresdm.ui.formatDistance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecorderStatus {
    IDLE,

    /** Grabando: el servicio está vivo y recibe posiciones. */
    RECORDING,

    /** El usuario pulsó "Finalizar": falta rellenar el formulario. */
    FINISHED,

    /** Android cerró la app a mitad de la grabación; los puntos se salvaron en disco. */
    INTERRUPTED
}

data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val draft: RecordingDraft? = null,
    /** Se terminó sin puntos suficientes para dibujar una ruta. */
    val tooShort: Boolean = false,
    /** No se pudo empezar (sin permiso o sin ubicación). */
    val failed: Boolean = false,
    /** Precisión de la última lectura del GPS, para avisar si la señal es débil. */
    val lastAccuracyMeters: Float? = null
)

/**
 * Graba la ubicación del usuario mientras recorre una ruta nueva en el micro.
 *
 * Es un servicio en primer plano con notificación permanente: así Android no lo
 * cierra al bloquear la pantalla, que es lo normal durante un trayecto. Los
 * puntos se van guardando en disco para no perder el recorrido si aun así la
 * app se cierra.
 */
class RouteRecorderService : Service(), LocationListener {

    private var locationManager: LocationManager? = null
    private lateinit var draftStore: RecordingDraftStore
    private var pointsSinceSave = 0

    override fun onCreate() {
        super.onCreate()
        draftStore = RecordingDraftStore(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_FINISH -> finishRecording()
            ACTION_DISCARD -> discardRecording()
            else -> stopEverything()
        }
        // Si el sistema mata el proceso no revivimos grabando a espaldas del
        // usuario: la grabación queda en disco y se le ofrece al volver.
        return START_NOT_STICKY
    }

    private fun startRecording() {
        // Android exige mostrar la notificación nada más arrancar el servicio.
        createChannel()
        startForegroundCompat(buildNotification(0.0))

        if (_state.value.status == RecorderStatus.RECORDING) return

        val manager = getSystemService<LocationManager>()
        if (!hasLocationPermission() || manager == null) {
            _state.value = RecorderState(failed = true)
            stopEverything()
            return
        }

        val draft = RecordingDraft(startedAt = System.currentTimeMillis(), points = emptyList())
        draftStore.save(draft)
        pointsSinceSave = 0
        _state.value = RecorderState(RecorderStatus.RECORDING, draft)

        try {
            // Solo el GPS. Antes se mezclaban sus lecturas con las de la red (wifi y
            // antenas), que tienen otro error, y el recorrido zigzagueaba entre ambas.
            // La red solo se usa si el GPS está apagado. Sin distancia mínima:
            // también queremos puntos estando quietos, para detectar paradas.
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                _state.value = RecorderState(failed = true)
                stopEverything()
                return
            }
            manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            locationManager = manager
        } catch (security: SecurityException) {
            Log.e(TAG, "Permiso de ubicación revocado al empezar", security)
            _state.value = RecorderState(failed = true)
            stopEverything()
        }
    }

    override fun onLocationChanged(location: Location) {
        val current = _state.value
        if (current.status != RecorderStatus.RECORDING) return
        // En ciudad el GPS a veces salta cientos de metros: esas lecturas deformarían la ruta.
        // Se descartan, pero la interfaz se entera para explicar por qué no avanza.
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        if (accuracy != null && accuracy > MAX_ACCURACY_METERS) {
            _state.value = current.copy(lastAccuracyMeters = accuracy)
            return
        }

        val draft = current.draft ?: return
        val point = RecordingPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis(),
            // Se guarda para que la limpieza del recorrido sepa cuánto fiarse de cada punto.
            accuracy = accuracy
        )

        val last = draft.points.lastOrNull()
        if (last != null) {
            val moved = NearbyRoutes.distanceMeters(last.latitude, last.longitude, point.latitude, point.longitude)
            val elapsed = point.timestamp - last.timestamp
            // Quietos también guardamos un punto de vez en cuando: así se ven las paradas.
            if (moved < MIN_POINT_DISTANCE_M && elapsed < STATIONARY_SAMPLE_MS) return
        }

        val updated = draft.copy(points = draft.points + point)
        _state.value = current.copy(draft = updated, lastAccuracyMeters = accuracy)

        pointsSinceSave++
        if (pointsSinceSave >= SAVE_EVERY_POINTS) {
            draftStore.save(updated)
            pointsSinceSave = 0
        }
        updateNotification(updated.distanceMeters)
    }

    @Deprecated("Obligatorio antes de API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun finishRecording() {
        stopLocationUpdates()
        val draft = _state.value.draft
        if (draft != null && draft.points.size >= MIN_POINTS) {
            draftStore.save(draft)
            _state.value = RecorderState(RecorderStatus.FINISHED, draft)
        } else {
            draftStore.clear()
            _state.value = RecorderState(tooShort = true)
        }
        stopEverything()
    }

    private fun discardRecording() {
        stopLocationUpdates()
        draftStore.clear()
        _state.value = RecorderState()
        stopEverything()
    }

    private fun stopEverything() {
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
        stopLocationUpdates()
        // Si el sistema nos cierra grabando, lo último grabado queda en disco.
        _state.value.draft?.takeIf { _state.value.status == RecorderStatus.RECORDING }?.let { draftStore.save(it) }
        super.onDestroy()
    }

    // --- Notificación ---

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.recorder_channel_description)
            setShowBadge(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun buildNotification(distanceMeters: Double): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recorder_notification_title))
            .setContentText(getString(R.string.recorder_notification_text, formatDistance(distanceMeters)))
            .setSmallIcon(R.drawable.ic_nav_routes)
            .setContentIntent(openApp)
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

    private fun updateNotification(distanceMeters: Double) {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification(distanceMeters))
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "RouteRecorder"
        private const val CHANNEL_ID = "grabacion_rutas"
        private const val NOTIFICATION_ID = 1002

        private const val ACTION_START = "com.peru.expresdm.recorder.START"
        private const val ACTION_FINISH = "com.peru.expresdm.recorder.FINISH"
        private const val ACTION_DISCARD = "com.peru.expresdm.recorder.DISCARD"

        private const val UPDATE_INTERVAL_MS = 2_000L
        /** Por debajo de esta distancia el micro no se ha movido... */
        private const val MIN_POINT_DISTANCE_M = 12.0
        /** ...pero aun quieto guardamos un punto cada este tiempo, para ver las paradas. */
        private const val STATIONARY_SAMPLE_MS = 10_000L
        private const val MAX_ACCURACY_METERS = 40f
        private const val SAVE_EVERY_POINTS = 5
        private const val MIN_POINTS = 2

        private val _state = MutableStateFlow(RecorderState())

        /** Estado de la grabación, observable desde la interfaz. */
        val state: StateFlow<RecorderState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, RouteRecorderService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun finish(context: Context) {
            when (_state.value.status) {
                RecorderStatus.RECORDING ->
                    context.startService(Intent(context, RouteRecorderService::class.java).setAction(ACTION_FINISH))
                // Sin servicio vivo basta con marcarla como terminada.
                RecorderStatus.INTERRUPTED ->
                    _state.value = _state.value.copy(status = RecorderStatus.FINISHED)
                else -> Unit
            }
        }

        fun discard(context: Context) {
            if (_state.value.status == RecorderStatus.RECORDING) {
                context.startService(Intent(context, RouteRecorderService::class.java).setAction(ACTION_DISCARD))
            } else {
                RecordingDraftStore(context).clear()
                _state.value = RecorderState()
            }
        }

        /** La ruta ya se guardó desde el formulario: la grabación pendiente se borra. */
        fun completeSaved(context: Context) {
            RecordingDraftStore(context).clear()
            _state.value = RecorderState()
        }

        /** Al arrancar la app, recupera una grabación que quedó a medias. */
        fun restoreInterrupted(context: Context) {
            if (_state.value.status != RecorderStatus.IDLE) return
            val draft = RecordingDraftStore(context).load() ?: return
            if (draft.points.isEmpty()) {
                RecordingDraftStore(context).clear()
                return
            }
            _state.value = RecorderState(RecorderStatus.INTERRUPTED, draft)
        }

        /** Los avisos de "muy corta" o "no se pudo empezar" se muestran una sola vez. */
        fun consumeMessages() {
            val current = _state.value
            if (current.tooShort || current.failed) {
                _state.value = current.copy(tooShort = false, failed = false)
            }
        }
    }
}

package com.peru.expresdm.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.peru.expresdm.data.AlertRepository
import com.peru.expresdm.data.AlertRules
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.FrequentRoutes
import com.peru.expresdm.data.NearbyRoutes
import com.peru.expresdm.data.RouteUsageStore
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.ui.LastPosition
import java.util.concurrent.TimeUnit

/**
 * Avisa de alertas nuevas cerca del usuario o en las rutas que más usa.
 *
 * Cada cierto tiempo, con la app cerrada, pregunta al servidor qué hay y manda
 * una notificación por cada alerta que no se había visto. No es instantáneo
 * —Android decide cuándo corre, cada 15 minutos o más—, pero no depende de
 * ningún servicio externo: es la red de seguridad de los avisos al momento
 * ([PushService]), que solo existen si el proyecto tiene Firebase.
 */
class AlertWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = AppSettings.get(context)
        if (!settings.alertNotifications || !ApiClient.isConfigured) return Result.success()

        // De paso, el servidor se entera de por dónde anda ahora el teléfono.
        PushRegistration.sync(context)

        val (latitude, longitude) = LastPosition.get(context) ?: return Result.success()

        AlertRepository.init(context)
        AlertRepository.refresh(latitude, longitude)

        val seen = AlertNotifier.seen(context)
        val now = System.currentTimeMillis()

        val favourites = FrequentRoutes.rank(RouteUsageStore.get(context).uses.value).take(FAVOURITE_ROUTES).toSet()
        val wanted = settings.alertTypes.value
        val active = AlertRules.active(AlertRepository.alerts.value, now)
        val fresh = active.filter { alert ->
            alert.id !in seen.orEmpty() && !alert.createdByMe && alert.type in wanted &&
                (alert.routeId in favourites ||
                    NearbyRoutes.distanceMeters(latitude, longitude, alert.latitude, alert.longitude) <= NEARBY_METERS)
        }

        // La primera vez solo se toma nota de lo que hay: nadie quiere diez avisos de golpe.
        if (seen != null) {
            fresh.take(MAX_NOTIFICATIONS).forEach { alert ->
                AlertNotifier.show(context, alert.id, alert.type, alert.routeId, alert.placeName, alert.comment)
            }
        }

        // Solo se recuerdan las vigentes: las caducadas ya no pueden volver a salir.
        AlertNotifier.rememberSeen(context, active.map { it.id }.toSet())
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "alert_watch"

        /** Lo que se considera "cerca" del último sitio donde se usó la app. */
        private const val NEARBY_METERS = 2_000.0

        /** Las rutas más usadas, que interesan aunque la alerta quede lejos. */
        const val FAVOURITE_ROUTES = 3
        private const val MAX_NOTIFICATIONS = 3

        /** Pone en marcha la revisión periódica, o la apaga, según diga Ajustes. */
        fun sync(context: Context) {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (!AppSettings.get(context).alertNotifications || !ApiClient.isConfigured) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AlertWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

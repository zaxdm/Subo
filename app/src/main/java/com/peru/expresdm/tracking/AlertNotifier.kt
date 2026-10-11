package com.peru.expresdm.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.peru.expresdm.MainActivity
import com.peru.expresdm.R
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.ui.AlertStyle

/**
 * La notificación de una alerta nueva, llegue por donde llegue: por el aviso al
 * momento del servidor ([PushService]) o por la revisión que hace la app cada
 * rato ([AlertWatchWorker]). Lleva la cuenta de las ya avisadas para que los
 * dos caminos no repitan la misma.
 */
object AlertNotifier {

    private const val CHANNEL_ID = "alertas_cercanas"
    private const val PREFS_NAME = "alert_watch"
    private const val KEY_SEEN = "seen"

    /** Tope de las que se recuerdan, para que la lista no crezca sin fin. */
    private const val MAX_REMEMBERED = 200

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Los ids de las alertas de las que ya se avisó, o `null` si nunca se ha mirado. */
    fun seen(context: Context): Set<String>? {
        val prefs = prefs(context)
        return if (prefs.contains(KEY_SEEN)) prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty() else null
    }

    fun rememberSeen(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SEEN, ids).apply()
    }

    /** Apunta una más. Devuelve `false` si ya estaba: no hay que avisar otra vez. */
    fun markSeen(context: Context, id: String): Boolean {
        val seen = seen(context).orEmpty()
        if (id in seen) return false
        rememberSeen(context, (seen + id).toList().takeLast(MAX_REMEMBERED).toSet())
        return true
    }

    fun show(
        context: Context,
        id: String,
        type: AlertType,
        routeId: String?,
        placeName: String?,
        comment: String?
    ) {
        if (!canNotify(context)) return
        createChannel(context)

        val typeName = context.getString(AlertStyle.label(type))
        val routeName = routeId?.let { RouteRepository.byId(it)?.name }
        val title = when {
            routeName != null -> context.getString(R.string.alert_watch_title_route, typeName, routeName)
            !placeName.isNullOrBlank() -> context.getString(R.string.alert_watch_title_place, typeName, placeName)
            else -> typeName
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(comment?.takeIf { it.isNotBlank() } ?: context.getString(R.string.alert_watch_text))
            .setSmallIcon(AlertStyle.icon(type))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // Una notificación por alerta: el id sale del suyo para no pisarse entre sí.
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // El permiso se quitó entre la comprobación y el aviso: no hay nada que hacer.
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alert_watch_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.alert_watch_channel_description) }
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

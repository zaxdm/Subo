package com.peru.expresdm.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.peru.expresdm.MainActivity
import com.peru.expresdm.R
import com.peru.expresdm.data.SharedLink

/**
 * Avisos de tu ruta (Premium): la notificación de que cambió el recorrido, el
 * pasaje o el horario de una ruta que el usuario tiene guardada.
 *
 * El servidor la manda al guardar el cambio y dice qué cambió; aquí se escribe
 * en el idioma del teléfono. Al tocarla se abre la ficha de esa ruta.
 */
object RouteChangeNotifier {

    private const val CHANNEL_ID = "cambios_rutas"

    /** Lo que el servidor dice que cambió, con el texto que le corresponde. */
    private val CHANGES = linkedMapOf(
        "path" to R.string.route_change_path,
        "fare" to R.string.route_change_fare,
        "schedule" to R.string.route_change_schedule
    )

    /** [changes] llega como "path,fare": lo que esta versión no conoce se ignora. */
    fun show(context: Context, routeId: String, routeName: String, changes: String) {
        if (!AlertNotifier.canNotify(context)) return
        val known = changes.split(',').mapNotNull { CHANGES[it.trim()] }.map(context::getString)
        if (known.isEmpty()) return
        createChannel(context)

        // Con el enlace de la ruta, MainActivity abre su ficha.
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        SharedLink.forRoute(routeId)?.let { intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse(it)) }
        val open = PendingIntent.getActivity(
            context,
            routeId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = context.getString(R.string.route_change_text, joined(context, known))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.route_change_title, routeName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_nav_routes)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // Una por ruta: un segundo cambio de la misma sustituye al primero.
            NotificationManagerCompat.from(context).notify(CHANNEL_ID.hashCode() xor routeId.hashCode(), notification)
        } catch (_: SecurityException) {
            // El permiso se quitó entre la comprobación y el aviso: no hay nada que hacer.
        }
    }

    /** "nuevo recorrido", "nuevo recorrido y nuevo pasaje", "nuevo recorrido, nuevo pasaje y nuevo horario". */
    private fun joined(context: Context, parts: List<String>): String =
        if (parts.size == 1) {
            parts.single()
        } else {
            context.getString(R.string.route_change_and, parts.dropLast(1).joinToString(", "), parts.last())
        }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.route_change_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.route_change_channel_description) }
        )
    }
}

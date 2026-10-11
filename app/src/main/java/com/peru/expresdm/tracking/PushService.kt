package com.peru.expresdm.tracking

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.AppSettings

/**
 * Recibe los avisos al momento: el servidor manda uno en cuanto alguien publica
 * una alerta cerca de donde anda este teléfono o en una ruta que usa.
 *
 * Llegan solo los datos de la alerta; la notificación se arma aquí, en el
 * idioma del teléfono y respetando lo que el usuario eligió en Ajustes.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushRegistration.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val id = data["alertId"]?.takeIf { it.isNotBlank() } ?: return
        // Un tipo que esta versión no conoce: mejor callar que avisar a medias.
        val type = AlertType.entries.firstOrNull { it.name == data["type"] } ?: return

        val settings = AppSettings.get(this)
        if (!settings.alertNotifications || type !in settings.alertTypes.value) return
        if (!AlertNotifier.markSeen(this, id)) return

        AlertNotifier.show(
            context = this,
            id = id,
            type = type,
            routeId = data["routeId"]?.takeIf { it.isNotBlank() },
            placeName = data["placeName"],
            comment = data["comment"]
        )
    }
}

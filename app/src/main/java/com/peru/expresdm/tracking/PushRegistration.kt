package com.peru.expresdm.tracking

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.FrequentRoutes
import com.peru.expresdm.data.RouteUsageStore
import com.peru.expresdm.data.SavedRoutes
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiResult
import com.peru.expresdm.ui.LastPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Apunta este teléfono a los avisos al momento, o lo borra, según diga Ajustes.
 *
 * Al servidor se le cuenta lo justo para que sepa a quién avisar: por dónde
 * andaba el teléfono la última vez que se usó la app, qué rutas usa más y
 * cuáles tiene guardadas (de esas se le avisa si cambian, con Premium).
 *
 * Si la app se compiló sin el archivo de Firebase (`app/google-services.json`)
 * no hace nada: los avisos llegan igual, más tarde, por [AlertWatchWorker].
 */
object PushRegistration {

    private const val TAG = "PushRegistration"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** `true` si esta compilación trae Firebase configurado. */
    fun isAvailable(context: Context): Boolean = try {
        FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    } catch (_: RuntimeException) {
        false
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        if (!ApiClient.isConfigured || !isAvailable(app)) return
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> scope.launch { send(app, token) } }
                .addOnFailureListener { Log.i(TAG, "Firebase no dio un identificador para este teléfono", it) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo hablar con Firebase", e)
        }
    }

    /** Lo llama [PushService] cuando Firebase cambia el identificador del teléfono. */
    fun onNewToken(context: Context, token: String) {
        val app = context.applicationContext
        if (ApiClient.isConfigured) scope.launch { send(app, token) }
    }

    private suspend fun send(context: Context, token: String) {
        val result = if (AppSettings.get(context).alertNotifications) {
            val body = JSONObject().put("token", token)
            LastPosition.get(context)?.let { (latitude, longitude) ->
                body.put("latitude", latitude).put("longitude", longitude)
            }
            val routes = FrequentRoutes.rank(RouteUsageStore.get(context).uses.value)
                .take(AlertWatchWorker.FAVOURITE_ROUTES)
            body.put("routeIds", JSONArray(routes))
            body.put("savedRouteIds", JSONArray(SavedRoutes.get(context).ids.value.sorted()))
            ApiClient.post("/api/push/devices", body, AccountRepository.token)
        } else {
            ApiClient.post("/api/push/devices/remove", JSONObject().put("token", token))
        }
        if (result is ApiResult.Failure) Log.i(TAG, "El servidor no apuntó el teléfono: ${result.reason}")
    }
}

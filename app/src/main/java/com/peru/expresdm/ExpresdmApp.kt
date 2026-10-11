package com.peru.expresdm

import android.app.Application
import android.util.Log
import com.peru.expresdm.data.AlertRepository
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.tracking.AlertWatchWorker
import com.peru.expresdm.tracking.PushRegistration
import com.peru.expresdm.tracking.RouteRecorderService
import org.osmdroid.config.Configuration

/**
 * Configuración que se hace una sola vez al arrancar.
 *
 * OpenStreetMap exige un user agent propio por aplicación: sin él, el servidor
 * de teselas devuelve 403 y el mapa aparece en blanco. La caché va al
 * almacenamiento privado de la app, así no hace falta ningún permiso de disco.
 */
class ExpresdmApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // El tema debe quedar fijado antes de que se cree la primera pantalla,
        // o la app parpadearía del claro al oscuro al abrirse.
        AppSettings.get(this).applySavedTheme()

        AccountRepository.init(this)
        RouteRepository.init(this)
        AlertRepository.init(this)
        // Si Android cerró la app mientras se grababa una ruta, se recupera.
        RouteRecorderService.restoreInterrupted(this)
        // La revisión de alertas cercanas sigue programada aunque se cierre la app.
        try {
            AlertWatchWorker.sync(this)
            PushRegistration.sync(this)
        } catch (e: IllegalStateException) {
            // WorkManager sin iniciar (pasa en las pruebas): la app sigue sin los avisos.
            Log.w("ExpresdmApp", "No se pudo programar la revisión de alertas", e)
        }

        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = java.io.File(filesDir, "osmdroid")
            osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
        }
    }
}

package com.peru.expresdm.tracking

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.peru.expresdm.R

/**
 * El permiso para seguir trabajando con la app cerrada.
 *
 * Muchos teléfonos (Xiaomi, Huawei, Samsung…) cierran las apps que no se están
 * mirando para ahorrar batería, y con ellas se va el aviso de bajada: justo
 * cuando el teléfono va en el bolsillo. Android deja pedirle al usuario que
 * exceptúe a la app de ese ahorro; se le explica por qué y decide él.
 */
object BackgroundAccess {

    private const val PREFS_NAME = "background_access"
    private const val KEY_ASKED = "asked"

    /** El teléfono ya deja a la app trabajar cerrada. */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Al activar un aviso de bajada, explica por qué hace falta el permiso y
     * lleva a darlo. Se pregunta una sola vez: quien dijo que no, no quiere
     * que se le insista en cada viaje.
     */
    fun askOnce(activity: Activity) {
        if (granted(activity) || activity.isFinishing) return
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.background_access_title)
            .setMessage(R.string.background_access_message)
            .setPositiveButton(R.string.background_access_allow) { _, _ -> request(activity) }
            .setNegativeButton(R.string.background_access_later, null)
            .show()
    }

    // La app se reparte fuera de Google Play, y un aviso de bajada es de los
    // casos para los que existe este permiso.
    @SuppressLint("BatteryLife")
    private fun request(activity: Activity) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${activity.packageName}"))
        try {
            activity.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            // Algunos teléfonos no tienen la pregunta directa: se abre la lista.
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // Sin esa pantalla no hay nada que pedir.
            }
        }
    }
}

package com.peru.expresdm.data.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Quién está usando la app, cuando ha iniciado sesión. */
data class Session(
    val token: String,
    val userId: String,
    val email: String,
    val displayName: String
)

/**
 * Guarda la sesión en el teléfono para no pedir la contraseña en cada arranque.
 *
 * El token es la llave de la cuenta: vive en el almacenamiento privado de la
 * app, al que no llegan otras aplicaciones.
 */
class SessionStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(read())

    /** Sesión actual; `null` si nadie ha iniciado sesión. */
    val session: StateFlow<Session?> = _session.asStateFlow()

    val token: String? get() = _session.value?.token

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.displayName)
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun read(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Session(
            token = token,
            userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, "").orEmpty()
        )
    }

    companion object {
        private const val PREFS_NAME = "expresdm_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "display_name"

        @Volatile
        private var instance: SessionStore? = null

        fun get(context: Context): SessionStore =
            instance ?: synchronized(this) {
                instance ?: SessionStore(context).also { instance = it }
            }
    }
}

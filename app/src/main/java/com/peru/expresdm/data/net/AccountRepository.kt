package com.peru.expresdm.data.net

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Cuentas de usuario contra el servidor.
 *
 * La app sigue funcionando sin cuenta: se pueden ver rutas y alertas. La sesión
 * hace falta para publicar alertas, votar, compartir rutas y usar el asistente,
 * porque todo eso escribe en el servidor o gasta su cuota de IA.
 */
object AccountRepository {

    private lateinit var store: SessionStore
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Sesión actual, observable desde la interfaz. */
    val session: StateFlow<Session?> get() = store.session

    val token: String? get() = store.token

    val isSignedIn: Boolean get() = store.token != null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!::store.isInitialized) store = SessionStore.get(context)
    }

    suspend fun register(email: String, password: String, displayName: String): ApiResult<Session> =
        authenticate(
            "/api/auth/register",
            JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("displayName", displayName.trim())
        )

    suspend fun login(email: String, password: String): ApiResult<Session> =
        authenticate(
            "/api/auth/login",
            JSONObject().put("email", email.trim()).put("password", password)
        )

    suspend fun googleChallenge(): ApiResult<JSONObject> =
        ApiClient.post("/api/auth/google/challenge", JSONObject())

    suspend fun loginGoogle(idToken: String, nonce: String, existingPassword: String): ApiResult<Session> =
        authenticate("/api/auth/google", JSONObject()
            .put("idToken", idToken).put("nonce", nonce)
            .apply { if (existingPassword.isNotBlank()) put("existingPassword", existingPassword) })

    fun signOut() {
        store.clear()
        scope.launch {
            try { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* La sesión local ya está cerrada. */ }
        }
    }

    /**
     * Cambia el nombre con el que se ve al usuario en alertas y rutas.
     *
     * El servidor manda el nombre ya limpio (sin espacios de sobra), y ese es
     * el que se guarda, para que el teléfono muestre lo mismo que ven los demás.
     */
    suspend fun updateName(displayName: String): ApiResult<Session> {
        val current = session.value ?: return ApiResult.Failure(ApiFailure.UNAUTHORIZED)
        val body = JSONObject().put("displayName", displayName.trim())
        return when (val result = ApiClient.post("/api/me", body, current.token)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val saved = result.data.optString("displayName").takeIf { it.isNotBlank() }
                    ?: displayName.trim()
                val updated = current.copy(displayName = saved)
                store.save(updated)
                ApiResult.Success(updated)
            }
        }
    }

    /**
     * Comprueba que la sesión guardada sigue valiendo. Si el servidor la
     * rechaza (cambió el secreto, caducó, se borró la cuenta) se cierra aquí,
     * en lugar de fallar más tarde en medio de otra cosa.
     */
    suspend fun refresh() {
        if (!isSignedIn || !ApiClient.isConfigured) return
        val result = ApiClient.get("/api/me", token)
        if (result is ApiResult.Failure && result.reason == ApiFailure.UNAUTHORIZED) signOut()
    }

    private suspend fun authenticate(path: String, body: JSONObject): ApiResult<Session> =
        when (val result = ApiClient.post(path, body)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val token = result.data.optString("token")
                val user = result.data.optJSONObject("user")
                if (token.isBlank() || user == null) {
                    ApiResult.Failure(ApiFailure.SERVER)
                } else {
                    val session = Session(
                        token = token,
                        userId = user.optString("id"),
                        email = user.optString("email"),
                        displayName = user.optString("displayName")
                    )
                    store.save(session)
                    ApiResult.Success(session)
                }
            }
        }
}

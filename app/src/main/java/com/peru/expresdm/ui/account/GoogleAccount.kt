package com.peru.expresdm.ui.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.peru.expresdm.BuildConfig
import com.peru.expresdm.data.net.ApiClient

/** El selector de cuentas es de Google; la app no solicita su contraseña. */
object GoogleAccount {
    val isConfigured: Boolean get() = ApiClient.isConfigured &&
        BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")

    suspend fun idToken(context: Context, nonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unexpected Google credential")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

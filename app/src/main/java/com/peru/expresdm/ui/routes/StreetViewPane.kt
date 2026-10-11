package com.peru.expresdm.ui.routes

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.peru.expresdm.databinding.ViewStreetPaneBinding
import java.util.Locale

/**
 * La vista de calle de un punto del mapa, en un panel del inicio.
 *
 * Es la página de Google Maps en un navegador propio: no pide clave ni cuenta
 * de Google, y no saca al usuario de la app. Donde Google no tiene fotos de la
 * calle, enseña su mapa de la zona.
 */
class StreetViewPane(private val binding: ViewStreetPaneBinding) {

    private var web: WebView? = null

    /** Cambia con cada punto pedido: lo que llegue tarde de uno anterior se ignora. */
    private var request = 0

    val isOpen: Boolean get() = binding.root.isVisible

    /** Abre el panel, si no lo estaba, y enseña la calle de ese punto. */
    fun show(latitude: Double, longitude: Double) {
        val web = web ?: createWeb().also { web = it }
        val current = ++request
        binding.root.isVisible = true
        binding.cover.isVisible = true
        binding.errorText.isVisible = false
        web.isVisible = true
        web.loadUrl(
            String.format(
                Locale.US,
                "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=%.6f,%.6f",
                latitude,
                longitude
            )
        )
        // Si la página de Google cambia y la pregunta no se responde sola, o si
        // ahí no hay fotos de la calle, se enseña lo que haya cargado.
        binding.cover.postDelayed({ if (current == request) reveal() }, REVEAL_TIMEOUT_MS)
    }

    fun close() {
        request++
        binding.root.isVisible = false
        // La página deja de gastar datos y batería mientras no se ve.
        web?.loadUrl(BLANK)
    }

    /** Al irse la pantalla que lo aloja. */
    fun destroy() {
        request++
        web?.let {
            binding.webHolder.removeView(it)
            it.destroy()
        }
        web = null
    }

    /** Quita la cortina que tapa la página mientras carga. */
    private fun reveal() {
        binding.cover.isVisible = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWeb(): WebView {
        val web = WebView(binding.root.context)
        // La página de Google Maps no funciona sin JavaScript ni almacenamiento.
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            // Los enlaces que abrirían la app de Google Maps o la tienda
            // (intent://, market://) se ignoran: la vista se queda aquí.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in WEB_SCHEMES

            override fun onPageFinished(view: WebView, url: String?) {
                // Antes de la calle, Google pregunta si se quiere abrir su app:
                // se responde que no, que es lo que el usuario ya eligió.
                if (url != BLANK) view.evaluateJavascript(STAY_ON_WEB, null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // La dirección de una vista de calle lleva sus datos detrás.
                if (url?.contains(PANORAMA_MARK) == true) reveal()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Solo cuenta el fallo de la página, no el de una imagen suelta.
                if (!request.isForMainFrame) return
                binding.cover.isVisible = false
                view.isVisible = false
                binding.errorText.isVisible = true
            }
        }
        binding.webHolder.addView(
            web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        return web
    }

    private companion object {
        const val BLANK = "about:blank"
        val WEB_SCHEMES = setOf("http", "https")
        const val PANORAMA_MARK = "/data="
        const val REVEAL_TIMEOUT_MS = 8_000L

        /** Pulsa "Permanecer en la versión web" en cuanto aparece; a los cinco segundos deja de buscarlo. */
        const val STAY_ON_WEB =
            "(function(){var n=0;var t=setInterval(function(){" +
                "var b=document.querySelector('button[jsaction*=\"dismiss_action\"]');" +
                "if(b){b.click();clearInterval(t)}else if(++n>25){clearInterval(t)}" +
                "},200)})()"
    }
}

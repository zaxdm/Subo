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
class StreetViewPane(
    private val binding: ViewStreetPaneBinding,
    /** El usuario salió de la calle con la flecha de la propia página. */
    private val onLeftStreet: () -> Unit
) {

    private var web: WebView? = null

    /** Cambia con cada punto pedido: lo que llegue tarde de uno anterior se ignora. */
    private var request = 0

    /** La página ya enseña la calle del punto pedido. */
    private var inStreet = false

    val isOpen: Boolean get() = binding.root.isVisible

    /** Abre el panel, si no lo estaba, y enseña la calle de ese punto. */
    fun show(latitude: Double, longitude: Double) {
        val web = web ?: createWeb().also { web = it }
        val current = ++request
        inStreet = false
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
        inStreet = false
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme == CLOSE_SCHEME) {
                    onLeftStreet()
                    return true
                }
                // Ya en la calle, moverse por ella no cambia de página: lo que
                // la cambiaría son los enlaces de Google (su portada, informar
                // de un problema), que aquí no llevan a ningún sitio útil.
                return inStreet || request.url.scheme !in WEB_SCHEMES
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Antes de la calle, Google pregunta si se quiere abrir su app:
                // se responde que no, que es lo que el usuario ya eligió.
                if (url != BLANK) view.evaluateJavascript(STAY_ON_WEB, null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // La dirección de una vista de calle lleva su marca. Si estando
                // en la calle la pierde, es que se pulsó la flecha de Google, que
                // lleva a su mapa: aquí el mapa es el de abajo, así que se cierra.
                if (url == null || url == BLANK) return
                if (url.contains(PANORAMA_MARK)) {
                    inStreet = true
                    reveal()
                    view.evaluateJavascript(HOOK_BACK, null)
                } else if (inStreet) {
                    inStreet = false
                    onLeftStreet()
                }
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
        const val PANORAMA_MARK = "!1e1"
        const val REVEAL_TIMEOUT_MS = 8_000L

        const val CLOSE_SCHEME = "subo"

        /**
         * La flecha de "atrás" que Google pinta sobre la calle no hace nada
         * dentro de este panel: se le da el sentido que uno espera, cerrarlo.
         */
        const val HOOK_BACK =
            "(function(){if(window.__subo)return;window.__subo=true;" +
                "document.addEventListener('click',function(e){" +
                "var a=e.target.closest&&e.target.closest('a[href=\"\"][jsaction]');" +
                "if(a){e.preventDefault();e.stopPropagation();location.href='subo://close'}" +
                "},true)})()"

        /** Pulsa "Permanecer en la versión web" en cuanto aparece; a los cinco segundos deja de buscarlo. */
        const val STAY_ON_WEB =
            "(function(){var n=0;var t=setInterval(function(){" +
                "var b=document.querySelector('button[jsaction*=\"dismiss_action\"]');" +
                "if(b){b.click();clearInterval(t)}else if(++n>25){clearInterval(t)}" +
                "},200)})()"
    }
}

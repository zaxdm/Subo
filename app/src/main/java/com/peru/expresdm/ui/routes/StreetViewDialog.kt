package com.peru.expresdm.ui.routes

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.peru.expresdm.R
import com.peru.expresdm.databinding.DialogStreetViewBinding
import java.util.Locale

/**
 * La vista de calle de un punto del mapa, dentro de la app.
 *
 * Es la página de Google Maps en un navegador propio: no pide clave ni cuenta
 * de Google, y cerrarla devuelve al mapa en lugar de dejar al usuario en otra
 * app. Donde Google no tiene fotos de la calle, enseña su mapa de la zona.
 */
class StreetViewDialog : DialogFragment() {

    private var _binding: DialogStreetViewBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Con el tema de la app, y no uno de diálogo, ocupa toda la pantalla.
        setStyle(STYLE_NO_TITLE, R.style.Theme_Expresdm)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStreetViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }

        val web = binding.webView
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
                view.evaluateJavascript(STAY_ON_WEB, null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // La dirección de una vista de calle lleva sus datos detrás.
                if (url?.contains(PANORAMA_MARK) == true) reveal()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Solo cuenta el fallo de la página, no el de una imagen suelta.
                if (!request.isForMainFrame) return
                _binding?.apply {
                    cover.isVisible = false
                    webView.isVisible = false
                    errorText.isVisible = true
                }
            }
        }
        // Si la página de Google cambia y la pregunta no se responde sola, o si
        // ahí no hay fotos de la calle, se enseña lo que haya cargado.
        binding.cover.postDelayed(::reveal, REVEAL_TIMEOUT_MS)

        if (savedInstanceState == null) {
            val args = requireArguments()
            web.loadUrl(
                String.format(
                    Locale.US,
                    "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=%.6f,%.6f",
                    args.getDouble(ARG_LATITUDE),
                    args.getDouble(ARG_LONGITUDE)
                )
            )
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    /** Quita la cortina que tapa la página mientras carga. */
    private fun reveal() {
        _binding?.cover?.isVisible = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.webView?.saveState(outState)
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "StreetViewDialog"
        private const val ARG_LATITUDE = "latitude"
        private const val ARG_LONGITUDE = "longitude"
        private val WEB_SCHEMES = setOf("http", "https")
        private const val PANORAMA_MARK = "/data="
        private const val REVEAL_TIMEOUT_MS = 8_000L

        /** Pulsa "Permanecer en la versión web" en cuanto aparece; a los cinco segundos deja de buscarlo. */
        private const val STAY_ON_WEB =
            "(function(){var n=0;var t=setInterval(function(){" +
                "var b=document.querySelector('button[jsaction*=\"dismiss_action\"]');" +
                "if(b){b.click();clearInterval(t)}else if(++n>25){clearInterval(t)}" +
                "},200)})()"

        fun show(manager: FragmentManager, latitude: Double, longitude: Double) {
            if (manager.findFragmentByTag(TAG) != null) return
            StreetViewDialog()
                .apply { arguments = bundleOf(ARG_LATITUDE to latitude, ARG_LONGITUDE to longitude) }
                .show(manager, TAG)
        }
    }
}

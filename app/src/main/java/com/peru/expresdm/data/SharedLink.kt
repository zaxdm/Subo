package com.peru.expresdm.data

import android.net.Uri
import com.peru.expresdm.BuildConfig

/**
 * Los enlaces que se comparten: `https://<web de Subo>/ruta/<id>`.
 *
 * Quien tiene la app instalada abre la ficha de ese micro; quien no, cae en la
 * web, que le enseña la misma línea.
 */
object SharedLink {

    private const val ROUTE_SEGMENT = "ruta"

    /** El enlace a una ruta, o `null` si la app se compiló sin dirección de la web. */
    fun forRoute(routeId: String, webUrl: String = BuildConfig.WEB_URL): String? =
        webUrl.takeIf { it.isNotBlank() }?.let { "$it/$ROUTE_SEGMENT/${Uri.encode(routeId)}" }

    /** El id de la ruta a la que apunta un enlace, o `null` si el enlace es de otra cosa. */
    fun routeIdOf(link: Uri?): String? {
        val segments = link?.pathSegments ?: return null
        if (segments.size != 2 || segments[0] != ROUTE_SEGMENT) return null
        return segments[1].takeIf { it.isNotBlank() }
    }
}

package com.peru.expresdm

import android.net.Uri
import com.peru.expresdm.data.SharedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedLinkTest {

    @Test
    fun `el enlace de una ruta se lee de vuelta`() {
        val link = SharedLink.forRoute("usuario-12 ab", "https://subo.pe")
        assertEquals("https://subo.pe/ruta/usuario-12%20ab", link)
        assertEquals("usuario-12 ab", SharedLink.routeIdOf(Uri.parse(link)))
    }

    @Test
    fun `sin direccion de la web no hay enlace`() {
        assertNull(SharedLink.forRoute("california", ""))
    }

    @Test
    fun `un enlace a otra cosa no es una ruta`() {
        assertNull(SharedLink.routeIdOf(null))
        assertNull(SharedLink.routeIdOf(Uri.parse("https://subo.pe/")))
        assertNull(SharedLink.routeIdOf(Uri.parse("https://subo.pe/ruta/")))
        assertNull(SharedLink.routeIdOf(Uri.parse("https://subo.pe/privacidad/california")))
        assertNull(SharedLink.routeIdOf(Uri.parse("https://subo.pe/ruta/california/extra")))
    }
}

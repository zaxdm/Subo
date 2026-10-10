package com.peru.expresdm

import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.StreetPath
import com.peru.expresdm.data.StreetRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** org.json es parte de Android: estas pruebas necesitan Robolectric. */
@RunWith(RobolectricTestRunner::class)
class StreetRouterTest {

    private val plaza = GeoCoordinate(-8.1120, -79.0288)
    private val espana = GeoCoordinate(-8.1132, -79.0330)

    @Test
    fun `la url pone longitud antes que latitud`() {
        val url = StreetRouter.buildUrl(StreetRouter.PROFILE_FOOT, listOf(plaza, espana))
        assertTrue(url, url.contains("/routed-foot/route/v1/driving/-79.028800,-8.112000;-79.033000,-8.113200?"))
    }

    @Test
    fun `la url usa punto decimal aunque el telefono use coma`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale("es", "ES"))
        try {
            val url = StreetRouter.buildUrl(StreetRouter.PROFILE_CAR, listOf(plaza, espana))
            assertTrue(url, url.contains("-79.028800,-8.112000"))
            assertTrue(url, url.contains("/routed-car/"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `lee el camino de una respuesta de OSRM`() {
        val json = """
            {"code":"Ok","routes":[{"geometry":{"type":"LineString","coordinates":
            [[-79.0299,-8.1149],[-79.0310,-8.1140],[-79.0330,-8.1132]]},
            "distance":543.2,"duration":434.1}]}
        """.trimIndent()

        val path = StreetRouter.parseRoute(json)!!

        assertEquals(3, path.points.size)
        assertEquals(-8.1149, path.points.first().latitude, 1e-9)
        assertEquals(-79.0299, path.points.first().longitude, 1e-9)
        assertEquals(543.2, path.meters, 1e-9)
        assertEquals(434.1, path.seconds, 1e-9)
    }

    @Test
    fun `sin ruta posible no devuelve camino`() {
        assertNull(StreetRouter.parseRoute("""{"code":"NoRoute","routes":[]}"""))
    }

    @Test
    fun `una respuesta ilegible no rompe la app`() {
        assertNull(StreetRouter.parseRoute("<html>503 Service Unavailable</html>"))
    }

    @Test
    fun `un camino de un solo punto no sirve para dibujar`() {
        val json = """{"code":"Ok","routes":[{"geometry":{"coordinates":[[-79.03,-8.11]]},"distance":0,"duration":0}]}"""
        assertNull(StreetRouter.parseRoute(json))
    }

    @Test
    fun `la url de ajuste a calles usa el servicio match con un radio por punto`() {
        val url = StreetRouter.buildMatchUrl(listOf(plaza, espana, plaza))
        assertTrue(url, url.contains("/routed-car/match/v1/driving/-79.028800,-8.112000;-79.033000,-8.113200;"))
        assertTrue(url, url.contains("radiuses=30;30;30"))
        assertTrue(url, url.contains("gaps=ignore"))
    }

    @Test
    fun `une en orden los tramos de una respuesta de match`() {
        val json = """
            {"code":"Ok","matchings":[
            {"geometry":{"coordinates":[[-79.030,-8.110],[-79.031,-8.111]]},"distance":150.0,"duration":20.0},
            {"geometry":{"coordinates":[[-79.032,-8.112],[-79.033,-8.113]]},"distance":160.0,"duration":22.0}]}
        """.trimIndent()

        val path = StreetRouter.parseMatch(json)!!

        assertEquals(4, path.points.size)
        assertEquals(-8.110, path.points.first().latitude, 1e-9)
        assertEquals(-79.033, path.points.last().longitude, 1e-9)
        assertEquals(310.0, path.meters, 1e-9)
    }

    @Test
    fun `sin ajuste posible no devuelve camino`() {
        assertNull(StreetRouter.parseMatch("""{"code":"NoMatch","message":"Could not match the trace."}"""))
    }

    @Test
    fun `un camino guardado en cache se recupera igual`() {
        val original = StreetPath(listOf(plaza, espana), meters = 480.0, seconds = 390.0)
        assertEquals(original, StreetRouter.decode(StreetRouter.encode(original)))
    }
}

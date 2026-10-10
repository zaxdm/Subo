package com.peru.expresdm

import com.peru.expresdm.data.AlertRepository
import com.peru.expresdm.data.AlertType
import com.peru.expresdm.data.AlertVote
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.GeoCoordinate
import com.peru.expresdm.data.PlaceSearch
import com.peru.expresdm.data.RecordingDraft
import com.peru.expresdm.data.RecordingDraftStore
import com.peru.expresdm.data.RecordingPoint
import com.peru.expresdm.data.RoadAlert
import com.peru.expresdm.data.Stop
import com.peru.expresdm.data.UserRouteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Lo que se guarda en el teléfono y lo que llega de los servicios de mapas.
 * org.json es parte de Android: estas pruebas necesitan Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class LocalStoresTest {

    // --- Rutas grabadas por el usuario ---

    @Test
    fun `una ruta grabada se guarda y se recupera con su trazado`() {
        val route = BusRoute(
            id = "usuario-1",
            letter = "N",
            name = "Nueva Línea",
            origin = "Av. España",
            destination = "Huanchaco",
            durationMinutes = 34,
            fare = 1.5,
            schedule = "06:00 – 21:00",
            stops = listOf(Stop("Av. España", -8.1132, -79.0330), Stop("Huanchaco", -8.0797, -79.1187)),
            accentIndex = 6,
            path = listOf(GeoCoordinate(-8.1132, -79.0330), GeoCoordinate(-8.10, -79.05), GeoCoordinate(-8.0797, -79.1187)),
            pathSnapped = true,
            vehicleName = "Micro California",
            imageUrl = "https://example.com/micro.jpg",
            description = "Verde con franja blanca",
            bidirectional = false
        )

        val restored = UserRouteStore.decodeRoutes(UserRouteStore.encodeRoutes(listOf(route))).single()

        assertEquals(route.copy(createdByUser = true), restored)
    }

    @Test
    fun `una ruta guardada con el gps en bruto se limpia al leerla`() {
        // 30 puntos cada 20 m hacia el sur, zigzagueando ±8 m: como se guardaban antes.
        val zigzag = (0 until 30).map { i ->
            GeoCoordinate(-8.11 - i * 0.00018, -79.03 + if (i % 2 == 0) 0.00007 else -0.00007)
        }
        val route = BusRoute(
            "usuario-viejo", "V", "Vieja", "A", "B", 10, 1.0, "",
            listOf(Stop("A", zigzag.first().latitude, zigzag.first().longitude), Stop("B", zigzag.last().latitude, zigzag.last().longitude)),
            0,
            path = zigzag
        )
        val oldFormat = UserRouteStore.encodeRoutes(listOf(route)).replace("\"pathVersion\":2,", "")

        val restored = UserRouteStore.decodeRoutes(oldFormat).single()

        assertTrue("quedaron ${restored.path!!.size} puntos", restored.path!!.size < zigzag.size / 2)
    }

    @Test
    fun `una ruta danada no borra las demas`() {
        val good = UserRouteStore.encodeRoutes(
            listOf(
                BusRoute("ok", "O", "Buena", "A", "B", 10, 1.0, "", listOf(Stop("A", 0.0, 0.0), Stop("B", 0.1, 0.1)), 0)
            )
        )
        val mixed = good.replaceFirst("[", """[{"id":"rota"},""")

        assertEquals(listOf("ok"), UserRouteStore.decodeRoutes(mixed).map { it.id })
    }

    // --- Grabación en curso ---

    @Test
    fun `la grabacion en curso se recupera tal cual`() {
        val draft = RecordingDraft(
            startedAt = 1_000L,
            points = listOf(RecordingPoint(-8.11, -79.03, 1_000L, accuracy = 6.5f), RecordingPoint(-8.12, -79.04, 11_000L))
        )
        assertEquals(draft, RecordingDraftStore.decode(RecordingDraftStore.encode(draft)))
    }

    @Test
    fun `una grabacion de la version anterior sin precision se sigue leyendo`() {
        val draft = RecordingDraftStore.decode("""{"startedAt":1000,"points":[[-8.11,-79.03,1000]]}""")!!
        assertEquals(RecordingPoint(-8.11, -79.03, 1_000L, accuracy = null), draft.points.single())
    }

    @Test
    fun `un archivo de grabacion ilegible no rompe la app`() {
        assertNull(RecordingDraftStore.decode("{roto"))
    }

    // --- Alertas ---

    @Test
    fun `las alertas se guardan con votos y comentario`() {
        val alert = RoadAlert(
            id = "x",
            type = AlertType.ROADWORK,
            latitude = -8.1,
            longitude = -79.03,
            placeName = null,
            comment = "Desvío por Av. España",
            createdAt = 5_000L,
            confirmations = 2,
            myVote = AlertVote.STILL_THERE,
            createdByMe = true
        )
        assertEquals(listOf(alert), AlertRepository.decode(AlertRepository.encode(listOf(alert))))
    }

    @Test
    fun `una alerta de un tipo desconocido se ignora`() {
        val json = """[{"id":"z","type":"METEORITO","lat":0,"lon":0,"createdAt":1}]"""
        assertTrue(AlertRepository.decode(json).isEmpty())
    }

    // --- Buscador de lugares ---

    @Test
    fun `la busqueda se limita a Trujillo y codifica el texto`() {
        val url = PlaceSearch.buildSearchUrl(" Hospital Belén ")
        assertTrue(url, url.contains("bounded=1"))
        assertTrue(url, url.contains("viewbox=${PlaceSearch.TRUJILLO_VIEWBOX}"))
        assertTrue(url, url.endsWith("q=Hospital+Bel%C3%A9n"))
    }

    @Test
    fun `lee los lugares y resume su direccion`() {
        val json = """[
            {"name":"Centro Comercial Real Plaza","lat":"-8.1322629","lon":"-79.0312901",
             "display_name":"Centro Comercial Real Plaza, Avenida Prolongación César Vallejo, Trujillo, La Libertad, 13006, Perú"}
        ]"""
        val place = PlaceSearch.parseSearch(json).single()

        assertEquals("Centro Comercial Real Plaza", place.name)
        assertEquals("Avenida Prolongación César Vallejo", place.detail)
        assertEquals(-8.1322629, place.latitude, 1e-9)
        assertEquals(-79.0312901, place.longitude, 1e-9)
    }

    @Test
    fun `un lugar sin nombre usa el principio de su direccion`() {
        val json = """[{"name":"","lat":"-8.11","lon":"-79.02","display_name":"Jirón Pizarro, Centro Histórico, Trujillo, Perú"}]"""
        assertEquals("Jirón Pizarro", PlaceSearch.parseSearch(json).single().name)
    }

    @Test
    fun `la posicion se traduce a la calle`() {
        val json = """{"name":"","address":{"road":"Calle Torre Tagle","suburb":"El Recreo"}}"""
        assertEquals("Calle Torre Tagle", PlaceSearch.parseReverse(json))
    }

    @Test
    fun `sin calle se usa el barrio`() {
        val json = """{"name":"","address":{"suburb":"El Recreo"}}"""
        assertEquals("El Recreo", PlaceSearch.parseReverse(json))
    }
}

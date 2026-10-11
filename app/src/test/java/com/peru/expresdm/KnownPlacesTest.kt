package com.peru.expresdm

import com.peru.expresdm.data.KnownPlaces
import com.peru.expresdm.data.UserPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Los sitios conocidos que vienen con la app: se encuentran como les dice la gente. */
@RunWith(RobolectricTestRunner::class)
class KnownPlacesTest {

    private val lugares = KnownPlaces.parse(
        """
        [{"n":"Universidad Privada Antenor Orrego","k":"Universidad","a":["UPAO"],"s":"Avenida América Sur","lat":-8.1288,"lon":-79.0323},
         {"n":"Odontologica UPAO","k":"Clínica","lat":-8.1100,"lon":-79.0300},
         {"n":"Mall Plaza Trujillo","k":"Centro comercial","lat":-8.1020,"lon":-79.0480},
         {"n":"Hospital Belén de Trujillo","k":"Hospital","a":["Hospital Belén"],"lat":-8.1140,"lon":-79.0260},
         {"n":"Iglesia Belén","k":"Iglesia","lat":-8.1150,"lon":-79.0270},
         {"n":"La Hermelinda","k":"Mercado","lat":-8.0950,"lon":-79.0200}]
        """.trimIndent()
    )

    private fun buscar(texto: String, cerca: UserPosition? = null) =
        KnownPlaces.search(lugares, texto, cerca, limit = 8).map { it.name }

    @Test
    fun `encuentra un sitio por su apodo, y el propio va antes que los que solo lo mencionan`() {
        assertEquals(listOf("Universidad Privada Antenor Orrego", "Odontologica UPAO"), buscar("upao"))
    }

    @Test
    fun `no le importan las tildes ni las mayusculas`() {
        assertEquals("Hospital Belén de Trujillo", buscar("HOSPITAL BELEN").first())
        assertEquals(listOf("Mall Plaza Trujillo"), buscar("mall plaza"))
    }

    @Test
    fun `entiende las palabras en otro orden y el tipo de sitio`() {
        assertEquals(listOf("Hospital Belén de Trujillo"), buscar("belen hospital"))
        assertEquals(listOf("La Hermelinda"), buscar("mercado hermelinda"))
    }

    @Test
    fun `a igual coincidencia sale primero lo mas buscado`() {
        // Un hospital antes que una iglesia, aunque la iglesia quede más cerca.
        val enLaIglesia = UserPosition(-8.1150, -79.0270)
        assertEquals(listOf("Hospital Belén de Trujillo", "Iglesia Belén"), buscar("belen", enLaIglesia))
    }

    @Test
    fun `dice que es cada sitio y en que calle esta`() {
        val upao = KnownPlaces.search(lugares, "upao", null, limit = 1).single()
        assertEquals("Universidad · Avenida América Sur", upao.detail)
        assertEquals(-8.1288, upao.latitude, 1e-6)
    }

    @Test
    fun `con una letra o sin coincidencias no devuelve nada`() {
        assertTrue(buscar("u").isEmpty())
        assertTrue(buscar("aeropuerto de lima").isEmpty())
    }
}

package com.peru.expresdm

import com.peru.expresdm.data.PlaceSuggest
import com.peru.expresdm.data.UserPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Las sugerencias de lugares mientras se escribe: qué se pide y cómo se lee la respuesta. */
@RunWith(RobolectricTestRunner::class)
class PlaceSuggestTest {

    private fun feature(name: String?, lat: Double, lon: Double, street: String? = null, district: String? = null) = """
        {"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},
         "properties":{${listOfNotNull(
        name?.let { "\"name\":\"$it\"" },
        street?.let { "\"street\":\"$it\"" },
        district?.let { "\"district\":\"$it\"" },
        "\"city\":\"Trujillo\""
    ).joinToString(",")}}}
    """.trimIndent()

    private fun respuesta(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test
    fun `lee el nombre, la calle y la posicion de cada lugar`() {
        val lugares = PlaceSuggest.parse(
            respuesta(feature("Centro Comercial Real Plaza", -8.1323, -79.0313, street = "Prolongación César Vallejo"))
        )

        val plaza = lugares.single()
        assertEquals("Centro Comercial Real Plaza", plaza.name)
        assertEquals("Prolongación César Vallejo, Trujillo", plaza.detail)
        assertEquals(-8.1323, plaza.latitude, 1e-6)
        assertEquals(-79.0313, plaza.longitude, 1e-6)
    }

    @Test
    fun `una calle sin nombre propio se llama como la calle`() {
        val lugares = PlaceSuggest.parse(respuesta(feature(null, -8.1275, -79.0413, street = "Avenida Fátima")))
        assertEquals("Avenida Fátima", lugares.single().name)
        // El nombre no se repite en el detalle.
        assertEquals("Trujillo", lugares.single().detail)
    }

    @Test
    fun `no repite el mismo sitio, pero si dos sitios distintos con el mismo nombre`() {
        val lugares = PlaceSuggest.parse(
            respuesta(
                feature("Avenida Fátima", -8.1275, -79.0413),
                // A unos 50 m: es otro tramo de la misma avenida.
                feature("Avenida Fátima", -8.1279, -79.0415),
                // A más de un kilómetro, en otro distrito: es otra.
                feature("Avenida Fátima", -8.1325, -79.0328, district = "Víctor Larco Herrera")
            )
        )
        assertEquals(2, lugares.size)
        assertEquals("Víctor Larco Herrera, Trujillo", lugares.last().detail)
    }

    @Test
    fun `una respuesta rota o vacia no rompe la busqueda`() {
        assertTrue(PlaceSuggest.parse("esto no es json").isEmpty())
        assertTrue(PlaceSuggest.parse("{}").isEmpty())
        assertTrue(PlaceSuggest.parse(respuesta()).isEmpty())
    }

    @Test
    fun `pide solo lugares de Trujillo y prefiere los cercanos al usuario`() {
        val url = PlaceSuggest.buildUrl(" real plaza ", UserPosition(-8.112, -79.0288))
        assertTrue(url, url.contains("q=real+plaza"))
        assertTrue(url, url.contains("bbox=-79.20,-8.25,-78.90,-7.95"))
        assertTrue(url, url.contains("lat=-8.11200&lon=-79.02880"))
        // Sin ubicación no se inventa una.
        assertTrue(!PlaceSuggest.buildUrl("upao", null).contains("lat="))
    }
}

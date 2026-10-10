package com.peru.expresdm

import com.peru.expresdm.data.FrequentRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentRoutesTest {

    @Test
    fun `sin uso no hay favoritas`() {
        assertTrue(FrequentRoutes.rank(emptyMap()).isEmpty())
    }

    @Test
    fun `una sola consulta no basta para ser favorita`() {
        assertTrue(FrequentRoutes.rank(mapOf("micro-202" to 1)).isEmpty())
    }

    @Test
    fun `con el minimo de usos la ruta pasa a favorita`() {
        assertEquals(listOf("micro-202"), FrequentRoutes.rank(mapOf("micro-202" to FrequentRoutes.MIN_USES)))
    }

    @Test
    fun `ordena de la mas usada a la menos`() {
        val ranked = FrequentRoutes.rank(
            mapOf("linea-303" to 2, "micro-202" to 9, "huanchaco-express" to 4)
        )
        assertEquals(listOf("micro-202", "huanchaco-express", "linea-303"), ranked)
    }

    @Test
    fun `a igual uso el orden es estable`() {
        val uses = mapOf("linea-ucv" to 3, "california-porvenir" to 3, "micro-202" to 3)
        assertEquals(listOf("california-porvenir", "linea-ucv", "micro-202"), FrequentRoutes.rank(uses))
    }

    @Test
    fun `nunca devuelve mas del maximo`() {
        val uses = (1..10).associate { "ruta-$it" to it + 5 }
        assertEquals(FrequentRoutes.MAX_ROUTES, FrequentRoutes.rank(uses).size)
    }
}

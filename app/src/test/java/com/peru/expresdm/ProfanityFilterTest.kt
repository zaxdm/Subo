package com.peru.expresdm

import com.peru.expresdm.ai.ProfanityFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del filtro local de moderación.
 *
 * Es la primera barrera y la única que funciona sin conexión, así que conviene
 * tenerla bien cubierta. La segunda barrera (Gemini) no se prueba aquí: no
 * tiene sentido llamar a un servicio de pago en cada compilación.
 */
class ProfanityFilterTest {

    @Test
    fun `deja pasar un reporte normal`() {
        assertTrue(ProfanityFilter.isClean("El micro 202 viene con 15 minutos de retraso."))
    }

    @Test
    fun `deja pasar una queja fuerte pero sin insultos`() {
        assertTrue(
            ProfanityFilter.isClean(
                "Pésimo servicio, el conductor maneja fatal y nunca respeta los paraderos."
            )
        )
    }

    @Test
    fun `rechaza un insulto directo`() {
        assertEquals("idiota", ProfanityFilter.firstMatch("El chofer es un idiota"))
    }

    @Test
    fun `rechaza un insulto escrito con tildes y mayusculas`() {
        assertNotNull(ProfanityFilter.firstMatch("QUE IMBÉCIL este conductor"))
    }

    @Test
    fun `rechaza un insulto pegado a signos de puntuacion`() {
        assertNotNull(ProfanityFilter.firstMatch("¡Mierda! otra vez lleno"))
    }

    @Test
    fun `rechaza variantes alargadas de un insulto`() {
        assertNotNull(ProfanityFilter.firstMatch("ese huevonazo se pasó el paradero"))
        assertNotNull(ProfanityFilter.firstMatch("conchatumadres todos"))
    }

    @Test
    fun `no confunde palabras corrientes que contienen fragmentos`() {
        // Estas contienen trozos de insultos pero son palabras legítimas.
        val inocentes = listOf(
            "El servicio es ágil y hay un vigilante en el paradero",
            "La combi pasó por la avenida principal",
            "Hay un control de pasajes en la esquina"
        )
        inocentes.forEach { texto ->
            assertTrue("marcó como ofensivo: $texto", ProfanityFilter.isClean(texto))
        }
    }

    @Test
    fun `un texto vacio esta limpio`() {
        assertTrue(ProfanityFilter.isClean(""))
        assertTrue(ProfanityFilter.isClean("   "))
    }

    @Test
    fun `detecta abreviaturas ofensivas`() {
        assertFalse(ProfanityFilter.isClean("ctm que lento"))
    }
}

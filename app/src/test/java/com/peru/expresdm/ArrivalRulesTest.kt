package com.peru.expresdm

import com.peru.expresdm.data.ArrivalRules
import com.peru.expresdm.data.ArrivalRules.Reading
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalRulesTest {

    @Test
    fun `lejos de la parada todavia no avisa`() {
        assertEquals(Reading.APPROACHING, ArrivalRules.evaluate(1_200.0, 10f))
    }

    @Test
    fun `dentro del radio avisa`() {
        assertEquals(Reading.ARRIVED, ArrivalRules.evaluate(200.0, 15f))
    }

    @Test
    fun `sin precision conocida decide por la distancia`() {
        assertEquals(Reading.ARRIVED, ArrivalRules.evaluate(240.0, null))
        assertEquals(Reading.APPROACHING, ArrivalRules.evaluate(260.0, null))
    }

    @Test
    fun `una lectura muy imprecisa no dispara el aviso`() {
        // A 200 m pero con 400 m de error: podría estar a medio kilómetro.
        assertEquals(Reading.IMPRECISE, ArrivalRules.evaluate(200.0, 400f))
    }

    @Test
    fun `aunque sea imprecisa, si con todo el error sigue dentro avisa`() {
        assertEquals(Reading.ARRIVED, ArrivalRules.evaluate(20.0, 150f))
    }
}

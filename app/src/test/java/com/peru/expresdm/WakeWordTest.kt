package com.peru.expresdm

import com.peru.expresdm.voice.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordTest {

    @Test
    fun `solo la palabra abre el asistente sin pregunta`() {
        assertEquals("", WakeWord.match("Subo"))
        assertEquals("", WakeWord.match("  oye subo  "))
        assertEquals("", WakeWord.match("¡Subo!"))
    }

    @Test
    fun `lo que se dice despues es la pregunta`() {
        assertEquals("qué micro va al centro", WakeWord.match("Subo, ¿qué micro va al centro?".trimEnd('?')))
        assertEquals("cómo llego a Huanchaco", WakeWord.match("oye Subo cómo llego a Huanchaco"))
    }

    @Test
    fun `entiende como suele escribirlo el telefono`() {
        assertEquals("llévame a la UNT", WakeWord.match("su vo llévame a la UNT"))
        assertEquals("", WakeWord.match("Zubo"))
        assertEquals("", WakeWord.match("Súbo."))
        assertEquals("llévame al centro", WakeWord.match("Tubo llévame al centro"))
        assertEquals("", WakeWord.match("hola su voz"))
    }

    @Test
    fun `una frase cualquiera no lo llama`() {
        assertNull(WakeWord.match("ahí me subo yo"))
        assertNull(WakeWord.match("qué micro va al centro"))
        assertNull(WakeWord.match("oye, ¿me pasas la sal?"))
        assertNull(WakeWord.match(""))
    }
}

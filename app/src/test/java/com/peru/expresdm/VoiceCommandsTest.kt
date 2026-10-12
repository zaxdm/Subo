package com.peru.expresdm

import com.peru.expresdm.voice.VoiceCommand
import com.peru.expresdm.voice.VoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun `pedir ir a un sitio se queda con el sitio tal como se dijo`() {
        assertEquals(VoiceCommand.GoTo("Huanchaco"), VoiceCommands.parse("Llévame a Huanchaco"))
        assertEquals(VoiceCommand.GoTo("Mall Plaza"), VoiceCommands.parse("quiero ir al Mall Plaza."))
        assertEquals(VoiceCommand.GoTo("casa"), VoiceCommands.parse("vamos a casa"))
        assertEquals(VoiceCommand.GoTo("la Avenida España"), VoiceCommands.parse("Llévame a la Avenida España"))
    }

    @Test
    fun `el aviso de llegada se enciende y se apaga`() {
        assertEquals(VoiceCommand.ArrivalOn, VoiceCommands.parse("Avísame al llegar"))
        assertEquals(VoiceCommand.ArrivalOn, VoiceCommands.parse("avísame"))
        assertEquals(VoiceCommand.ArrivalOn, VoiceCommands.parse("Guíame"))
        assertEquals(VoiceCommand.ArrivalOff, VoiceCommands.parse("quita el aviso"))
        assertEquals(VoiceCommand.ArrivalOff, VoiceCommands.parse("Ya no me avises, por favor"))
    }

    @Test
    fun `compartir y dejar de compartir no se confunden`() {
        assertEquals(VoiceCommand.ShareTrip, VoiceCommands.parse("Comparte mi viaje"))
        assertEquals(VoiceCommand.StopSharing, VoiceCommands.parse("deja de compartir mi viaje"))
    }

    @Test
    fun `preguntar donde esta y mandar callar`() {
        assertEquals(VoiceCommand.WhereAmI, VoiceCommands.parse("¿Dónde estoy?"))
        assertEquals(VoiceCommand.Close, VoiceCommands.parse("Gracias"))
        assertEquals(VoiceCommand.Close, VoiceCommands.parse("eso es todo"))
    }

    @Test
    fun `lo demas es una pregunta para el asistente`() {
        assertEquals(VoiceCommand.Ask("¿Qué micro va al centro?"), VoiceCommands.parse("¿Qué micro va al centro?"))
        // Empieza como una orden de callar, pero es una pregunta.
        assertEquals(
            VoiceCommand.Ask("para ir al centro qué micro tomo"),
            VoiceCommands.parse("para ir al centro qué micro tomo")
        )
        assertEquals(VoiceCommand.Ask("cuánto cuesta el pasaje"), VoiceCommands.parse("cuánto cuesta el pasaje"))
        assertEquals(VoiceCommand.Ask(""), VoiceCommands.parse(""))
    }

    @Test
    fun `al preguntar a donde va un lugar a secas es el destino`() {
        assertEquals(VoiceCommand.GoTo("Huanchaco"), VoiceCommands.parseDestination("Huanchaco"))
        assertEquals(VoiceCommand.GoTo("Mall Plaza"), VoiceCommands.parseDestination("al Mall Plaza."))
        assertEquals(VoiceCommand.GoTo("la Avenida España"), VoiceCommands.parseDestination("A la Avenida España"))
        assertEquals(VoiceCommand.GoTo("casa"), VoiceCommands.parseDestination("llévame a casa"))
    }

    @Test
    fun `al preguntar a donde va una pregunta sigue siendo pregunta y una orden orden`() {
        assertEquals(
            VoiceCommand.Ask("qué micro va al centro"),
            VoiceCommands.parseDestination("qué micro va al centro")
        )
        assertEquals(VoiceCommand.Ask("¿Hay paro hoy?"), VoiceCommands.parseDestination("¿Hay paro hoy?"))
        assertEquals(VoiceCommand.Close, VoiceCommands.parseDestination("gracias"))
        assertEquals(VoiceCommand.WhereAmI, VoiceCommands.parseDestination("dónde estoy"))
        assertEquals(VoiceCommand.Ask(""), VoiceCommands.parseDestination(""))
    }
}

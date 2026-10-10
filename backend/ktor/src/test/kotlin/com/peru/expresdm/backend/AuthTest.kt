package com.peru.expresdm.backend

import com.peru.expresdm.backend.auth.DisplayNameProblem
import com.peru.expresdm.backend.auth.DisplayNames
import com.peru.expresdm.backend.auth.Passwords
import com.peru.expresdm.backend.auth.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTest {

    @Test
    fun `la contrasena no se guarda tal cual y se puede comprobar`() {
        val hash = Passwords.hash("micro-2026")
        assertFalse(hash.contains("micro-2026"))
        assertTrue(Passwords.matches("micro-2026", hash))
        assertFalse(Passwords.matches("otra-cosa", hash))
    }

    @Test
    fun `una contrasena corta no vale`() {
        assertNotNull(Passwords.problem("1234"))
        assertNull(Passwords.problem("micro-2026"))
    }

    @Test
    fun `el nombre se guarda sin espacios de sobra`() {
        assertEquals("Ana Maria", DisplayNames.clean("  Ana   Maria "))
        assertNull(DisplayNames.problem("Ana Maria"))
    }

    @Test
    fun `un nombre vacio o larguisimo no vale`() {
        assertEquals(DisplayNameProblem.EMPTY, DisplayNames.problem("   "))
        assertEquals(DisplayNameProblem.TOO_LONG, DisplayNames.problem("a".repeat(DisplayNames.MAX_LENGTH + 1)))
    }

    @Test
    fun `un token propio identifica al usuario`() {
        val tokens = Tokens("secreto-de-prueba-muy-largo")
        val token = tokens.issue("usuario-1", "Ana")
        assertEquals("usuario-1", tokens.userIdOf(token))
    }

    @Test
    fun `un token firmado con otro secreto no vale`() {
        val token = Tokens("secreto-de-prueba-muy-largo").issue("usuario-1", "Ana")
        assertNull(Tokens("otro-secreto-distinto").userIdOf(token))
    }

    @Test
    fun `un token caducado no vale`() {
        val tokens = Tokens("secreto-de-prueba-muy-largo")
        val viejo = tokens.issue("usuario-1", "Ana", now = System.currentTimeMillis() - Tokens.LIFETIME_MS - 1_000)
        assertNull(tokens.userIdOf(viejo))
    }

    @Test
    fun `un texto cualquiera no es un token`() {
        assertNull(Tokens("secreto-de-prueba-muy-largo").userIdOf("esto-no-es-un-token"))
    }
}

package com.peru.expresdm

import com.peru.expresdm.data.RouteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {

    @Test
    fun `el catalogo no esta vacio y cada ruta tiene paradas`() {
        val routes = RouteRepository.all()
        assertTrue(routes.isNotEmpty())
        routes.forEach { route ->
            assertTrue("${route.name} debe tener al menos 2 paradas", route.stops.size >= 2)
        }
    }

    @Test
    fun `los identificadores de ruta son unicos`() {
        val ids = RouteRepository.all().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `una busqueda vacia devuelve todas las rutas`() {
        assertEquals(RouteRepository.all().size, RouteRepository.search("").size)
    }

    @Test
    fun `la busqueda ignora mayusculas y encuentra por destino`() {
        val results = RouteRepository.search("HUANCHACO")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.matches("huanchaco") })
    }

    @Test
    fun `la busqueda tambien encuentra por nombre de parada`() {
        val results = RouteRepository.search("Plaza de Armas")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `una busqueda sin coincidencias devuelve lista vacia`() {
        assertTrue(RouteRepository.search("zzz-no-existe").isEmpty())
    }

    @Test
    fun `byId devuelve la ruta correcta y null si no existe`() {
        val first = RouteRepository.all().first()
        assertNotNull(RouteRepository.byId(first.id))
        assertEquals(first.name, RouteRepository.byId(first.id)?.name)
        assertNull(RouteRepository.byId("ruta-inventada"))
    }

    @Test
    fun `el subtitulo combina origen y destino`() {
        val route = RouteRepository.all().first()
        assertEquals("${route.origin} — ${route.destination}", route.subtitle)
    }
}

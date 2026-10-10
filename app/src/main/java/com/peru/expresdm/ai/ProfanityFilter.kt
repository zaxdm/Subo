package com.peru.expresdm.ai

import java.text.Normalizer

/**
 * Filtro local de lenguaje ofensivo.
 *
 * Es la primera barrera de la moderación: instantáneo, gratuito y disponible
 * sin conexión. Atrapa lo evidente; lo sutil queda para [ModerationService],
 * que consulta al modelo.
 *
 * No depende de Android ni de la red, así que se puede probar directamente.
 */
object ProfanityFilter {

    /** Insultos frecuentes en español peruano, sin tildes y en minúsculas. */
    private val bannedWords = setOf(
        "concha", "conchatumadre", "conchasumadre", "ctm", "csm", "mierda",
        "puta", "puto", "cabron", "cabrona", "huevon", "huevona", "hdp",
        "imbecil", "idiota", "estupido", "estupida", "pendejo", "pendeja",
        "maricon", "marica", "cojudo", "cojuda", "carajo", "verga",
        "pelotudo", "malparido", "chucha", "zorra", "mrd"
    )

    /**
     * Raíces que se buscan también dentro de otras palabras, para atrapar
     * variantes como "conchatumadres" o "huevonazo".
     *
     * Solo raíces largas y unívocas: buscar fragmentos cortos daría falsos
     * positivos con palabras corrientes.
     */
    private val bannedRoots = listOf(
        "conchatumadre", "conchasumadre", "huevon", "imbecil", "cojudo",
        "pendejo", "maricon", "malparido", "pelotudo"
    )

    /** Primera palabra ofensiva encontrada, o `null` si el texto está limpio. */
    fun firstMatch(text: String): String? {
        val words = tokenize(text)

        words.firstOrNull { it in bannedWords }?.let { return it }

        return words.firstOrNull { word ->
            bannedRoots.any { root -> word.contains(root) }
        }
    }

    fun isClean(text: String): Boolean = firstMatch(text) == null

    /**
     * Parte el texto en palabras normalizadas: minúsculas, sin tildes y sin
     * signos, de modo que "¡IMBÉCIL!" y "imbecil" sean lo mismo.
     */
    private fun tokenize(text: String): List<String> =
        normalize(text)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}

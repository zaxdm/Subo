package com.peru.expresdm.data

/** Una parada del recorrido, con su posición geográfica. */
data class Stop(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Ruta de transporte público.
 *
 * [letter] es la insignia que se muestra en el listado (A, B, H...).
 * [accentIndex] elige el color del badge de forma estable, sin guardar
 * colores en el modelo de datos.
 */
data class BusRoute(
    val id: String,
    val letter: String,
    val name: String,
    val origin: String,
    val destination: String,
    val durationMinutes: Int,
    val fare: Double,
    val schedule: String,
    val stops: List<Stop>,
    val accentIndex: Int,
    /**
     * Trazado real por las calles, cuando la ruta la grabó un usuario yendo en
     * el micro. Las rutas del catálogo no lo traen y se calcula por calles.
     */
    val path: List<GeoCoordinate>? = null,
    /** El trazado grabado ya se ajustó a las calles; si no, se intenta al mostrarlo. */
    val pathSnapped: Boolean = false,
    val createdByUser: Boolean = false,
    val vehicleName: String = "",
    val imageUrl: String? = null,
    val description: String = "",
    val bidirectional: Boolean = true,
    /** Pasajes reducidos y cada cuánto pasa; `null` mientras nadie los cargue en el panel. */
    val fareSchool: Double? = null,
    val fareUniversity: Double? = null,
    val frequencyMinutes: Int? = null
) {
    val subtitle: String get() = "$origin — $destination"

    /** Texto sobre el que filtra la búsqueda por nombre. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            origin.lowercase().contains(q) ||
            destination.lowercase().contains(q) ||
            letter.lowercase() == q ||
            stops.any { it.name.lowercase().contains(q) }
    }
}

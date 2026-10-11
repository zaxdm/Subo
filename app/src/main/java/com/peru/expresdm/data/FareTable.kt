package com.peru.expresdm.data

/**
 * Lo que paga cada tipo de pasajero en una ruta.
 *
 * El escolar y el universitario los carga el administrador en el panel. Si
 * todavía no están, quedan en `null` y la ficha no los enseña: un pasaje
 * inventado es peor que uno que falta.
 */
data class FareTable(
    val school: Double?,
    val university: Double?,
    val regular: Double
) {
    /** Falta alguno de los pasajes reducidos. */
    val isIncomplete: Boolean get() = school == null || university == null

    companion object {
        fun of(route: BusRoute): FareTable = FareTable(
            school = route.fareSchool,
            university = route.fareUniversity,
            regular = route.fare
        )
    }
}

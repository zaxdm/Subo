package com.peru.expresdm.data

/** Lo que paga cada tipo de pasajero en una ruta. */
data class FareTable(
    val school: Double,
    val university: Double,
    val regular: Double
) {
    companion object {
        private const val SCHOOL_SHARE = 0.25
        private const val UNIVERSITY_SHARE = 0.5

        /**
         * El catálogo solo guarda el pasaje regular. El escolar y el
         * universitario se calculan como una parte de él, así que son
         * referenciales: el día que el catálogo los traiga, salen de ahí.
         */
        fun of(route: BusRoute): FareTable = FareTable(
            school = route.fare * SCHOOL_SHARE,
            university = route.fare * UNIVERSITY_SHARE,
            regular = route.fare
        )
    }
}

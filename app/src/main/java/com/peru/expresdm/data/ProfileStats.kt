package com.peru.expresdm.data

/**
 * Lo que el usuario lleva hecho en la app, para el resumen del perfil.
 *
 * Son cuentas de lo que hay ahora mismo, no un historial: las alertas caducan y
 * dejan de contarse, y una ruta borrada deja de sumar. Lógica pura, sin
 * Android, para poder probarla directamente.
 */
data class ProfileStats(
    /** Rutas que el usuario grabó en este teléfono. */
    val recordedRoutes: Int,
    /** Alertas suyas que siguen vigentes. */
    val activeAlerts: Int,
    /** Rutas distintas que ha consultado alguna vez. */
    val usedRoutes: Int
) {
    val isEmpty: Boolean get() = recordedRoutes == 0 && activeAlerts == 0 && usedRoutes == 0

    companion object {
        val EMPTY = ProfileStats(0, 0, 0)

        fun of(
            routes: List<BusRoute>,
            alerts: List<RoadAlert>,
            uses: Map<String, Int>,
            now: Long
        ): ProfileStats {
            val knownIds = routes.map { it.id }.toSet()
            return ProfileStats(
                recordedRoutes = routes.count { it.createdByUser },
                activeAlerts = AlertRules.active(alerts, now).count { it.createdByMe },
                // Una ruta que ya no está en el catálogo no debería seguir
                // contando como "usada": el número quedaría sin nada detrás.
                usedRoutes = uses.count { (id, count) -> count > 0 && id in knownIds }
            )
        }
    }
}

package com.peru.expresdm.backend.store

import com.peru.expresdm.backend.model.RouteDto
import com.peru.expresdm.backend.model.StopDto

/**
 * Las rutas con las que arranca el servidor: las mismas que traía la app.
 *
 * Son aproximadas y están para que la app no aparezca vacía. La idea es que se
 * vayan reemplazando por recorridos grabados de verdad.
 */
object Catalog {

    val ROUTES: List<RouteDto> = listOf(
        RouteDto(
            id = "california-porvenir",
            letter = "A",
            name = "California",
            origin = "El Porvenir",
            destination = "Huanchaco",
            durationMinutes = 38,
            fare = 2.00,
            schedule = "05:00 – 22:30",
            accentIndex = 0,
            stops = listOf(
                StopDto("El Porvenir", -8.0806, -78.9950),
                StopDto("Av. Pumacahua", -8.0921, -79.0090),
                StopDto("Av. América Sur", -8.1085, -79.0245),
                StopDto("Plaza de Armas", -8.1120, -79.0288),
                StopDto("Av. Mansiche", -8.1024, -79.0389),
                StopDto("Óvalo Huanchaco", -8.0932, -79.0780),
                StopDto("Huanchaco", -8.0797, -79.1187)
            )
        ),
        RouteDto(
            id = "california-esperanza",
            letter = "B",
            name = "California",
            origin = "La Esperanza",
            destination = "Buenos Aires",
            durationMinutes = 42,
            fare = 2.00,
            schedule = "05:15 – 22:00",
            accentIndex = 1,
            stops = listOf(
                StopDto("La Esperanza", -8.0704, -79.0470),
                StopDto("Av. Condorcanqui", -8.0839, -79.0432),
                StopDto("Av. Mansiche", -8.1024, -79.0389),
                StopDto("Av. España", -8.1132, -79.0330),
                StopDto("Av. Larco", -8.1226, -79.0332),
                StopDto("Buenos Aires", -8.1339, -79.0490)
            )
        ),
        RouteDto(
            id = "huanchaco-express",
            letter = "H",
            name = "Huanchaco Express",
            origin = "Centro Cívico",
            destination = "Huanchaco",
            durationMinutes = 30,
            fare = 2.50,
            schedule = "06:00 – 21:00",
            accentIndex = 2,
            stops = listOf(
                StopDto("Centro Cívico", -8.1108, -79.0301),
                StopDto("Av. Mansiche", -8.1024, -79.0389),
                StopDto("Óvalo Huanchaco", -8.0932, -79.0780),
                StopDto("Huanchaco", -8.0797, -79.1187)
            )
        ),
        RouteDto(
            id = "micro-202",
            letter = "C",
            name = "Micro 202",
            origin = "Av. Larco",
            destination = "Av. España",
            durationMinutes = 22,
            fare = 1.50,
            schedule = "05:30 – 23:00",
            accentIndex = 3,
            stops = listOf(
                StopDto("Av. Larco", -8.1226, -79.0332),
                StopDto("Óvalo Larco", -8.1191, -79.0345),
                StopDto("Av. Húsares de Junín", -8.1155, -79.0318),
                StopDto("Av. España", -8.1132, -79.0330)
            )
        ),
        RouteDto(
            id = "linea-303",
            letter = "D",
            name = "Bus 303",
            origin = "Av. Los Incas",
            destination = "Moche",
            durationMinutes = 35,
            fare = 2.00,
            schedule = "05:00 – 21:30",
            accentIndex = 4,
            stops = listOf(
                StopDto("Av. Los Incas", -8.1170, -79.0210),
                StopDto("Av. Pumacahua", -8.0921, -79.0090),
                StopDto("Óvalo Grau", -8.1195, -79.0264),
                StopDto("Moche", -8.1697, -79.0083)
            )
        ),
        RouteDto(
            id = "linea-ucv",
            letter = "E",
            name = "Universitaria",
            origin = "UPN",
            destination = "UNT",
            durationMinutes = 28,
            fare = 1.80,
            schedule = "06:00 – 22:00",
            accentIndex = 5,
            stops = listOf(
                StopDto("UPN", -8.0684, -79.0575),
                StopDto("Av. El Ejército", -8.1063, -79.0350),
                StopDto("Av. Juan Pablo II", -8.1191, -79.0407),
                StopDto("UNT", -8.1178, -79.0417)
            )
        )
    )
}

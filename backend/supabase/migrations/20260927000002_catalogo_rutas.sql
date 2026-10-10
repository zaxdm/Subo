-- Las rutas con las que arranca el servidor: las mismas que traía la app
-- (antes en ktor/src/main/kotlin/.../store/Catalog.kt).
--
-- Son aproximadas y están para que la app no aparezca vacía. La idea es que se
-- vayan reemplazando por recorridos grabados de verdad.

INSERT INTO routes (id, letter, name, origin, destination, duration_minutes, fare, schedule,
                    accent_index, stops, path, path_snapped, created_by_user, status, author_id, created_at)
SELECT r.id, r.letter, r.name, r.origin, r.destination, r.duration_minutes, r.fare, r.schedule,
       r.accent_index, r.stops::jsonb, NULL, FALSE, FALSE, 'APPROVED', NULL,
       (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
FROM (VALUES
    ('california-porvenir', 'A', 'California', 'El Porvenir', 'Huanchaco', 38, 2.00, '05:00 – 22:30', 0, '[
        {"name": "El Porvenir", "latitude": -8.0806, "longitude": -78.9950},
        {"name": "Av. Pumacahua", "latitude": -8.0921, "longitude": -79.0090},
        {"name": "Av. América Sur", "latitude": -8.1085, "longitude": -79.0245},
        {"name": "Plaza de Armas", "latitude": -8.1120, "longitude": -79.0288},
        {"name": "Av. Mansiche", "latitude": -8.1024, "longitude": -79.0389},
        {"name": "Óvalo Huanchaco", "latitude": -8.0932, "longitude": -79.0780},
        {"name": "Huanchaco", "latitude": -8.0797, "longitude": -79.1187}
    ]'),
    ('california-esperanza', 'B', 'California', 'La Esperanza', 'Buenos Aires', 42, 2.00, '05:15 – 22:00', 1, '[
        {"name": "La Esperanza", "latitude": -8.0704, "longitude": -79.0470},
        {"name": "Av. Condorcanqui", "latitude": -8.0839, "longitude": -79.0432},
        {"name": "Av. Mansiche", "latitude": -8.1024, "longitude": -79.0389},
        {"name": "Av. España", "latitude": -8.1132, "longitude": -79.0330},
        {"name": "Av. Larco", "latitude": -8.1226, "longitude": -79.0332},
        {"name": "Buenos Aires", "latitude": -8.1339, "longitude": -79.0490}
    ]'),
    ('huanchaco-express', 'H', 'Huanchaco Express', 'Centro Cívico', 'Huanchaco', 30, 2.50, '06:00 – 21:00', 2, '[
        {"name": "Centro Cívico", "latitude": -8.1108, "longitude": -79.0301},
        {"name": "Av. Mansiche", "latitude": -8.1024, "longitude": -79.0389},
        {"name": "Óvalo Huanchaco", "latitude": -8.0932, "longitude": -79.0780},
        {"name": "Huanchaco", "latitude": -8.0797, "longitude": -79.1187}
    ]'),
    ('micro-202', 'C', 'Micro 202', 'Av. Larco', 'Av. España', 22, 1.50, '05:30 – 23:00', 3, '[
        {"name": "Av. Larco", "latitude": -8.1226, "longitude": -79.0332},
        {"name": "Óvalo Larco", "latitude": -8.1191, "longitude": -79.0345},
        {"name": "Av. Húsares de Junín", "latitude": -8.1155, "longitude": -79.0318},
        {"name": "Av. España", "latitude": -8.1132, "longitude": -79.0330}
    ]'),
    ('linea-303', 'D', 'Bus 303', 'Av. Los Incas', 'Moche', 35, 2.00, '05:00 – 21:30', 4, '[
        {"name": "Av. Los Incas", "latitude": -8.1170, "longitude": -79.0210},
        {"name": "Av. Pumacahua", "latitude": -8.0921, "longitude": -79.0090},
        {"name": "Óvalo Grau", "latitude": -8.1195, "longitude": -79.0264},
        {"name": "Moche", "latitude": -8.1697, "longitude": -79.0083}
    ]'),
    ('linea-ucv', 'E', 'Universitaria', 'UPN', 'UNT', 28, 1.80, '06:00 – 22:00', 5, '[
        {"name": "UPN", "latitude": -8.0684, "longitude": -79.0575},
        {"name": "Av. El Ejército", "latitude": -8.1063, "longitude": -79.0350},
        {"name": "Av. Juan Pablo II", "latitude": -8.1191, "longitude": -79.0407},
        {"name": "UNT", "latitude": -8.1178, "longitude": -79.0417}
    ]')
) AS r(id, letter, name, origin, destination, duration_minutes, fare, schedule, accent_index, stops)
ON CONFLICT (id) DO NOTHING;

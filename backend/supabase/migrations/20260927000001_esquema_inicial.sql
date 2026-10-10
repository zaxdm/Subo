-- Esquema inicial: el mismo que creaba el servidor Ktor al arrancar
-- (ktor/src/main/kotlin/.../db/Database.kt), con dos cambios:
--   * paradas y recorrido en jsonb en lugar de texto con JSON dentro;
--   * RLS activado en todas las tablas (ver al final).

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    -- Se marca a mano en la base de datos: por ahora no hay pantalla de administración.
    is_admin BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE alerts (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    place_name TEXT,
    comment TEXT,
    created_at BIGINT NOT NULL,
    last_confirmed_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    author_id TEXT NOT NULL REFERENCES users(id)
);

-- El filtro por caja de coordenadas y por vigencia es el más usado.
CREATE INDEX alerts_expires_idx ON alerts (expires_at);
CREATE INDEX alerts_position_idx ON alerts (latitude, longitude);
CREATE INDEX alerts_author_idx ON alerts (author_id);

CREATE TABLE alert_votes (
    alert_id TEXT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    vote TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (alert_id, user_id)
);

CREATE INDEX alert_votes_user_idx ON alert_votes (user_id);

CREATE TABLE routes (
    id TEXT PRIMARY KEY,
    letter TEXT NOT NULL,
    name TEXT NOT NULL,
    origin TEXT NOT NULL,
    destination TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    fare DOUBLE PRECISION NOT NULL,
    schedule TEXT NOT NULL,
    accent_index INTEGER NOT NULL,
    stops JSONB NOT NULL,
    path JSONB,
    path_snapped BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'APPROVED',
    author_id TEXT REFERENCES users(id),
    created_at BIGINT NOT NULL
);

CREATE INDEX routes_status_idx ON routes (status);
CREATE INDEX routes_author_idx ON routes (author_id);

-- Supabase publica el esquema public por su API REST con la clave anónima, que
-- viaja en cualquier cliente. Con RLS activado y sin políticas, esa API no ve
-- nada (ni los hashes de contraseñas). La función `server` entra con la
-- conexión directa de Postgres, que no pasa por RLS.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE routes ENABLE ROW LEVEL SECURITY;

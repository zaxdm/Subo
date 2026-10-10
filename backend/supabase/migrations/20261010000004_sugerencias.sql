-- Buzón de sugerencias: lo que la gente escribe desde Soporte en la app y el
-- administrador lee en el panel.
CREATE TABLE feedback (
    id TEXT PRIMARY KEY,
    -- Si la cuenta se borra, la sugerencia se queda: sigue sirviendo sin autor.
    user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
    subject TEXT NOT NULL,
    message TEXT NOT NULL,
    app_version TEXT NOT NULL DEFAULT '',
    device TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE INDEX feedback_created_idx ON feedback (created_at);
CREATE INDEX feedback_user_idx ON feedback (user_id);

-- Como el resto de tablas: sin políticas, la API REST pública no ve nada.
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;

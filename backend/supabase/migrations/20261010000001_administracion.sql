ALTER TABLE routes ADD COLUMN vehicle_name TEXT NOT NULL DEFAULT '';
ALTER TABLE routes ADD COLUMN image_url TEXT;
ALTER TABLE routes ADD COLUMN description TEXT NOT NULL DEFAULT '';
ALTER TABLE routes ADD COLUMN bidirectional BOOLEAN NOT NULL DEFAULT TRUE;

-- Solo el servidor con service role escribe imágenes. Lectura pública para la app.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('route-images', 'route-images', TRUE, 5242880,
        ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO NOTHING;

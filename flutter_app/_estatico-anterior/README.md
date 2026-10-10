# Administración de Ruta Norte

Web estática en `dist/`, conectada a la misma API Supabase que Android. Incluye
inicio de sesión, catálogo con filtros, creación/edición/eliminación de rutas,
paraderos ordenados en un mapa, dibujo de recorridos, fotos del micro, pasaje,
horario, descripción, sentido de circulación y publicación/ocultación.

El JWT permanece en memoria y se elimina al cerrar sesión o recargar la página.
Solo se recuerda la URL del servidor; no se guardan contraseñas ni claves de Supabase.
El servidor comprueba `users.is_admin` en cada operación de administración.

## Preparar el backend

Desde `backend/`, con la CLI autenticada y el proyecto correcto enlazado:

```powershell
npx.cmd supabase db push
npx.cmd supabase functions deploy server
```

La migración `20261010000001_administracion.sql` añade datos del micro y el bucket
`route-images`. Debe aplicarse antes de desplegar la función actualizada.
Supabase proporciona `SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` a la función;
estas claves no deben copiarse a la web ni a Android.

Crea tu cuenta desde Android y, en el editor SQL de Supabase, asigna permisos a
esa cuenta (sustituye el correo por el tuyo):

```sql
UPDATE public.users SET is_admin = TRUE WHERE email = 'tu-correo@ejemplo.com';
```

Después entra en la web con ese correo y contraseña. La URL del servidor tiene
el formato `https://<proyecto>.supabase.co/functions/v1/server`. Se permite HTTP
solo para `localhost` y `127.0.0.1` durante desarrollo.

## Uso

1. Crea una ruta y completa los datos del micro.
2. Añade al menos dos paraderos haciendo clic en el mapa. Pon nombres útiles.
   Arrastra los marcadores para corregir posiciones; usa las flechas para ordenar.
3. Para un trazado exacto, cambia a «Dibujar recorrido» y marca las esquinas y
   calles en el orden de circulación. Sin trazado, la app lo estima entre paraderos.
4. Sube JPG, PNG o WebP de hasta 5 MB, o introduce una URL HTTPS de imagen.
5. Marca si circula en ambos sentidos; si la vuelta usa calles distintas, crea
   una ruta de regreso independiente y desmarca la opción en ambas.
6. Guarda como publicada para que Android la use al actualizar su catálogo.

La distancia y duración de caminata son estimaciones; el planificador no conoce
barreras peatonales ni frecuencias reales. Los paraderos y recorridos deben ser
verificados por el administrador. Las imágenes subidas quedan en el bucket;
quitar una foto de una ruta no elimina su archivo, que puede reutilizarse.

Para servir localmente, usa cualquier servidor estático sobre `dist/`.
Los recursos del mapa (Leaflet y teselas OSM) y las fuentes requieren conexión.

## Verificación

```powershell
node --check dist/app.js
cd ../backend/supabase/functions/server
deno test --cached-only --allow-env
deno check --cached-only index.ts
```

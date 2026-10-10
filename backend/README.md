# Servidor de Subo (Supabase)

El acceso Google añade `POST /api/auth/google/challenge` y `POST /api/auth/google`.
Requiere la migración `20261010000002_google_login.sql` y `GOOGLE_WEB_CLIENT_IDS`.
Sin esa variable permanece desactivado. Consulta [la configuración](../GOOGLE_LOGIN.md).

## Administración web

El [panel de administración](../admin-web/README.md) usa cuentas existentes con
`users.is_admin = TRUE`. Los endpoints `/api/admin/me`, `/api/admin/routes`
(GET/POST), `/api/admin/routes/:id` (PUT/DELETE) y `/api/admin/images` (POST)
verifican ese permiso en el servidor. La web no recibe la clave de servicio.

Aplica `20261010000001_administracion.sql` antes de desplegar esta versión de
la función. Añade nombre del micro, foto, descripción, sentido de circulación
y el bucket `route-images`. Las fotos admiten JPG, PNG o WebP de hasta 5 MB.
Los recorridos pendientes u ocultos no se entregan a otros usuarios por ID.

Backend de la app [Subo](../expressdm/README.md). Es lo que permite que
las alertas y las rutas se compartan entre usuarios y que la clave de Gemini no
viaje dentro del APK.

Corre entero en **Supabase**:

- **Base de datos**: el Postgres del proyecto. El esquema y el catálogo inicial
  llegan por **migraciones** (`supabase/migrations`): no hay que escribir SQL a
  mano en el panel de Supabase.
- **API**: una Edge Function llamada `server` (Deno + TypeScript + Hono) con los
  mismos caminos y el mismo JSON que tenía el servidor Ktor. La app no cambia:
  solo su `BACKEND_URL`.

```
backend/
├── supabase/
│   ├── config.toml                  Proyecto y ajustes de la función
│   ├── migrations/                  Esquema y datos iniciales, en orden
│   │   ├── 20260927000001_esquema_inicial.sql
│   │   └── 20260927000002_catalogo_rutas.sql
│   └── functions/
│       ├── .env.example             Plantilla de secretos (JWT_SECRET, GEMINI_API_KEY)
│       └── server/                  La API
│           ├── index.ts             Arranque: conexión, secretos y servidor
│           ├── app.ts               Los caminos HTTP, sesiones y errores
│           ├── stores.ts            Consultas SQL: usuarios, alertas y rutas
│           ├── auth.ts              Contraseñas (bcrypt), nombres y tokens (JWT)
│           ├── alert_policy.ts      Caducidad y votos de las alertas
│           ├── geo.ts               Distancias y cajas de búsqueda
│           ├── gemini.ts            Asistente y moderación con Gemini
│           ├── models.ts            Lo que viaja entre app y servidor
│           ├── db.ts                Conexión a Postgres
│           └── *_test.ts            Pruebas
└── ktor/                            El servidor anterior (Kotlin), solo de referencia
```

## La API

| Camino | Quién puede | Para qué |
|---|---|---|
| `POST /api/auth/register` | cualquiera | Crear cuenta |
| `POST /api/auth/login` | cualquiera | Iniciar sesión (devuelve un token) |
| `GET /api/me` | con sesión | Comprobar la sesión |
| `POST /api/me` | con sesión | Cambiar el nombre visible |
| `GET /api/alerts?lat&lon&radius` | cualquiera | Alertas vigentes cerca de un punto |
| `POST /api/alerts` | con sesión | Publicar una alerta |
| `POST /api/alerts/{id}/vote` | con sesión | "Sigue ahí" o "ya no está" |
| `GET /api/routes` | cualquiera | Catálogo de rutas |
| `GET /api/routes/{id}` | cualquiera | Una ruta |
| `POST /api/routes` | con sesión | Guardar una ruta grabada |
| `DELETE /api/routes/{id}` | autor o administrador | Borrar una ruta |
| `POST /api/assistant` | con sesión | Preguntar qué micro tomar |
| `POST /api/moderation` | con sesión | Revisar un texto antes de publicarlo |
| `GET /health` | cualquiera | Comprobar que el servidor vive |

Todos cuelgan de `https://<proyecto>.supabase.co/functions/v1/server`, que es lo
que va en `BACKEND_URL` de la app.

Decisiones que conviene conocer:

- **Las cuentas son propias, no Supabase Auth.** Se mantienen la tabla `users`,
  bcrypt y los tokens JWT del servidor Ktor, así la app no cambia. Por eso la
  función tiene `verify_jwt = false` en `config.toml`: la pasarela de Supabase no
  conoce estos tokens y la comprobación la hace la propia función.
- **RLS activado en todas las tablas, sin políticas.** La API REST automática de
  Supabase no puede leer ni escribir nada con la clave anónima (en particular,
  los hashes de contraseñas). La función entra con `SUPABASE_DB_URL`, la
  conexión directa de Postgres, que no pasa por RLS.
- **SQL a la vista**, sin ORM, igual que antes.
- **Paradas y recorrido en `jsonb`** (antes texto con JSON dentro).

## Desplegar en Supabase

Necesitas la [CLI de Supabase](https://supabase.com/docs/guides/cli)
(`npm i -g supabase`, o `npx supabase ...` sin instalar) y un proyecto creado en
[supabase.com](https://supabase.com/dashboard). Todo se hace desde `backend/`.

```powershell
cd backend

# 1. Enlazar con tu proyecto (el ref sale en la URL del panel)
npx supabase login
npx supabase link --project-ref TU_PROJECT_REF

# 2. Crear las tablas y el catálogo: aplica las migraciones pendientes
npx supabase db push

# 3. Secretos de la función (copia antes .env.example como .env y rellénalo)
npx supabase secrets set --env-file supabase/functions/.env

# 4. Publicar la API
npx supabase functions deploy server
```

Comprobar que responde:

```powershell
curl https://TU_PROJECT_REF.supabase.co/functions/v1/server/health
# {"status":"ok"}
```

Y en la app, en `expressdm/local.properties`:

```
BACKEND_URL=https://TU_PROJECT_REF.supabase.co/functions/v1/server
```

## Cambiar el esquema

Nunca a mano en el panel: se crea una migración nueva y se sube.

```powershell
npx supabase migration new agregar_algo     # crea supabase/migrations/<fecha>_agregar_algo.sql
# ...escribe el cambio en ese archivo...
npx supabase db push                         # lo aplica en la nube
```

Supabase lleva la cuenta de qué migraciones ya se aplicaron, así que `db push`
solo ejecuta las nuevas. Las ya subidas no se editan: cualquier corrección va en
una migración posterior.

## Probar en local

Con Docker Desktop instalado, la CLI levanta un Supabase completo en la PC:

```powershell
npx supabase start                           # base de datos + migraciones aplicadas
npx supabase functions serve --env-file supabase/functions/.env
# API en http://localhost:54321/functions/v1/server
# desde el emulador de Android: http://10.0.2.2:54321/functions/v1/server
npx supabase db reset                        # borra la base local y reaplica las migraciones
```

Pruebas y chequeo de tipos (sin Docker ni base de datos):

```powershell
cd supabase/functions/server
npx deno test --allow-env
npx deno check index.ts
```

## Administración

No hay pantalla de administración. Para que alguien pueda borrar rutas ajenas,
marca `is_admin` en la tabla `users` desde el editor de tablas de Supabase.

## Pasar los datos del servidor anterior

Las contraseñas (bcrypt) y los tokens (mismo emisor y formato) son compatibles.
Si `JWT_SECRET` es el mismo que tenía `ktor/local.properties`, las sesiones
abiertas siguen valiendo. Para llevar los datos de la base local:

```powershell
# Exportar solo datos desde el PostgreSQL portable (con el servidor viejo arrancado)
C:\Users\Usuario\pgsql\bin\pg_dump.exe -h localhost -U expresdm -d expresdm --data-only `
  -t users -t alerts -t alert_votes -t routes -f datos.sql
```

Ojo: en `routes` las columnas `stops_json` y `path_json` (texto) pasaron a
llamarse `stops` y `path` (`jsonb`), así que ese volcado hay que ajustarlo antes
de cargarlo con `psql` en la base de Supabase.

## El servidor anterior (`ktor/`)

Es el servidor Kotlin + Ktor que corría en la PC o dentro del teléfono con
Termux, con su propio [README](ktor/README.md). Ya no se usa, y se conserva solo
como referencia porque nunca estuvo en git. Cuando la versión de Supabase esté
en marcha se puede borrar la carpeta entera.

# Servidor de Subo

Servidor propio en **Kotlin + Ktor** con **PostgreSQL**. Es lo que permite que
las alertas y las rutas se compartan entre usuarios, en lugar de quedarse en
cada teléfono, y saca la clave de Gemini del APK.

Por ahora corre en la PC de desarrollo. El día que se publique en la nube no
cambia el código: solo los valores de `local.properties`.

## Qué hace

| Camino | Quién puede | Para qué |
|---|---|---|
| `POST /api/auth/register` | cualquiera | Crear cuenta |
| `POST /api/auth/login` | cualquiera | Iniciar sesión (devuelve un token) |
| `GET /api/me` | con sesión | Comprobar la sesión |
| `POST /api/me` | con sesión | Cambiar el nombre visible (`{"displayName": "..."}`) |
| `GET /api/alerts?lat&lon&radius` | cualquiera | Alertas vigentes cerca de un punto |
| `POST /api/alerts` | con sesión | Publicar una alerta |
| `POST /api/alerts/{id}/vote` | con sesión | "Sigue ahí" o "ya no está" |
| `GET /api/routes` | cualquiera | Catálogo de rutas |
| `POST /api/routes` | con sesión | Guardar una ruta grabada |
| `DELETE /api/routes/{id}` | autor o administrador | Borrar una ruta |
| `POST /api/assistant` | con sesión | Preguntar qué micro tomar |
| `POST /api/moderation` | con sesión | Revisar un texto antes de publicarlo |
| `GET /health` | cualquiera | Comprobar que el servidor vive |

Decisiones que conviene conocer:

- **Leer no pide cuenta, escribir sí.** La app puede mostrar alertas y rutas a
  quien aún no se registró; publicar, votar y grabar necesitan sesión.
- **Las reglas de caducidad viven aquí**
  ([`AlertPolicy`](src/main/kotlin/com/peru/expresdm/backend/alerts/AlertPolicy.kt)).
  Si cada teléfono decidiera, dos personas verían cosas distintas del mismo sitio.
- **Un voto por persona y alerta**, en su propia tabla: el recuento es real y
  nadie puede votar dos veces.
- **La clave de Gemini no sale del servidor.** La app manda el texto y recibe la
  respuesta.
- **Sin ORM**: SQL a la vista con JDBC y HikariCP. Para un servidor de este
  tamaño es más fácil de leer y depurar.

## Cómo levantarlo

Necesitas el **JDK 21** (el mismo de la app).

**1. Base de datos.** PostgreSQL portable, sin instalador ni permisos de
administrador, en `C:\Users\Usuario\pgsql`:

```powershell
.\scripts\postgres.ps1 init     # solo la primera vez
.\scripts\postgres.ps1 start    # arrancarlo
.\scripts\postgres.ps1 status   # ver si corre
.\scripts\postgres.ps1 stop     # detenerlo
```

Los datos quedan en `backend\pgdata`, que no se sube a git.

**2. Configuración.** Copia `local.properties.example` como `local.properties` y
rellena los valores. Ese archivo tampoco se sube a git.

**3. Servidor.**

```powershell
$env:JAVA_HOME = "C:\Users\Usuario\.jdks\jdk-21.0.12.1+1"
.\gradlew.bat run        # queda escuchando en el puerto 8080
.\gradlew.bat build      # compila y pasa las pruebas
```

Escucha en `0.0.0.0`, así que el teléfono lo alcanza por la red local
(`http://LA_IP_DE_TU_PC:8080`). Si el cortafuegos de Windows lo bloquea, para
probar con un teléfono conectado por adb:

```powershell
adb reverse tcp:8080 tcp:8080   # el teléfono usa http://localhost:8080
```

## El servidor dentro del teléfono (Termux)

El servidor también corre en el propio teléfono, sin PC y sin internet. Es el
modo que usamos ahora: la app habla con `http://127.0.0.1:8080`, es decir,
consigo misma.

Lo que se instala en el teléfono: **Termux** (una terminal de Android), dentro de
ella **Java 17** y **PostgreSQL**, y el servidor empaquetado
(`gradlew.bat distZip` deja el zip en `backend/build/distributions`).

**Instalación, una sola vez.** Con los archivos ya copiados en *Descargas*
(`expresdm-backend.zip`, `instalar.sh`, `servidor.sh` y, si se quiere asistente,
`gemini.txt`), abre Termux y escribe:

```bash
termux-setup-storage          # y acepta el permiso que aparece
bash ~/storage/downloads/instalar.sh
```

**Actualizar a una versión nueva.** Copia el zip nuevo a *Descargas* como
`expresdm-backend.zip` (con `actualizar.sh` al lado) y, en Termux:

```bash
bash storage/downloads/actualizar.sh
```

Cambia solo el programa: la base de datos, las cuentas y la configuración se
quedan como están, y la versión anterior queda en `~/rutanorte/app.anterior`
por si el arranque falla.

**Encender y apagar**, desde Termux:

```bash
~/rutanorte/servidor.sh start    # base de datos + servidor
~/rutanorte/servidor.sh stop
~/rutanorte/servidor.sh status
~/rutanorte/servidor.sh log      # si algo falla
```

Detalles que conviene saber:

- El servidor escucha **solo dentro del teléfono** (`127.0.0.1`): nadie de la
  red puede entrar, ni siquiera tú desde la PC.
- Por eso, en este modo **los datos no se comparten con otros usuarios**: el
  teléfono es a la vez app y servidor. Para compartir de verdad hace falta
  publicarlo en la nube.
- **Android congela Termux en segundo plano, y eso rompe el servidor.** En un
  Honor con Android 15 se comprobó así: con Termux delante, el servidor responde
  en decenas de milisegundos; con Subo delante, la misma petición no se
  contesta — se queda en la cola y se atiende minutos después, al volver a
  Termux. No bastó con `termux-wake-lock`, ni con `settings put global
  cached_apps_freezer disabled`, `device_config put activity_manager use_freezer
  false`, `dumpsys deviceidle whitelist +com.termux` ni `am set-standby-bucket
  com.termux active`. La solución está en el propio teléfono: *Ajustes →
  Batería → Inicio de aplicaciones → Termux → Gestión manual*, con los tres
  permisos activados. Sin eso, este modo no sirve y conviene usar el servidor en
  la PC.
- También se desactivó el "asesino de procesos fantasma" con adb, que es un
  problema distinto (mata procesos hijos) y ese sí queda resuelto.
- La clave de Gemini queda en `~/rutanorte/local.properties`, dentro del
  almacenamiento privado de Termux, y el instalador borra la copia de Descargas.
- El servidor compila para **Java 17**, que es el que trae Termux, aunque en la
  PC se compile con el JDK 21.

## Estructura

```
backend/src/main/kotlin/com/peru/expresdm/backend/
├── Main.kt              Arranque: base de datos, catálogo inicial y servidor
├── Config.kt            Claves y ajustes (local.properties o variables de entorno)
├── api/                 Los caminos HTTP
│   ├── Server.kt        Plugins, sesiones y manejo de errores
│   ├── AuthApi.kt       Registro, inicio de sesión y "quién soy"
│   ├── AlertsApi.kt     Alertas y votos
│   ├── RoutesApi.kt     Catálogo y rutas grabadas
│   └── AiApi.kt         Asistente y moderación
├── alerts/AlertPolicy.kt  Caducidad y votos
├── auth/                Contraseñas (bcrypt) y tokens (JWT)
├── db/Database.kt       Conexión y creación de tablas
├── geo/Geo.kt           Distancias y cajas de búsqueda
├── model/Dtos.kt        Lo que viaja entre app y servidor
└── store/               Consultas: usuarios, alertas, rutas y catálogo inicial
```

## Pendiente

- **Servidor en el teléfono sin depender de los ajustes del fabricante**: hoy
  hay que poner Termux en gestión manual a mano (ver arriba).
- **Administración**: para aprobar o borrar rutas de otros hay que marcar
  `is_admin` a mano en la base de datos.
- **Publicar en la nube** (Railway, un VPS...) cuando haga falta que lo usen
  personas fuera de esta red.
- **HTTPS**: en la PC va por HTTP. En internet, el token de sesión viaja en cada
  petición y necesita HTTPS obligatoriamente.

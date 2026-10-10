# Activar el acceso con Google

## Estado actual

Client ID configurado en Android y Supabase:
`608487070231-84hnjf4mr3rollfpdat0ev4vqa5ud6ot.apps.googleusercontent.com`.
La migración de Google está aplicada y el backend está desplegado.
La app debug actualizada está instalada en el celular conectado.
El ID proporcionado debe corresponder a un cliente OAuth de tipo Web.

Para autorizar esta instalación en Google Cloud, crear el cliente OAuth Android
en el mismo proyecto, con estos datos:

- Paquete: `com.peru.expresdm.debug`
- SHA-1: `6E:C6:6C:A6:F0:14:16:0A:5B:C2:CC:B9:87:5D:FD:E5:1C:72:FE:9B`

La prueba de inicio de sesión con una cuenta real sigue pendiente.

El código está preparado en Android y en Supabase. Sin configuración, el acceso
Google permanece desactivado y correo/contraseña sigue funcionando.

## Configuración que falta

En un mismo proyecto de Google Cloud:

1. Configurar la pantalla de consentimiento OAuth con nombre, correo de soporte
   y los usuarios de prueba mientras la aplicación siga en pruebas.
2. Crear un cliente OAuth **Web**. Su Client ID se usa en Android y en el
   backend como audiencia de los tokens. No se necesita su Client Secret para
   este flujo; no lo pongas en el APK ni lo compartas en el chat.
3. Crear un cliente OAuth **Android** para `com.peru.expresdm.debug`, con el
   SHA-1 del certificado debug, para probar el celular conectado.
4. Para publicar, crear también el cliente Android de `com.peru.expresdm` con
   el SHA-1 del certificado que firma la distribución. Con Play App Signing,
   usar el certificado de firma de la app, no solamente el de subida.

Para obtener los certificados desde `expressdm/`:

```powershell
.\gradlew.bat signingReport
```

## Android

Añadir a `expressdm/local.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=CLIENT_ID_WEB.apps.googleusercontent.com
BACKEND_URL=https://TU_PROYECTO.supabase.co/functions/v1/server
```

Usar el ID **Web**, no el Android. Después volver a compilar e instalar el APK.

## Supabase

Aplicar las migraciones antes de desplegar el backend. La nueva migración
`20261010000002_google_login.sql` añade `users.google_sub`, permite usuarios sin
contraseña local y crea los desafíos de acceso. Los desafíos se guardan como
hashes, caducan a los cinco minutos y se consumen una sola vez.

Desde `backend/`, con la CLI autenticada y el proyecto correcto enlazado:

```powershell
npx.cmd supabase db push
npx.cmd supabase secrets set GOOGLE_WEB_CLIENT_IDS=CLIENT_ID_WEB.apps.googleusercontent.com
npx.cmd supabase functions deploy server
```

Se pueden aceptar varios clientes Web separándolos con comas. Mantener
`verify_jwt = false` para la función: la aplicación usa sus propios JWT y el
servidor valida los tokens Google antes de emitir una sesión de Subo.

## Comportamiento

- «Continuar con Google» abre el selector oficial mediante Credential Manager.
- El backend verifica firma RS256, emisor Google, audiencia, caducidad, correo
  verificado y nonce emitido por el servidor. Los tokens Google no se guardan.
- La identidad se reconoce por `sub`, no por nombre ni por un correo enviado
  libremente desde el teléfono. Los usuarios nuevos no reciben permisos de admin.
- Si el correo ya tiene una cuenta local, el usuario debe introducir su
  contraseña actual de Subo y pulsar Google otra vez para vincularla.
  Esto conserva el ID, el nombre elegido, los datos y los permisos existentes.
- Cerrar sesión borra la sesión local y limpia el estado de Credential Manager.
- Cancelar el selector no inicia sesión ni crea una cuenta.

El panel web continúa usando correo/contraseña. Una cuenta creada únicamente
con Google no tiene contraseña local; el acceso Google del navegador requeriría
su propia integración de Google Identity Services.

## Validación

Las pruebas del backend usan claves RSA locales para comprobar tokens válidos,
firmas falsas, audiencia y emisor incorrectos, caducidad, nonce incorrecto,
correo no verificado, reintentos y vinculación de cuentas. No necesitan una
cuenta de Google real. La prueba completa con el selector real y el backend
desplegado queda pendiente hasta recibir la configuración OAuth.

Referencias oficiales:

- [Credential Manager y Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Verificación del token en el backend](https://developers.google.com/identity/sign-in/android/backend-auth)

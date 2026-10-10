#!/usr/bin/env bash
# Compila la web para Cloudflare Pages.
#
# Cloudflare no trae Flutter instalado, así que se descarga en cada despliegue.
# En Cloudflare (Workers & Pages):
#   Directorio raíz:        flutter_app
#   Comando de compilación: bash cloudflare-build.sh
#   Comando de despliegue:  npx wrangler deploy (usa wrangler.jsonc)
set -euo pipefail

# La misma versión con la que se desarrolla; se puede cambiar con una variable
# de entorno FLUTTER_VERSION en Cloudflare.
FLUTTER_VERSION="${FLUTTER_VERSION:-3.47.2}"

if [ ! -d .flutter-sdk ]; then
  git clone --depth 1 --branch "$FLUTTER_VERSION" https://github.com/flutter/flutter.git .flutter-sdk
fi
export PATH="$PWD/.flutter-sdk/bin:$PATH"

flutter --version
flutter pub get

# SERVER_URL (opcional) apunta a otro servidor; sin ella se usa el de Supabase.
if [ -n "${SERVER_URL:-}" ]; then
  flutter build web --release --dart-define="SERVER_URL=$SERVER_URL"
else
  flutter build web --release
fi

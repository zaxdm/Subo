#!/data/data/com.termux/files/usr/bin/bash
# Pone en el teléfono una versión nueva del servidor, sin tocar la base de
# datos ni la configuración (las cuentas y las alertas se quedan como están).
#
# En la PC:  copia backend/build/distributions/expresdm-backend-*.zip a
#            Descargas del teléfono (renombrado a expresdm-backend.zip).
# En Termux: bash storage/downloads/actualizar.sh
#
# Después del cambio, el servidor queda encendido.

set -e

DESTINO="$HOME/rutanorte"
DESCARGAS="$HOME/storage/downloads"
ZIP="$DESCARGAS/expresdm-backend.zip"

if [ ! -f "$ZIP" ]; then
    echo "No encuentro expresdm-backend.zip en Descargas."
    exit 1
fi

if [ ! -d "$DESTINO" ]; then
    echo "El servidor no está instalado todavía. Usa instalar.sh la primera vez."
    exit 1
fi

echo "== 1/3 Apagando el servidor =="
# Solo el servidor: la base de datos se queda encendida, así el cambio dura
# unos segundos en lugar de un arranque entero.
PID="$DESTINO/servidor.pid"
if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
    kill "$(cat "$PID")" 2>/dev/null || true
    sleep 2
    kill -9 "$(cat "$PID")" 2>/dev/null || true
    rm -f "$PID"
    echo "Servidor apagado."
else
    echo "No estaba encendido."
fi

echo "== 2/3 Copiando la versión nueva =="
rm -rf "$DESTINO/app.anterior"
[ -d "$DESTINO/app" ] && mv "$DESTINO/app" "$DESTINO/app.anterior"
unzip -q -o "$ZIP" -d "$DESTINO"
mv "$DESTINO"/expresdm-backend-* "$DESTINO/app"
# Si el arranque falla, en app.anterior queda la versión que sí funcionaba.

# El script de encendido también puede haber cambiado.
if [ -f "$DESCARGAS/servidor.sh" ]; then
    cp "$DESCARGAS/servidor.sh" "$DESTINO/servidor.sh"
    chmod +x "$DESTINO/servidor.sh"
fi

echo "== 3/3 Encendiendo =="
"$DESTINO/servidor.sh" start

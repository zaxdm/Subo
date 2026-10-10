#!/data/data/com.termux/files/usr/bin/bash
# Instala en el teléfono todo lo que el servidor necesita: Java, PostgreSQL y
# el propio servidor. Se ejecuta una sola vez, desde Termux:
#
#   bash ~/storage/downloads/instalar.sh
#
# Después, para encender y apagar:  ~/rutanorte/servidor.sh start | stop

set -e

DESTINO="$HOME/rutanorte"
DESCARGAS="$HOME/storage/downloads"
DATOS="$HOME/pgdata"
BASE=expresdm
USUARIO=expresdm
CLAVE=expresdm2026

echo "== 1/5 Actualizando paquetes de Termux =="
pkg update -y
pkg install -y openjdk-17 postgresql unzip

echo "== 2/5 Copiando el servidor =="
mkdir -p "$DESTINO"
if [ ! -f "$DESCARGAS/expresdm-backend.zip" ]; then
    echo "No encuentro expresdm-backend.zip en Descargas."
    echo "Copia ahí el archivo y vuelve a ejecutar esto."
    exit 1
fi
rm -rf "$DESTINO/app"
unzip -q -o "$DESCARGAS/expresdm-backend.zip" -d "$DESTINO"
# El zip trae una carpeta con el nombre y la versión: se renombra a "app".
mv "$DESTINO"/expresdm-backend-* "$DESTINO/app"
cp "$DESCARGAS/servidor.sh" "$DESTINO/servidor.sh"
chmod +x "$DESTINO/servidor.sh"

echo "== 3/5 Preparando la base de datos =="
if [ ! -f "$DATOS/PG_VERSION" ]; then
    mkdir -p "$DATOS"
    initdb -D "$DATOS" -U "$USUARIO" --auth=trust --encoding=UTF8 >/dev/null
    # Solo escucha dentro del teléfono: nadie de la red puede entrar.
    echo "listen_addresses = '127.0.0.1'" >> "$DATOS/postgresql.conf"
    pg_ctl -D "$DATOS" -l "$DATOS/servidor.log" start >/dev/null
    sleep 3
    createdb -U "$USUARIO" -h 127.0.0.1 "$BASE"
    pg_ctl -D "$DATOS" stop >/dev/null
    echo "Base de datos creada."
else
    echo "La base de datos ya existía."
fi

echo "== 4/5 Configuración del servidor =="
if [ ! -f "$DESTINO/local.properties" ]; then
    # Secreto aleatorio para firmar las sesiones.
    SECRETO=$(head -c 32 /dev/urandom | base64 | tr -d '\n')
    CLAVE_GEMINI=""
    if [ -f "$DESCARGAS/gemini.txt" ]; then
        CLAVE_GEMINI=$(tr -d ' \r\n' < "$DESCARGAS/gemini.txt")
    fi
    cat > "$DESTINO/local.properties" <<FIN
DB_URL=jdbc:postgresql://127.0.0.1:5432/$BASE
DB_USER=$USUARIO
DB_PASSWORD=$CLAVE
JWT_SECRET=$SECRETO
GEMINI_API_KEY=$CLAVE_GEMINI
PORT=8080
FIN
    chmod 600 "$DESTINO/local.properties"
    # La clave ya está guardada en la carpeta privada de Termux: se borra de
    # Descargas, que cualquier app del teléfono puede leer.
    rm -f "$DESCARGAS/gemini.txt"
    if [ -z "$CLAVE_GEMINI" ]; then
        echo "Sin clave de Gemini: el asistente quedará desactivado."
        echo "Para activarlo, pon la clave en Descargas/gemini.txt y repite este paso."
    fi
else
    echo "La configuración ya existía."
fi

echo "== 5/5 Listo =="
echo "Enciende el servidor con:  ~/rutanorte/servidor.sh start"
echo "Apágalo con:               ~/rutanorte/servidor.sh stop"

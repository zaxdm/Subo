#!/data/data/com.termux/files/usr/bin/bash
# Enciende y apaga el servidor de Subo dentro del teléfono.
#
#   ~/rutanorte/servidor.sh start     enciende base de datos y servidor
#   ~/rutanorte/servidor.sh stop      apaga los dos
#   ~/rutanorte/servidor.sh status    dice si están corriendo
#   ~/rutanorte/servidor.sh log       muestra el final del registro

DESTINO="$HOME/rutanorte"
DATOS="$HOME/pgdata"
REGISTRO="$DESTINO/servidor.log"
PID="$DESTINO/servidor.pid"

esta_vivo() {
    [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null
}

case "${1:-status}" in
    start)
        if esta_vivo; then
            echo "El servidor ya estaba encendido (pid $(cat "$PID"))."
            exit 0
        fi

        # Evita que Android duerma el proceso al apagar la pantalla.
        termux-wake-lock 2>/dev/null || true

        if ! pg_ctl -D "$DATOS" status >/dev/null 2>&1; then
            echo "Encendiendo la base de datos..."
            pg_ctl -D "$DATOS" -l "$DATOS/servidor.log" start >/dev/null
            sleep 3
        fi

        echo "Encendiendo el servidor..."
        cd "$DESTINO"
        nohup "$DESTINO/app/bin/expresdm-backend" >"$REGISTRO" 2>&1 &
        echo $! > "$PID"
        sleep 6

        if esta_vivo; then
            echo "Listo: la app ya puede usarlo."
        else
            echo "No arrancó. Mira el registro con: $0 log"
            exit 1
        fi
        ;;

    stop)
        if esta_vivo; then
            kill "$(cat "$PID")" 2>/dev/null
            sleep 2
            kill -9 "$(cat "$PID")" 2>/dev/null || true
            rm -f "$PID"
            echo "Servidor apagado."
        else
            echo "El servidor no estaba encendido."
        fi

        pg_ctl -D "$DATOS" stop >/dev/null 2>&1 && echo "Base de datos apagada."
        termux-wake-unlock 2>/dev/null || true
        ;;

    status)
        if esta_vivo; then
            echo "Servidor: encendido (pid $(cat "$PID"))"
        else
            echo "Servidor: apagado"
        fi
        if pg_ctl -D "$DATOS" status >/dev/null 2>&1; then
            echo "Base de datos: encendida"
        else
            echo "Base de datos: apagada"
        fi
        ;;

    log)
        tail -n 40 "$REGISTRO"
        ;;

    *)
        echo "Uso: $0 {start|stop|status|log}"
        exit 1
        ;;
esac

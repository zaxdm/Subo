# Maneja el PostgreSQL portable del proyecto, sin instalador ni permisos de administrador.
#
#   .\scripts\postgres.ps1 init     crea la base de datos la primera vez
#   .\scripts\postgres.ps1 start    lo arranca
#   .\scripts\postgres.ps1 stop     lo detiene
#   .\scripts\postgres.ps1 status   dice si está corriendo
#
# Los datos viven en backend\pgdata, que no se sube a git.

param(
    [Parameter(Position = 0)]
    [ValidateSet('init', 'start', 'stop', 'status')]
    [string]$Accion = 'status'
)

$ErrorActionPreference = 'Stop'

$pg = 'C:\Users\Usuario\pgsql\bin'
$datos = Join-Path $PSScriptRoot '..\pgdata' | Resolve-Path -ErrorAction SilentlyContinue
if (-not $datos) { $datos = Join-Path (Split-Path $PSScriptRoot -Parent) 'pgdata' }
$registro = Join-Path (Split-Path $PSScriptRoot -Parent) 'pgdata\servidor.log'
$usuario = 'expresdm'
$baseDatos = 'expresdm'
$clave = 'expresdm2026'

function Requiere-Postgres {
    if (-not (Test-Path (Join-Path $pg 'pg_ctl.exe'))) {
        Write-Output "No encuentro PostgreSQL en $pg. Vuelve a ejecutar la descarga."
        exit 1
    }
}

Requiere-Postgres

switch ($Accion) {
    'init' {
        if (Test-Path (Join-Path $datos 'PG_VERSION')) {
            Write-Output 'La base de datos ya estaba creada.'
            break
        }
        New-Item -ItemType Directory -Force -Path $datos | Out-Null
        $archivoClave = Join-Path $env:TEMP 'pg_clave.txt'
        Set-Content -Path $archivoClave -Value $clave -Encoding ascii -NoNewline
        & (Join-Path $pg 'initdb.exe') -D $datos -U $usuario --auth=scram-sha-256 --pwfile=$archivoClave --encoding=UTF8 | Out-Null
        Remove-Item $archivoClave -Force
        & (Join-Path $pg 'pg_ctl.exe') -D $datos -l $registro start | Out-Null
        Start-Sleep -Seconds 3
        $env:PGPASSWORD = $clave
        & (Join-Path $pg 'createdb.exe') -U $usuario -h localhost $baseDatos
        Write-Output "Base de datos '$baseDatos' creada. Usuario '$usuario'."
    }
    'start' {
        & (Join-Path $pg 'pg_ctl.exe') -D $datos -l $registro start
    }
    'stop' {
        & (Join-Path $pg 'pg_ctl.exe') -D $datos stop
    }
    'status' {
        & (Join-Path $pg 'pg_ctl.exe') -D $datos status
    }
}

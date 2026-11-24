# Prueba manual del módulo de autenticación y usuarios (PowerShell).
# Requisitos: backend en marcha (por defecto http://localhost:8080) con MONGODB_URI y JWT_SECRET definidos.
# Uso:  .\docs\pruebas\modulo-02-autenticacion.ps1 [-Base http://localhost:8080]
param([string]$Base = "http://localhost:8080")

$sufijo = Get-Random -Minimum 10000 -Maximum 99999
$correo = "estudiante$sufijo@universidad.edu.co"
$clave  = "Segura#2026"

Write-Host "1) Registro" -ForegroundColor Cyan
$registro = @{
    nombre = "Carlos Gómez"; correo = $correo; documento = "10$sufijo$sufijo"; clave = $clave
    rol = "ESTUDIANTE"; aceptaTratamientoDatos = $true; semestre = 4
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$Base/api/auth/registro" -ContentType "application/json; charset=utf-8" `
    -Body ([Text.Encoding]::UTF8.GetBytes($registro))

Write-Host "2) Inicio de sesión" -ForegroundColor Cyan
$sesion = Invoke-RestMethod -Method Post -Uri "$Base/api/auth/iniciar-sesion" -ContentType "application/json" `
    -Body (@{ correo = $correo; clave = $clave } | ConvertTo-Json)
$sesion.usuario
$cabeceras = @{ Authorization = "Bearer $($sesion.token)" }

Write-Host "3) Ruta protegida: perfil del usuario autenticado" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$Base/api/usuarios/yo" -Headers $cabeceras

Write-Host "4) Actualizar perfil de estudiante" -ForegroundColor Cyan
Invoke-RestMethod -Method Put -Uri "$Base/api/usuarios/yo/perfil-estudiante" -Headers $cabeceras `
    -ContentType "application/json" -Body (@{ semestre = 6 } | ConvertTo-Json)

Write-Host "5) Sin token debe responder 401" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$Base/api/auth/yo" } catch { "HTTP $($_.Exception.Response.StatusCode.value__): $($_.ErrorDetails.Message)" }

Write-Host "6) Rol incorrecto (perfil-tutor siendo ESTUDIANTE) debe responder 403" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Method Put -Uri "$Base/api/usuarios/yo/perfil-tutor" -Headers $cabeceras -ContentType "application/json" `
        -Body (@{ presentacion = "Hola"; tipoTutor = "PAR"; modalidades = @("VIRTUAL") } | ConvertTo-Json)
} catch { "HTTP $($_.Exception.Response.StatusCode.value__): $($_.ErrorDetails.Message)" }

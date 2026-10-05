# 0003. Acceso del paciente por enlace privado

- Estado: Aceptada
- Fecha: 2026-10-05

## Contexto

Los pacientes hoy reciben una página de Notion por persona. Pedirles crear cuenta y contraseña reduce el uso, pero la página contiene datos de salud.

## Decisión

Cada paciente recibe un enlace único que funciona como credencial:

- Token aleatorio de al menos 128 bits; la base guarda solo su hash SHA-256.
- Solo lectura y limitado a ese paciente y ese consultorio.
- PIN opcional como segundo factor (guardado con Argon2), caducidad opcional y revocación inmediata.
- Cada acceso queda en la bitácora de auditoría; rate limiting por IP, `noindex` y `Referrer-Policy: no-referrer`.
- El paciente puede convertir su enlace en una cuenta si lo prefiere.

## Consecuencias

- Quien obtenga el enlace puede ver la página mientras no esté revocado; el PIN mitiga este riesgo cuando el consultorio lo activa.
- La nutrióloga decide qué indicadores ve cada paciente.

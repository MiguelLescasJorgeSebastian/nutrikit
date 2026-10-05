# 0002. Multi-tenant con base compartida y tenant_id

- Estado: Aceptada
- Fecha: 2026-10-05

## Contexto

Varios consultorios usan la misma instalación y sus datos son de salud: una fuga entre consultorios es el peor fallo posible. El costo de infraestructura debe caber en una mensualidad baja.

## Opciones consideradas

| Opción | Aislamiento | Costo de operación |
| --- | --- | --- |
| Base de datos por consultorio | Máximo | Alto: una base y migraciones por cliente |
| Esquema por consultorio | Alto | Medio: migraciones por esquema |
| Base compartida con `tenant_id` | Medio, reforzado con RLS | Bajo |

## Decisión

Base compartida con columna `tenant_id NOT NULL` en toda tabla de negocio, con dos barreras:

1. La aplicación resuelve el consultorio (por subdominio o JWT) y filtra todas las consultas.
2. Row-Level Security de PostgreSQL como segunda barrera: la política compara `tenant_id` con una variable de sesión fijada en cada transacción.

## Consecuencias

- Los índices empiezan por `tenant_id`.
- Cada endpoint tiene una prueba automatizada que confirma que un usuario de un consultorio recibe 404 sobre recursos de otro.
- Si un cliente grande exige aislamiento físico, se puede mover a esquema propio sin cambiar el código de dominio.

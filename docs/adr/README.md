# Decisiones de arquitectura (ADR)

Cada decisión importante se registra en un archivo corto con su contexto, la decisión y sus consecuencias. Formato de [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

| # | Decisión | Estado |
| --- | --- | --- |
| [0001](0001-monolito-modular.md) | Monolito modular con Spring Modulith | Aceptada |
| [0002](0002-multi-tenant.md) | Multi-tenant con base compartida y `tenant_id` | Aceptada |
| [0003](0003-acceso-paciente-enlace.md) | Acceso del paciente por enlace privado | Aceptada |
| [0004](0004-implementacion-aislamiento-tenant.md) | Implementación del aislamiento entre consultorios | Aceptada |
| [0005](0005-autenticacion-jwt.md) | Autenticación con JWT propios y refresh tokens rotativos | Aceptada |
| [0006](0006-bitacora-de-auditoria.md) | Bitácora de auditoría con eventos de dominio | Aceptada |
| [0007](0007-modulos-por-consultorio.md) | Módulos encendidos y apagados por consultorio | Aceptada |
| [0008](0008-pacientes.md) | Pacientes: baja lógica, búsqueda y ediciones concurrentes | Aceptada |
| [0009](0009-cifrado-de-campos.md) | Cifrado de campos con llaves por consultorio (envelope encryption) | Aceptada |
| [0010](0010-consentimiento.md) | Aviso de privacidad y consentimiento del paciente | Aceptada |
| [0011](0011-consultas-y-mediciones.md) | Consultas y mediciones con catálogo de indicadores | Aceptada |
| [0012](0012-indicadores-calculados.md) | Indicadores calculados: dominio puro, guardados y recalculados | Aceptada |
| [0013](0013-lectura-de-series.md) | Series de indicadores: lectura con SQL y sin entidades | Aceptada |
| [0014](0014-enlace-privado-del-paciente.md) | Implementación del enlace privado del paciente | Aceptada |

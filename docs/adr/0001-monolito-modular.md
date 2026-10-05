# 0001. Monolito modular con Spring Modulith

- Estado: Aceptada
- Fecha: 2026-10-05

## Contexto

El sistema empieza con un solo consultorio y un desarrollador, pero debe crecer a muchos nutriólogos. Cada consultorio debe poder apagar módulos que no use (agenda, página web, planes) sin que afecten al resto. Los microservicios desde el inicio añadirían despliegues, redes y consistencia distribuida sin un beneficio real a esta escala.

## Decisión

Un solo desplegable de Spring Boot organizado por módulos de negocio (un paquete por *bounded context*), con estas reglas:

- Cada módulo es dueño de sus tablas y nunca lee las de otro módulo.
- Entre módulos solo se guardan referencias por ID, sin llaves foráneas en la base.
- La comunicación entre módulos es por su API pública o por eventos de dominio, persistidos en el registro de eventos de Spring Modulith (outbox).
- Un test de arquitectura (`ModularityTests`) falla el build si un módulo usa clases internas de otro o si aparece un ciclo.

## Consecuencias

- Un módulo opcional se puede apagar por consultorio con un *feature flag* (`tenant_modulo`).
- Extraer un módulo a un microservicio es mover su paquete y cambiar el bus de eventos interno por Kafka o RabbitMQ, sin rediseñar el esquema.
- La integridad entre módulos se valida en la aplicación y con eventos, no con *constraints*: requiere pruebas de esos flujos.

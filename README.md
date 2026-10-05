# nutrikit

Plataforma web multi-tenant para nutriólogos: expediente, consultas con mediciones y gráficas de progreso, planes de alimentación con el Sistema Mexicano de Alimentos Equivalentes, y una página privada para cada paciente, sin registro.

> Estado: fase 0 (fundamentos). Ver el [modelo de datos](docs/modelo-de-datos.pdf) y las [decisiones de arquitectura](docs/adr/).

## Arquitectura

- **Monolito modular** con Spring Modulith: cada módulo es dueño de sus tablas, se comunica por eventos y se puede encender o apagar por consultorio ([ADR-0001](docs/adr/0001-monolito-modular.md)).
- **Multi-tenant** en una sola base con `tenant_id` y Row-Level Security ([ADR-0002](docs/adr/0002-multi-tenant.md)).
- **Datos de salud** tratados como datos sensibles (LFPDPPP, NOM-004): cifrado de campos, bitácora de auditoría y acceso del paciente por enlace privado ([ADR-0003](docs/adr/0003-acceso-paciente-enlace.md)).

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security, Spring Data JPA |
| Datos | PostgreSQL 17, Flyway |
| Pruebas | JUnit 5, Testcontainers, pruebas de arquitectura de Modulith |
| Frontend | React + TypeScript + Vite (pendiente) |
| Infra | Docker Compose, GitHub Actions, AWS + Terraform (pendiente) |

## Estructura

```
nutrikit/
├── backend/     # Spring Boot: un módulo por paquete en dev.jorgemiguel.nutrikit
├── frontend/    # React (pendiente)
├── infra/       # Terraform y despliegue (pendiente)
└── docs/        # ADRs y modelo de datos
```

## Correr en local

Requisitos: JDK 25 y Docker Desktop abierto.

```bash
cd backend
./mvnw spring-boot:run   # levanta Postgres con compose.yaml y la app en :8080
./mvnw verify            # pruebas, incluida la verificación de módulos
```

`./mvnw verify` genera diagramas de los módulos en `backend/target/spring-modulith-docs`.

## Datos

El repositorio y cualquier demo usan solo datos ficticios.

# Cómo contribuir

Este repositorio sigue [GitHub Flow](https://docs.github.com/es/get-started/using-github/github-flow): `main` siempre está en verde y se puede desplegar, y todo cambio entra por Pull Request desde una rama corta.

## Flujo

1. Cada cambio empieza con un issue. Si no existe, créalo antes de escribir código.
2. Asígnate el issue y comenta "Empiezo".
3. Crea una rama desde `main` actualizado.
4. Haz commits pequeños que compilen.
5. Abre el PR en cuanto tengas algo que mostrar; usa *Draft* si aún no está listo.
6. Cuando cumpla la [definición de terminado](#definición-de-terminado), pide revisión.
7. Se integra con **squash merge** y la rama se borra sola.

## Ramas

Formato: `tipo/numero-descripcion-corta`, en minúsculas y con guiones.

```
feat/5-entidad-tenant
fix/12-slug-duplicado
docs/10-diagramas-c4
```

- `tipo` es uno de los tipos de commit de la tabla de abajo.
- `numero` es el issue que resuelve la rama.
- Una rama resuelve un solo issue y vive pocos días. Si crece demasiado, divide el issue.
- Antes de pedir revisión, actualiza la rama con `main` (*Update branch* en el PR o `git rebase main`).

## Commits

Se usa [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/):

```
tipo(alcance): descripción en imperativo y minúsculas

Cuerpo opcional: qué cambia y por qué, no cómo.

Closes #5
```

| Tipo | Cuándo |
| --- | --- |
| `feat` | Funcionalidad nueva para el usuario |
| `fix` | Corrección de un error |
| `docs` | Solo documentación |
| `test` | Agregar o corregir pruebas |
| `refactor` | Cambio de código que no altera el comportamiento |
| `perf` | Mejora de rendimiento |
| `build` | Maven, dependencias, Docker |
| `ci` | Jenkins y automatización |
| `chore` | Mantenimiento que no encaja en los anteriores |

- **Alcance:** el módulo afectado (`tenants`, `iam`, `audit`, `patients`...) o el área (`backend`, `frontend`, `infra`).
- **Descripción:** máximo 72 caracteres, sin punto final. Ejemplo: `feat(tenants): validar formato del slug`.
- **Cambio incompatible:** agrega `!` después del tipo (`feat(iam)!: ...`) y explica en el cuerpo con `BREAKING CHANGE:`.
- **Migraciones de Flyway:** usa el tipo del cambio que la motiva (`feat` si agrega una tabla para una función nueva) y el módulo como alcance. Nunca edites una migración que ya está en `main`; crea una nueva.

## Pull Requests

- **Título:** en formato Conventional Commits. Con squash merge, el título del PR se convierte en el commit que queda en `main`.
- **Descripción:** llena la plantilla. Incluye `Closes #N` para que el issue se cierre al integrar.
- **Tamaño:** procura que se pueda revisar en menos de 30 minutos (unas 400 líneas). Si es más grande, divídelo.
- **Revisión:** todas las conversaciones deben quedar resueltas antes de integrar.
- **Integración:** solo squash merge. No hay push directo a `main`.

## Definición de terminado

Un cambio está terminado cuando:

- [ ] Cumple todos los criterios de aceptación del issue.
- [ ] `./mvnw clean verify` termina en `BUILD SUCCESS`, incluida la verificación de módulos de Spring Modulith.
- [ ] La lógica nueva tiene pruebas; los errores corregidos tienen una prueba que los reproduce.
- [ ] Los cambios de esquema van en una migración nueva de Flyway.
- [ ] Ningún módulo usa clases internas de otro módulo.
- [ ] Toda tabla con datos de un consultorio tiene `tenant_id`.
- [ ] No hay secretos, credenciales ni datos reales de pacientes en el código, las pruebas o los logs.
- [ ] La documentación afectada está actualizada (README, ADR, diagramas).
- [ ] El PR usa la plantilla y responde las preguntas para reflexionar del issue.

## Datos

El repositorio y cualquier demo usan solo datos ficticios. Si encuentras datos reales, avisa en un issue sin copiarlos.

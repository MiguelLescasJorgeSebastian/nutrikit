# Jenkins local

Integración continua de nutrikit en Docker. Toda la configuración es código:

| Archivo | Qué define |
| --- | --- |
| [`Dockerfile`](Dockerfile) | Jenkins LTS, JDK 25 para compilar y los plugins |
| [`plugins.txt`](plugins.txt) | Plugins con versión fija |
| [`casc/jenkins.yaml`](casc/jenkins.yaml) | Usuario, seguridad, JDK, credenciales y el Multibranch Pipeline ([Configuration as Code](https://www.jenkins.io/projects/jcasc/)) |
| [`compose.yaml`](compose.yaml) | Contenedor, volumen persistente y acceso a Docker para Testcontainers |
| [`../../Jenkinsfile`](../../Jenkinsfile) | Las etapas del pipeline |

No hay asistente de instalación ni configuración a mano: lo que se cambia en la interfaz se pierde al reiniciar.

## Levantarlo desde cero

Requisitos: Docker (Docker Desktop en Mac o Windows; Docker Engine en Linux).

```bash
cd infra/jenkins
cp .env.example .env
```

Edita `.env`:

1. `JENKINS_ADMIN_PASSWORD`: genera una con `openssl rand -base64 24`.
2. `GITHUB_USER` y `GITHUB_TOKEN` (recomendado): crea un [token fine-grained](https://github.com/settings/personal-access-tokens/new) solo para este repositorio, con permisos de solo lectura en *Contents* y *Pull requests*. Sin token, Jenkins usa la API de GitHub de forma anónima (60 peticiones por hora) y los escaneos se frenan.
3. En Linux, `DOCKER_GID`: el resultado de `stat -c %g /var/run/docker.sock`. En Docker Desktop déjalo en `0`.

```bash
docker compose up -d --build
docker compose logs -f jenkins   # espera "Jenkins is fully up and running"
```

Abre <http://localhost:8081> y entra con el usuario y la contraseña de `.env`.

El job **nutrikit** ya existe y escanea el repositorio al arrancar: crea un job por cada rama o Pull Request que tenga `Jenkinsfile` y lo construye.

El primer build tarda más (unos 11 minutos) porque descarga Maven y todas las dependencias. Se quedan en el volumen `jenkins_home` (`~/.m2`), así que los siguientes no descargan nada y tardan unos 6 minutos, casi todo en las pruebas.

## El pipeline

`Jenkinsfile`, en la raíz del repositorio:

1. **Checkout** de la rama o del PR. Los PR se construyen como si ya estuvieran integrados en `main`.
2. **Build y pruebas:** `./mvnw clean verify` con el JDK `temurin-25`. Incluye las pruebas con Testcontainers y la verificación de módulos de Spring Modulith. Si una prueba falla, el build queda en rojo.
3. **Siempre**, pase o falle:
   - Publica los resultados de JUnit: en cada build, *Tests*; en el job, la tendencia.
   - Archiva `backend/target/spring-modulith-docs` como artifact descargable.

## Cómo se entera Jenkins de los cambios

Jenkins corre en tu laptop y GitHub no puede llamarlo por un webhook. Por eso el job revisa el repositorio **cada 5 minutos** (`periodicFolderTrigger`). Para construir de inmediato: job **nutrikit** → *Scan Repository Now*.

Para recibir webhooks hace falta exponer Jenkins a internet (un servidor, o un túnel como ngrok o Cloudflare Tunnel). En ese caso se agrega el webhook en GitHub y se quita el escaneo periódico.

## Testcontainers dentro de Jenkins

Las pruebas levantan Postgres con Testcontainers. Jenkins no trae su propio Docker: usa el del host montando `/var/run/docker.sock`. Los contenedores de prueba corren en el host, así que el JVM de las pruebas llega a ellos por `host.docker.internal` (`TESTCONTAINERS_HOST_OVERRIDE`).

**Riesgo:** quien controla el socket de Docker controla el host (puede montar cualquier carpeta o correr contenedores privilegiados). Cualquier `Jenkinsfile` que Jenkins ejecute tiene ese poder, así que esto es aceptable solo para un Jenkins local que construye tu propio repositorio. En un servidor compartido, los builds van en agentes aislados (por ejemplo, máquinas efímeras o Docker-in-Docker en modo rootless) y el controlador no ejecuta builds (`numExecutors: 0`).

## Comprobar que un build falla en rojo

Sin subir una prueba rota al repositorio: en un build verde → *Replay*, y agrega antes de la etapa *Build y pruebas* una etapa que escriba una prueba que falle:

```groovy
stage('Prueba que falla a propósito') {
    steps {
        writeFile file: 'backend/src/test/java/dev/jorgemiguel/nutrikit/FallaAPropositoTest.java', text: '''
package dev.jorgemiguel.nutrikit;
class FallaAPropositoTest {
    @org.junit.jupiter.api.Test
    void demuestraQueElBuildQuedaEnRojo() {
        org.junit.jupiter.api.Assertions.assertEquals(1, 2, "falla inyectada con Replay");
    }
}
'''
    }
}
```

El build queda en rojo (`FAILURE`), *Tests* muestra la prueba fallida y los diagramas se archivan igual.

## Operación

```bash
docker compose ps                    # estado
docker compose logs -f jenkins       # logs
docker compose restart jenkins       # aplica cambios de casc/jenkins.yaml
docker compose up -d --build         # aplica cambios del Dockerfile o de plugins.txt
docker compose down                  # detiene, conserva jobs, historial y caché
docker compose down -v               # borra todo (volumen jenkins_home incluido)
```

### Actualizar Jenkins o los plugins

1. Cambia la versión de `jenkins/jenkins` en el `Dockerfile`.
2. En `plugins.txt`, quita las versiones (deja solo los nombres) y reconstruye: `docker compose build`.
3. Lee las versiones resueltas y fíjalas de nuevo:

   ```bash
   docker run --rm --entrypoint sh nutrikit-jenkins:local -c \
     'for f in /usr/share/jenkins/ref/plugins/*.jpi; do echo "$(basename $f .jpi):$(unzip -p $f META-INF/MANIFEST.MF | grep ^Plugin-Version | cut -d" " -f2 | tr -d "\r")"; done'
   ```

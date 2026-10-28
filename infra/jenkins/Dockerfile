# Jenkins para nutrikit: controlador LTS + JDK 25 y Node 24 para compilar + plugins fijos.
# Todo lo que no es imagen (usuario, JDK, credenciales, jobs) está en casc/jenkins.yaml.

# JDK de compilación: el backend usa Java 25. Jenkins mismo corre con el JDK 21 de su imagen.
FROM eclipse-temurin:25-jdk AS jdk

# Node para el frontend (npm ci, lint, pruebas y build). Misma distribución que la imagen de
# Jenkins (Debian trixie), así el binario encuentra las mismas bibliotecas del sistema.
FROM node:24.22.1-trixie-slim AS node

FROM jenkins/jenkins:2.580.1-lts-jdk21

COPY --from=jdk /opt/java/openjdk /opt/jdk-25

COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
USER root
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
USER jenkins

# Sin asistente de instalación: la configuración llega completa por Configuration as Code.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false" \
    CASC_JENKINS_CONFIG=/var/jenkins_conf/jenkins.yaml

# Versiones fijas: la misma imagen se construye igual hoy y en seis meses.
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

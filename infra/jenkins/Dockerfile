# Jenkins para nutrikit: controlador LTS + JDK 25 para compilar + plugins fijos.
# Todo lo que no es imagen (usuario, JDK, credenciales, jobs) está en casc/jenkins.yaml.

# JDK de compilación: el backend usa Java 25. Jenkins mismo corre con el JDK 21 de su imagen.
FROM eclipse-temurin:25-jdk AS jdk

FROM jenkins/jenkins:2.580.1-lts-jdk21

COPY --from=jdk /opt/java/openjdk /opt/jdk-25

# Sin asistente de instalación: la configuración llega completa por Configuration as Code.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false" \
    CASC_JENKINS_CONFIG=/var/jenkins_conf/jenkins.yaml

# Versiones fijas: la misma imagen se construye igual hoy y en seis meses.
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

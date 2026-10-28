// Pipeline de integración continua de nutrikit. Lo ejecuta el Multibranch Pipeline de
// infra/jenkins para cada rama y cada Pull Request.

pipeline {
    agent any

    tools {
        // JDK 25 del contenedor de Jenkins (infra/jenkins/casc/jenkins.yaml).
        jdk 'temurin-25'
    }

    options {
        // El checkout es una etapa explícita para verlo en la vista del pipeline.
        skipDefaultCheckout()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        // Un push nuevo a la misma rama cancela el build anterior: solo importa el último.
        disableConcurrentBuilds(abortPrevious: true)
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
    }

    environment {
        // -B sin colores ni prompts; -ntp sin el progreso de cada descarga en el log.
        MAVEN_ARGS = '-B -ntp'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build y pruebas') {
            steps {
                dir('backend') {
                    sh 'java -version'
                    // verify: compila, corre las pruebas (Testcontainers incluido) y la verificación
                    // de módulos de Spring Modulith. Si una prueba falla, el comando falla y el build
                    // queda en rojo.
                    sh './mvnw clean verify'
                }
            }
        }
    }

    post {
        // always: los reportes se publican también cuando las pruebas fallan, que es cuando más
        // se necesitan.
        always {
            junit testResults: 'backend/target/surefire-reports/TEST-*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'backend/target/spring-modulith-docs/**',
                    allowEmptyArchive: true, fingerprint: true
        }
    }
}

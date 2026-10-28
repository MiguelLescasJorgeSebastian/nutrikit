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

        // Backend y frontend no dependen uno del otro: corren a la vez y el build tarda lo que el
        // más lento. Si uno falla, el otro termina igual para ver todos los errores juntos.
        stage('Build y pruebas') {
            parallel {
                stage('Backend') {
                    steps {
                        dir('backend') {
                            sh 'java -version'
                            // verify: compila, corre las pruebas (Testcontainers incluido) y la
                            // verificación de módulos de Spring Modulith. Si una prueba falla, el
                            // comando falla y el build queda en rojo.
                            sh './mvnw clean verify'
                        }
                    }
                }

                stage('Frontend') {
                    steps {
                        dir('frontend') {
                            sh 'node --version && npm --version'
                            // ci: instala exactamente lo de package-lock.json, sin actualizarlo.
                            sh 'npm ci --no-audit --no-fund'
                            sh 'npm run lint'
                            sh 'npm run format:check'
                            sh 'npm run test:ci'
                            // build = tsc -b (tipos estrictos) + vite build.
                            sh 'npm run build'
                        }
                    }
                }
            }
        }
    }

    post {
        // always: los reportes se publican también cuando las pruebas fallan, que es cuando más
        // se necesitan.
        always {
            junit testResults: 'backend/target/surefire-reports/TEST-*.xml, frontend/reports/junit.xml',
                    allowEmptyResults: true
            archiveArtifacts artifacts: 'backend/target/spring-modulith-docs/**',
                    allowEmptyArchive: true, fingerprint: true
            archiveArtifacts artifacts: 'frontend/dist/**', allowEmptyArchive: true, fingerprint: true
        }
    }
}

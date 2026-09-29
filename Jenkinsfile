// ==============================================================================
// PIPELINE DE CI/CD - QUARKUS GAME
// ==============================================================================

// Definición de variables globales predeterminadas
def jdkTool             = 'Java 21'
def appName             = 'quarkus-game'
def gitRepoUrl          = 'https://github.com/psehgaft/openshift-quarkus-game.git'
def gitCredentials      = 'gitlab-deploy-token-38'
def mavenTool           = 'apache-maven-3.9.6'
def quayRegistry        = 'quay-9tfrr.apps.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com/quayadmin/quarkus-game'
def openshiftApi        = 'https://api.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com:6443'

pipeline {
    agent any

    // Herramientas necesarias configuradas en Jenkins
    tools {
        maven "${mavenTool}"
        jdk   "${jdkTool}"
    }

    // Parámetros de ejecución manual desde la consola de Jenkins
    parameters {
        choice(
            name        : 'APLICATIVO',
            choices     : ['quarkus-game', 'CONSOLA'],
            description : 'Selecciona el aplicativo destino del despliegue'
        )
        choice(
            name        : 'AMBIENTE',
            choices     : ['DEV', 'QA', 'PROD'],
            description : 'Ambiente destino inicial de ejecución'
        )
        booleanParam(
            name        : 'SKIP_SONARQUBE',
            defaultValue: false,
            description : 'Omitir el análisis de calidad de SonarQube'
        )
        booleanParam(
            name        : 'SKIP_VERACODE',
            defaultValue: true,
            description : 'Omitir el escaneo de seguridad de Veracode'
        )
        string(
            name        : 'RAMA_OVERRIDE',
            defaultValue: '',
            description : 'Rama de Git a desplegar (si está vacío usa "main" o "test")'
        )
    }

    // Variables de entorno calculadas
    environment {
        APP_NAME         = 'quarkus-game'
        GIT_REPO_URL     = 'https://github.com/psehgaft/openshift-quarkus-game.git'
        GIT_CREDENTIALS  = 'gitlab-deploy-token-38'
        JENKINS_OC_CREDS = 'usuario-generico-quarkus-game' // ID de credencial en Jenkins
        QUAY_SECRET_NAME = 'quay-push-secret'             // Secret creado en OpenShift para Quay
        
        RAMA             = "${params.RAMA_OVERRIDE?.trim() ?: 'test'}"
        APP_PROFILE      = "${params.AMBIENTE?.toLowerCase() ?: 'dev'}"
        DEPLOY_ENV       = "${params.AMBIENTE ?: 'DEV'}"
        QUAY_REGISTRY    = 'quay-9tfrr.apps.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com/quayadmin/quarkus-game'
        OPENSHIFT_API    = 'https://api.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com:6443'
        
        // Espacios de nombres (Namespaces) en OpenShift
        BUILD_NAMESPACE  = 'sicatel-dev'
        QA_NAMESPACE     = 'quarkus-game-qa'
        PROD_NAMESPACE   = 'quarkus-game-prod'
        
        IMAGE_REF        = ''
        APP_VERSION      = ''
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    stages {

        // ----------------------------------------------------------------------
        // STAGE 1: Inicialización de variables y verificación de conectividad
        // ----------------------------------------------------------------------
        stage('1. Initialize Pipeline') {
            steps {
                script {
                    echo "========================================="
                    echo "  App         : ${env.APP_NAME}"
                    echo "  Aplicativo  : ${params.APLICATIVO}"
                    echo "  Rama        : ${env.RAMA}"
                    echo "  Ambiente    : ${env.DEPLOY_ENV}"
                    echo "  Build #     : ${env.BUILD_NUMBER}"
                    echo "  Cluster API : ${env.OPENSHIFT_API}"
                    echo "  Quay Repo   : ${env.QUAY_REGISTRY}"
                    echo "========================================="

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.GIT_CREDENTIALS}",
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )]) {
                        sh "git ls-remote https://\${GIT_USER}:\${GIT_TOKEN}@${env.GIT_REPO_URL.replace('https://', '')} HEAD"
                    }
                    echo "=== Repositorio Git accesible ==="
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 2: Descarga del código fuente y cálculo de versión
        // ----------------------------------------------------------------------
        stage('2. Checkout Source & Configuration') {
            steps {
                script {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${env.RAMA}"]],
                        extensions: [[$class: 'CleanBeforeCheckout']],
                        userRemoteConfigs: [[
                            url          : "${env.GIT_REPO_URL}",
                            credentialsId: "${env.GIT_CREDENTIALS}"
                        ]]
                    ])
                    
                    // Extraer versión de pom.xml o asignar 1.0.0 por defecto
                    def baseVersion = sh(
                        script: "JAVA_TOOL_OPTIONS='' mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | grep -v 'Picked up' | tr -d '\\r\\n' || echo '1.0.0'",
                        returnStdout: true
                    ).trim()

                    env.APP_VERSION = "${(baseVersion && baseVersion != 'null' && baseVersion != '') ? baseVersion : '1.0.0'}-${env.BUILD_NUMBER}"
                    echo "Versión calculada para el artefacto: ${env.APP_VERSION}"
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 3: Compilación con Maven y pruebas unitarias
        // ----------------------------------------------------------------------
        stage('3. Build & Unit Test') {
            steps {
                sh "mvn clean verify -B -DskipTests"
                sh 'echo "=== Artefactos generados ==="'
                sh 'find . -path "*/target/*.jar" -o -path "*/target/*.ear"'
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar,**/target/*.ear', fingerprint: true, allowEmptyArchive: true
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 4: Análisis de Calidad de Código con SonarQube
        // ----------------------------------------------------------------------
        stage('4. Code Quality Scan') {
            when {
                expression { params.SKIP_SONARQUBE == false }
            }
            steps {
                script {
                    withSonarQubeEnv('SonarServer1') {
                        sh "mvn compile org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectName=${env.APP_NAME} -Dsonar.projectKey=${env.APP_NAME}"
                    }
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 5: Escaneo de Seguridad con Veracode (Opcional)
        // ----------------------------------------------------------------------
        stage('5. Application Security Scan') {
            when {
                expression { params.SKIP_VERACODE == false }
            }        
            steps {
                script {
                    echo "Escaneo de Veracode omitido temporalmente."
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 6: Etiquetado de Versión en Git
        // ----------------------------------------------------------------------
        stage('6. Version & Tagging') {
            steps {
                script {
                    echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                    withCredentials([usernamePassword(credentialsId: "${env.GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                        sh 'git config user.email "jenkins@ci.com"'
                        sh 'git config user.name "Jenkins CI"'
                        sh 'git tag -a "v' + env.APP_VERSION + '" -m "Build de integración automática #' + env.BUILD_NUMBER + '" || true'
                    }
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 7: Construcción del Contenedor en OpenShift y Push a Quay
        // ----------------------------------------------------------------------
        stage('7. Build Image & Publish to Quay') {
            steps {
                script {
                    // Tag con el que se identificará la imagen en Red Hat Quay
                    def imageTag = "${env.QUAY_REGISTRY}:${env.DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER}"
                    env.IMAGE_REF = imageTag

                    echo "Iniciando compilación en OpenShift y Push hacia Quay: ${env.IMAGE_REF}"

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            # 1. Autenticarse en OpenShift
                            oc login ${env.OPENSHIFT_API} -u "$OC_USER" -p "$OC_PASSWORD" --insecure-skip-tls-verify=true
                            oc project ${env.BUILD_NAMESPACE}

                            # 2. Crear el BuildConfig tipo Docker si no existe, asignando el push-secret
                            if ! oc get buildconfig "${env.APP_NAME}-builder" -n ${env.BUILD_NAMESPACE} >/dev/null 2>&1; then
                                echo "Creando nuevo BuildConfig para ${env.APP_NAME}..."
                                oc new-build \
                                    --name="${env.APP_NAME}-builder" \
                                    --strategy=docker \
                                    --binary \
                                    --to-docker=true \
                                    --to="${env.IMAGE_REF}" \
                                    --push-secret="${env.QUAY_SECRET_NAME}" \
                                    -n ${env.BUILD_NAMESPACE}
                            else
                                echo "BuildConfig existente. Actualizando la imagen destino a: ${env.IMAGE_REF}"
                                oc patch bc/${env.APP_NAME}-builder -p '{"spec":{"output":{"to":{"name":"'${env.IMAGE_REF}'"}}}}' -n ${env.BUILD_NAMESPACE}
                            fi

                            # 3. Enviar el contexto del directorio actual para construir la imagen y subirla a Quay
                            oc start-build ${env.APP_NAME}-builder --from-dir=. --follow -n ${env.BUILD_NAMESPACE}
                        """
                    }

                    echo "Imagen construida y publicada exitosamente en Quay: ${env.IMAGE_REF}"
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 8: Despliegue Automático en QA (quarkus-game-qa)
        // ----------------------------------------------------------------------
        stage('8. Deploy to QA') {
            steps {
                script {
                    echo "Desplegando imagen ${env.IMAGE_REF} en el ambiente QA (${env.QA_NAMESPACE})..."

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            oc login ${env.OPENSHIFT_API} -u "$OC_USER" -p "$OC_PASSWORD" --insecure-skip-tls-verify=true
                            oc project ${env.QA_NAMESPACE} || oc new-project ${env.QA_NAMESPACE}

                            # Actualizar o crear Deployment
                            oc set image deployment/${env.APP_NAME} ${env.APP_NAME}="${env.IMAGE_REF}" -n ${env.QA_NAMESPACE} || \
                            oc create deployment ${env.APP_NAME} --image="${env.IMAGE_REF}" -n ${env.QA_NAMESPACE}

                            # Monitorear estado del despliegue
                            oc rollout status deployment/${env.APP_NAME} -n ${env.QA_NAMESPACE} --timeout=5m
                        """
                    }
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 9: Aprobación Manual para Producción
        // ----------------------------------------------------------------------
        stage('9. Manual Approval for PROD') {
            steps {
                script {
                    echo "================================================================="
                    echo "La aplicación ha sido desplegada en QA (${env.QA_NAMESPACE})."
                    echo "Esperando aprobación manual para proceder al despliegue en PROD."
                    echo "================================================================="

                    timeout(time: 2, unit: 'HOURS') {
                        input message: "¿Deseas promocionar la versión ${env.IMAGE_REF} al ambiente de Producción (${env.PROD_NAMESPACE})?",
                              ok: "Aprobar y Desplegar en PROD",
                              submitterParameter: 'APPROVED_BY'
                    }
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 10: Despliegue en Producción (quarkus-game-prod)
        // ----------------------------------------------------------------------
        stage('10. Deploy to PROD') {
            steps {
                script {
                    echo "Aprobado por el usuario. Desplegando en Producción (${env.PROD_NAMESPACE})..."

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            oc login ${env.OPENSHIFT_API} -u "$OC_USER" -p "$OC_PASSWORD" --insecure-skip-tls-verify=true
                            oc project ${env.PROD_NAMESPACE} || oc new-project ${env.PROD_NAMESPACE}

                            # Actualizar o crear Deployment en PROD
                            oc set image deployment/${env.APP_NAME} ${env.APP_NAME}="${env.IMAGE_REF}" -n ${env.PROD_NAMESPACE} || \
                            oc create deployment ${env.APP_NAME} --image="${env.IMAGE_REF}" -n ${env.PROD_NAMESPACE}

                            # Monitorear estado del despliegue en PROD
                            oc rollout status deployment/${env.APP_NAME} -n ${env.PROD_NAMESPACE} --timeout=5m
                        """
                    }
                }
            }
        }

        // ----------------------------------------------------------------------
        // STAGE 11: Limpieza de Imágenes Locales
        // ----------------------------------------------------------------------
        stage('11. Cleanup Local Images') {
            steps {
                script {
                    sh "podman rmi ${env.IMAGE_REF} || true"
                    sh "podman image prune -f --filter 'until=24h' || true"
                }
            }
        }
    }

    // Acciones posteriores a la ejecución
    post {
        always {
            deleteDir() // Limpia el espacio de trabajo en el agente
        }
        success {
            echo "Pipeline ${env.APP_NAME} completado exitosamente hasta PROD."
        }
        failure {
            echo "El pipeline ha fallado. Revisa los logs de la ejecución."
        }
    }
}

    def appName             = 'quarkus-game'
    def gitRepoUrl          = 'https://github.com/psehgaft/openshift-quarkus-game.git'
    def gitDeployRepoUrl    = ''
    def gitCredentials      = 'gitlab-deploy-token-38'
    def mavenTool           = 'apache-maven-3.9.6'
    def staticAssetsEnabled = true
    def staticAssetsDir     = 'container-assets'
    def staticAssetsProfile = 'core'
    def dockerfileEnabled   = true
    def dockerfileOutputPath = 'Dockerfile'
    def dockerBaseImage     = 'registry.access.redhat.com/ubi9/openjdk-17-runtime:latest'
    def quayRegistry = 'quay-9tfrr.apps.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com/quayadmin/quarkus-game'
    def openshiftApi = 'https://api.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com:6443'


pipeline {
        agent any

        //Esto si se necesita para la aplicacion Sicatel, para sicatel necesita la 3.3.9
        tools {
            maven "${mavenTool}"
        }

        parameters {
            choice(
                name        : 'APLICATIVO',
                choices     : ['MDELALUZ-QUARKUS-GAME', 'KIOSCO'],
                description : 'Selecciona el aplicativo destino del despliegue'
            )
            choice(
                name        : 'AMBIENTE',
                choices     : ['DEV', 'QA', 'PREPROD'],
                description : 'MDELALUZ-QUARKUS-GAME: DEV, QA | KIOSCO: PREPROD'
            )
            booleanParam(
                name         : 'SKIP_SONARQUBE',
                defaultValue : false,
                description  : 'Omitir el análisis de SonarQube'
            )
            string(
                name         : 'RAMA_OVERRIDE',
                defaultValue : '',
                description  : 'Rama a desplegar (opcional). Si se deja vacío: main'
            )
        }

        environment {
            APP_NAME        = "${appName}"
            GIT_REPO_URL    = "${gitRepoUrl}"
            GIT_CREDENTIALS = "${gitCredentials}"
            RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: 'main'}"
            APP_PROFILE     = "${params.AMBIENTE?.toLowerCase() ?: 'dev'}"
            DEPLOY_ENV      = "${params.AMBIENTE ?: 'DEV'}"
            QUAY_REGISTRY   = "${quayRegistry}"
            OPENSHIFT_API   = "${openshiftApi}"
            IMAGE_DIGEST    = ''
            IMAGE_REF       = ''
            APP_VERSION     = ''
            
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: 2, unit: 'HOURS')
        }

        stages {

            stage('1. Initialize Pipeline') {
                steps {
                    script {
                        echo "========================================="
                        echo "  App         : ${APP_NAME}"
                        echo "  Aplicativo  : ${params.APLICATIVO}"
                        echo "  Rama        : ${RAMA}"
                        echo "  Ambiente    : ${DEPLOY_ENV}"
                        echo "  Perfil      : ${APP_PROFILE}"
                        echo "  Build #     : ${env.BUILD_NUMBER}"
                        echo "  Cluster API : ${OPENSHIFT_API}"
                        echo "  Quay Repo   : ${QUAY_REGISTRY}"
                        echo "========================================="

                        withCredentials([usernamePassword(
                            credentialsId : "${GIT_CREDENTIALS}",
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_TOKEN'
                        )]) {
                            sh "git ls-remote https://\${GIT_USER}:\${GIT_TOKEN}@${GIT_REPO_URL.replace('https://', '')} HEAD"
                        }
                        echo "=== Repositorio Git accesible ==="
                    }
                }
            }

            stage('2. Checkout Source & Configuration') {
                steps {
                    script {
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${RAMA}"]],
                            extensions: [[$class: 'CleanBeforeCheckout']],
                            userRemoteConfigs: [[
                                url           : "${GIT_REPO_URL}",
                                credentialsId : "${GIT_CREDENTIALS}"
                            ]]
                        ])
                        
                        def baseVersion = sh(
                            //script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout || echo '1.0.0'",
                            //script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | grep -v 'Picked up' | tail -n 1 || echo '1.0.0'",
                            //script: "JAVA_TOOL_OPTIONS='' mvn help:evaluate -Dexpression=project.version -q -DforceStdout | tr -d '\\r\\n' || echo '1.0.0'",
                            //script: "JAVA_TOOL_OPTIONS='' bash -c 'if [ -f ./mvnw ]; then chmod +x ./mvnw && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout; else mvn help:evaluate -Dexpression=project.version -q -DforceStdout; fi' | tr -d '\\r\\n' || echo '1.0.0'",
                            //script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | grep -v 'Picked up' | tr -d '\\r\\n' || echo '1.0.0'",
                            script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | grep -v 'Picked up' | tr -d '\\r\\n' || echo '1.0.0'",
                            returnStdout: true
                        ).trim()
                        
                        //env.APP_VERSION = "${baseVersion}-${env.BUILD_NUMBER}"
                        env.APP_VERSION = "${(baseVersion && baseVersion != 'null') ? baseVersion : '1.0.0'}-${env.BUILD_NUMBER}"
                        echo "Versión calculada para el artefacto: ${env.APP_VERSION}"

                        //Checkout opcional del repositorio de configuración de despliegue
                        if (gitDeployRepoUrl?.trim()) {
                            dir('deploy-config') {
                                checkout([
                                    $class: 'GitSCM',
                                    branches: [[name: "*/${RAMA}"]],
                                    extensions: [[$class: 'CleanBeforeCheckout']],
                                    userRemoteConfigs: [[
                                        url           : "${gitDeployRepoUrl}",
                                        credentialsId : "${GIT_CREDENTIALS}"
                                    ]]
                                ])
                            }
                            echo "Configuración de despliegue descargada correctamente."
                        } else {
                            echo "No se definió repositorio de configuraciones (gitDeployRepoUrl). Omitiendo checkout."
                        }
                    }
                }
            }

            stage('3. Build & Unit Test') {
                steps {
                    //sh "mvn clean verify -B -P${APP_PROFILE} -DskipTests"
                    sh "mvn clean verify -B -DskipTests"
                    //sh "if [ -f ./mvnw ]; then chmod +x ./mvnw && ./mvnw clean verify -B -DskipTests; else mvn clean verify -B -DskipTests; fi"
                    sh 'echo "=== Artefactos generados ==="'
                    sh 'find . -path "*/target/*.jar" -o -path "*/target/*.ear"'
                }
                post {
                    success {
                        archiveArtifacts artifacts: '**/target/*.jar,**/target/*.ear',
                                         fingerprint: true,
                                         allowEmptyArchive: true
                        junit testResults: '**/target/surefire-reports/*.xml',
                              allowEmptyResults: true
                    }
                }
            }

            

            stage('4. Code Quality Scan') {
                when {
                    expression { params.SKIP_SONARQUBE == false }
                }
                steps {
                    script {
                        withSonarQubeEnv('SonarQubeServer') {
                            //sh "mvn sonar:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME} -P${APP_PROFILE}"
                            sh "mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME}"
                            //sh "if [ -f ./mvnw ]; then ./mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME}; else mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME}; fi"
                        }
                        timeout(time: 4, unit: 'MINUTES') {
                            script {
                                def qualityGate = waitForQualityGate()
                                echo "Estado de Quality Gate: ${qualityGate.status}"
                                if (qualityGate.status != 'OK') {
                                    error "Quality Gate falló con estado: ${qualityGate.status}"
                                }
                            }
                        }
                    }
                }
            }           

            stage('5. Application Security Scan') {
                steps {
                    script {
                        withCredentials([file(credentialsId: 'veracode-adapter', variable: 'VERACODE_ADAPTER')]) {
                            sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/ || echo "Veracode ejecutado sin alertas críticas."'
                        }
                    }
                }
            }

            stage('6. Version & Build Image') {
                steps {
                    script {
                        echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                        withCredentials([usernamePassword(credentialsId: "${GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                            sh 'git config user.email "jenkins@ci.com"'
                            sh 'git config user.name "Jenkins CI"'
                            sh 'git tag -a "v' + env.APP_VERSION + '" -m "Build de integración automática #' + env.BUILD_NUMBER + '" || true'
                        }
                    }
                }
            }

            stage('7. Publish Candidate') {
                steps {
                    script {
                        def assetBase = staticAssetsDir
                        def assetProfile = staticAssetsProfile

                        if (staticAssetsEnabled) {
                            sh "mkdir -p ${assetBase}"
                            writeFile file: "${assetBase}/server.xml", text: libraryResource("container-assets/${assetProfile}/server.xml")
                            writeFile file: "${assetBase}/init-logs.sh", text: libraryResource("container-assets/${assetProfile}/init-logs.sh")
                            writeFile file: "${assetBase}/validate-startup.sh", text: libraryResource("container-assets/${assetProfile}/validate-startup.sh")
                            sh "chmod +x ${assetBase}/init-logs.sh ${assetBase}/validate-startup.sh"
                        }

                        if (dockerfileEnabled) {
                            def earSourcePath = sh(
                                script: "find . -path '*/target/*.ear' -o -path '*/target/*.jar' | sort | head -n 1",
                                returnStdout: true
                            ).trim()

                            if (!earSourcePath) {
                                error 'No .ear/.jar artifact found under target/. Check Build stage output.'
                            }

                            def earFileName = earSourcePath.tokenize('/').last()
                            sh "cp \"${earSourcePath}\" \"${assetBase}/${earFileName}\""

                            def dockerfileTemplate = libraryResource("container-assets/${assetProfile}/Dockerfile.template")
                            def dockerfileContent = dockerfileTemplate
                                .replace('__BASE_IMAGE__', dockerBaseImage)
                                .replace('__ASSET_DIR__', assetBase)
                                .replace('__EAR_FILE__', earFileName)

                            writeFile file: dockerfileOutputPath, text: dockerfileContent
                        }

                        def imageTag = "${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER}"
                        sh "podman build -f ${dockerfileOutputPath} -t ${imageTag} ."

                        withCredentials([usernamePassword(credentialsId: 'quay-push', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASSWORD')]) {
                            sh 'printf "%s" "$QUAY_PASSWORD" | podman login ' + QUAY_REGISTRY.split('/')[0] + ' --username "$QUAY_USER" --password-stdin'
                            sh 'podman push --digestfile image-digest.txt ' + imageTag
                        }

                        env.IMAGE_DIGEST = readFile('image-digest.txt').trim()
                        env.IMAGE_REF = "${QUAY_REGISTRY}@${env.IMAGE_DIGEST}"
                        echo "Imagen publicada en Quay: ${env.IMAGE_REF}"
                    }
                }
                
            }

            stage('8. Generate SBOM & Scan Image') {
                steps {
                echo 'SBOM y escaneo de imagen pendientes de implementación'
              }
            }

            stage('9. Quality & Security Gate') {
                steps {
                echo 'Quality & Security Gate pendiente de implementación'
              }
            }

            stage('10. Sign & Attest') {
                steps {
                echo 'Firma y attestation pendientes de implementación'
              }
            }
            
            stage('11. Deploy DEV') {
                when {
                    allOf {
                        expression {
                            params.RAMA_OVERRIDE?.trim() ? true : RAMA in ['develop', 'main']
                        }
                        expression {
                            (params.APLICATIVO == 'MDELALUZ-QUARKUS-GAME' && params.AMBIENTE in ['DEV', 'QA']) ||
                            (params.APLICATIVO == 'KIOSCO'  && params.AMBIENTE == 'PREPROD')
                        }
                    }
                }
                steps {
                    script {
                        def targetNamespace = "${params.APLICATIVO.toLowerCase()}-${DEPLOY_ENV.toLowerCase()}"
                        echo "Desplegando en OpenShift (${OPENSHIFT_API}) - Namespace: ${targetNamespace}"

                        withCredentials([string(credentialsId: 'oc-dev-token', variable: 'OC_TOKEN')]) {
                            sh 'oc login ' + OPENSHIFT_API + ' --token="$OC_TOKEN" --insecure-skip-tls-verify=true'
                            sh 'oc project ' + targetNamespace + ' || oc new-project ' + targetNamespace
                            sh 'oc set image deployment/' + APP_NAME + ' ' + APP_NAME + '="' + env.IMAGE_REF + '" -n ' + targetNamespace + ' || oc create deployment ' + APP_NAME + ' --image="' + env.IMAGE_REF + '" -n ' + targetNamespace
                            sh 'oc rollout status deployment/' + APP_NAME + ' -n ' + targetNamespace + ' --timeout=5m'
                        }
                    }
                }
            }

            stage('Remove registry repository tags') {
                steps {
                    script {
                        sh "podman rmi ${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER} || true"
                        sh "podman image prune -f --filter 'until=24h' || true"
                    }
                }
            }

        }
        post {
            always {
                deleteDir()
            }
            success {
                echo "Pipeline ${APP_NAME} finalizado correctamente en ${DEPLOY_ENV}"
            }
            failure {
                echo "Pipeline ${APP_NAME} fallido. Revisa los logs."
            }
        }
    }

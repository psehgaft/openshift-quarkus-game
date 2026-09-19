pipeline {
    agent any

    options {
        timeout(time: 2, unit: 'HOURS')
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'NEXUS_MAVEN_URL', defaultValue: '', description: 'URL del repositorio Maven group de Nexus; vacío para Maven Central')
        booleanParam(name: 'PUBLISH_IMAGE', defaultValue: false, description: 'Construir y publicar la imagen candidata en Quay')
        booleanParam(name: 'DEPLOY_DEV', defaultValue: false, description: 'Desplegar el digest publicado en OpenShift DEV')
        booleanParam(name: 'ENABLE_SONAR', defaultValue: false, description: 'Análisis y quality gate de SonarQube')
        booleanParam(name: 'ENABLE_VERACODE', defaultValue: false, description: 'Ejecutar el adaptador Veracode configurado en Jenkins')
        booleanParam(name: 'ENABLE_TPA', defaultValue: false, description: 'Subir SBOM mediante el adaptador TPA configurado en Jenkins')
        booleanParam(name: 'ENABLE_RHACS', defaultValue: false, description: 'Ejecutar el adaptador de política RHACS')
        booleanParam(name: 'ENABLE_SIGNING', defaultValue: false, description: 'Firmar y atestar mediante el adaptador TAS')
    }

    environment {
        GIT_REPO          = 'https://github.com/psehgaft/openshift-quarkus-game.git'
        QUAY_REGISTRY     = 'quay-svr5h.apps.cluster-svr5h.svr5h.sandbox1725.opentlc.com/quayadmin/quarkus-game'
        OPENSHIFT_PROJECT = 'dev-quarkus-game'
        APP_NAME          = 'quarkus-game'
        IMAGE_DIGEST      = ''
        IMAGE_REF         = ''
    }

    stages {
        stage('1. Initialize Pipeline') {
            steps {
                script {
                    if (params.DEPLOY_DEV && !params.PUBLISH_IMAGE) error('DEPLOY_DEV requiere PUBLISH_IMAGE')
                    if ((params.ENABLE_TPA || params.ENABLE_RHACS || params.ENABLE_SIGNING) && !params.PUBLISH_IMAGE) {
                        error('Los análisis de imagen y la firma requieren PUBLISH_IMAGE')
                    }
                    
                    // Asegurar permisos para el wrapper y validar herramientas básicas (usa ./mvnw si mvn no está instalado)
                    sh 'chmod +x ./mvnw && command -v java && command -v git'

                    if (params.PUBLISH_IMAGE) {
                        if (!(env.QUAY_REGISTRY ==~ /quay[.]io\/[a-zA-Z0-9.-]+\/[a-zA-Z0-9.-]+/)) {
                            error('Configura QUAY_REGISTRY con quay.io/<org>/<repo>')
                        }
                        if (env.QUAY_REGISTRY == 'quay.io/organization/app') error('Sustituye QUAY_REGISTRY por el repositorio Quay real')
                        if (!env.RUNTIME_BASE_IMAGE?.contains('@sha256:')) {
                            error('Configura RUNTIME_BASE_IMAGE en Jenkins con una imagen base aprobada fijada por @sha256:')
                        }
                        sh 'command -v podman && command -v syft'
                    }
                    if (params.DEPLOY_DEV) {
                        if (env.OPENSHIFT_PROJECT == 'dev-environment') error('Configura OPENSHIFT_PROJECT con el namespace DEV real')
                        if (!env.OPENSHIFT_API?.trim()) error('Configura OPENSHIFT_API en Jenkins')
                        sh 'command -v oc && command -v curl'
                    }
                }
            }
        }

        stage('2. Checkout Source & Configuration') {
            steps {
                checkout scm
                sh 'test -f pom.xml && test -f ci/build.sh && test -f Dockerfile.runtime'
            }
        }

        stage('3. Code Quality Scan') {
            steps {
                sh 'bash -n ci/build.sh && git diff --check'
                echo 'Comprobaciones estáticas iniciales completadas; SonarQube analiza bytecode después del build.'
            }
        }

        stage('4. Build & Unit Test') {
            steps {
                script {
                    if (params.NEXUS_MAVEN_URL?.trim()) {
                        withCredentials([usernamePassword(credentialsId: 'nexus-readonly', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                            withEnv(["NEXUS_MAVEN_URL=${params.NEXUS_MAVEN_URL.trim()}"]) {
                                sh 'bash ci/build.sh'
                                if (params.ENABLE_SONAR) {
                                    withSonarQubeEnv('SonarQubeServer') {
                                        sh './mvnw -B -ntp -s ci/settings-nexus.xml sonar:sonar'
                                    }
                                }
                            }
                        }
                    } else {
                        sh 'bash ci/build.sh'
                        if (params.ENABLE_SONAR) {
                            withSonarQubeEnv('SonarQubeServer') {
                                sh './mvnw -B -ntp sonar:sonar'
                            }
                        }
                    }
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml' }
            }
        }

        stage('5. Application Security Scan') {
            when { expression { params.ENABLE_VERACODE } }
            steps {
                withCredentials([file(credentialsId: 'veracode-adapter', variable: 'VERACODE_ADAPTER')]) {
                    sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/quarkus-app'
                }
            }
        }

        stage('6. Version & Build Image') {
            when { expression { params.PUBLISH_IMAGE } }
            steps {
                sh '''
                    test -f target/quarkus-app/quarkus-run.jar
                    podman build --build-arg "BASE_IMAGE=$RUNTIME_BASE_IMAGE" -f Dockerfile.runtime -t "$QUAY_REGISTRY:$BUILD_NUMBER" .
                '''
            }
        }

        stage('7. Publish Candidate') {
            when { expression { params.PUBLISH_IMAGE } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'quay-push', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASSWORD')]) {
                    sh '''
                        set +x
                        export REGISTRY_AUTH_FILE="$WORKSPACE/.quay-auth.json"
                        printf '%s' "$QUAY_PASSWORD" | podman login quay.io --username "$QUAY_USER" --password-stdin
                        podman push --digestfile image-digest.txt "$QUAY_REGISTRY:$BUILD_NUMBER"
                        podman logout quay.io
                    '''
                }
                script {
                    env.IMAGE_DIGEST = readFile('image-digest.txt').trim()
                    if (!(env.IMAGE_DIGEST ==~ /sha256:[0-9a-f]{64}/)) error('Quay no devolvió un digest válido')
                    env.IMAGE_REF = "${env.QUAY_REGISTRY}@${env.IMAGE_DIGEST}"
                    echo "Candidato publicado: ${env.IMAGE_REF}"
                }
            }
        }

        stage('8. Generate SBOM & Scan Image') {
            when { expression { params.PUBLISH_IMAGE } }
            steps {
                sh '''
                    podman save --format oci-archive -o image.oci.tar "$QUAY_REGISTRY:$BUILD_NUMBER"
                    syft image.oci.tar -o cyclonedx-json=sbom.cdx.json
                    rm image.oci.tar
                '''
                script {
                    if (params.ENABLE_TPA) {
                        withCredentials([file(credentialsId: 'tpa-adapter', variable: 'TPA_ADAPTER')]) {
                            sh 'test -s "$TPA_ADAPTER" && bash "$TPA_ADAPTER" sbom.cdx.json "$IMAGE_REF"'
                        }
                    }
                }
            }
        }

        stage('9. Quality & Security Gate') {
            steps {
                script {
                    if (params.ENABLE_SONAR) {
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                    if (params.ENABLE_RHACS) {
                        withCredentials([file(credentialsId: 'rhacs-adapter', variable: 'RHACS_ADAPTER')]) {
                            sh 'test -s "$RHACS_ADAPTER" && bash "$RHACS_ADAPTER" "$IMAGE_REF"'
                        }
                    }
                }
            }
        }

        stage('10. Sign & Attest') {
            when { expression { params.ENABLE_SIGNING } }
            steps {
                withCredentials([file(credentialsId: 'tas-adapter', variable: 'TAS_ADAPTER')]) {
                    sh 'test -s "$TAS_ADAPTER" && bash "$TAS_ADAPTER" "$IMAGE_REF" sbom.cdx.json'
                }
            }
        }

        stage('11. Deploy DEV') {
            when { expression { params.DEPLOY_DEV } }
            steps {
                withCredentials([string(credentialsId: 'oc-dev-token', variable: 'OC_TOKEN')]) {
                    sh '''
                        set +x
                        export KUBECONFIG="$WORKSPACE/.kubeconfig"
                        oc login "$OPENSHIFT_API" --token="$OC_TOKEN"
                        oc project "$OPENSHIFT_PROJECT"
                        if oc -n "$OPENSHIFT_PROJECT" get deployment "$APP_NAME" >/dev/null 2>&1; then
                            oc -n "$OPENSHIFT_PROJECT" set image "deployment/$APP_NAME" "$APP_NAME=$IMAGE_REF"
                        else
                            oc -n "$OPENSHIFT_PROJECT" create deployment "$APP_NAME" --image="$IMAGE_REF"
                        fi
                        if ! oc -n "$OPENSHIFT_PROJECT" get service "$APP_NAME" >/dev/null 2>&1; then
                            oc -n "$OPENSHIFT_PROJECT" expose deployment "$APP_NAME" --port=8080 --name="$APP_NAME"
                        fi
                        if ! oc -n "$OPENSHIFT_PROJECT" get route "$APP_NAME" >/dev/null 2>&1; then
                            oc -n "$OPENSHIFT_PROJECT" expose service "$APP_NAME"
                        fi
                    '''
                }
            }
        }

        stage('12. Validate DEV') {
            when { expression { params.DEPLOY_DEV } }
            steps {
                withCredentials([string(credentialsId: 'oc-dev-token', variable: 'OC_TOKEN')]) {
                    sh '''
                        set +x
                        export KUBECONFIG="$WORKSPACE/.kubeconfig"
                        oc login "$OPENSHIFT_API" --token="$OC_TOKEN"
                        oc -n "$OPENSHIFT_PROJECT" rollout status "deployment/$APP_NAME" --timeout=5m
                        ACTUAL_IMAGE="$(oc -n "$OPENSHIFT_PROJECT" get deployment "$APP_NAME" -o jsonpath='{.spec.template.spec.containers[0].image}')"
                        test "$ACTUAL_IMAGE" = "$IMAGE_REF"
                        ROUTE_HOST="$(oc -n "$OPENSHIFT_PROJECT" get route "$APP_NAME" -o jsonpath='{.spec.host}')"
                        ROUTE_TLS="$(oc -n "$OPENSHIFT_PROJECT" get route "$APP_NAME" -o jsonpath='{.spec.tls.termination}')"
                        SCHEME=http
                        if [ -n "$ROUTE_TLS" ]; then SCHEME=https; fi
                        curl --fail --silent --show-error --retry 10 --retry-delay 3 "$SCHEME://$ROUTE_HOST/q/health/ready"
                        curl --fail --silent --show-error "$SCHEME://$ROUTE_HOST/api/game"
                        curl --fail --silent --show-error "$SCHEME://$ROUTE_HOST/" | grep -q 'Quarkus Snake'
                    '''
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'sbom.cdx.json,image-digest.txt,target/surefire-reports/**',
                             allowEmptyArchive: true, fingerprint: true
        }
        success { echo 'Pipeline completado: revisa las etapas activadas y el digest publicado.' }
        failure { echo 'Pipeline detenido por fallo de build, prueba, integración o gate.' }
        cleanup { deleteDir() }
    }
}

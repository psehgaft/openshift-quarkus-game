# Quarkus Snake 🐍

Juego sencillo de Snake servido por Quarkus con HTML, CSS y JavaScript. Incluye una API REST y un marcador en memoria para probar despliegues en OpenShift.

## Jugar

Abre la ruta raíz de la aplicación y pulsa **Jugar / reiniciar**. Usa las flechas o **W A S D** para moverte; **Espacio** pausa y **R** reinicia. Al terminar la partida puedes guardar tu puntuación.

El récord personal queda en el navegador. El marcador de la API solo se guarda en memoria: se borra al reiniciar el proceso y no se comparte entre réplicas.

## Proyecto Maven y pruebas

Requisitos: Java 21 y Maven 3.9 o posterior. El [`pom.xml`](pom.xml) importa el BOM de Quarkus y declara `quarkus-rest-jackson`, `quarkus-smallrye-health`, `quarkus-junit5` y `rest-assured`. La meta `verify` compila, ejecuta las pruebas y genera `target/quarkus-app/`.

```bash
bash ci/build.sh
# Equivalente sin Nexus:
mvn -B -ntp clean verify
# Desarrollo local:
mvn quarkus:dev
```

- `ScoreBoardTest`: pruebas unitarias sin servidor para validación, orden, límite de diez resultados e inmutabilidad de la lista.
- `GameResourceTest`: prueba de integración con `@QuarkusTest` para la página y los endpoints REST.
- Reportes JUnit XML: `target/surefire-reports/TEST-*.xml`.
- Aplicación empaquetada: `target/quarkus-app/quarkus-run.jar` junto con el resto de `target/quarkus-app/`.

El workflow [Build and test](.github/workflows/build.yml) ejecuta estas pruebas en cada push y PR.

## Resolver dependencias con Nexus en el pipeline

El archivo [`ci/settings-nexus.xml`](ci/settings-nexus.xml) configura un único espejo Maven con `mirrorOf=*`. Debes proporcionar **la URL del repositorio Maven group** de tu Nexus, que incluya o actúe como proxy de Maven Central y contenga los plugins y las dependencias Quarkus necesarias. Es la URL de `.../repository/<grupo-maven>/`, no la página web de Nexus. Maven usa el mismo servidor para resolver dependencias y plugins. La configuración mediante `settings.xml` sigue la [documentación oficial de Maven](https://maven.apache.org/guides/mini/guide-mirror-settings.html).

Inyecta estas variables como secretos/variables del pipeline:

| Variable | Descripción |
| --- | --- |
| `NEXUS_MAVEN_URL` | URL completa del repositorio group de Nexus |
| `NEXUS_USERNAME` | Usuario de solo lectura de Nexus |
| `NEXUS_PASSWORD` | Contraseña/token del usuario |

Por ejemplo, en un paso de CI que ya haya inyectado las variables:

```bash
bash ci/build.sh
```

El script usa `mvn -B -ntp -s ci/settings-nexus.xml clean verify` cuando existe `NEXUS_MAVEN_URL`. Falla si falta alguna credencial. Sin variables Nexus usa Maven Central con la configuración normal de Maven. No pongas URL interna ni credenciales reales en el POM, el script o Git. En Jenkins usa Credentials Binding; en Tekton/OpenShift entrega las variables desde un Secret al paso Maven. Si tu Nexus permite lectura anónima, crea credenciales de solo lectura para este ejemplo o adapta `settings.xml` en tu pipeline.

Para comprobar que la compilación usó Nexus, examina el log Maven: debe indicar `Using mirror nexus-maven for central` y las descargas desde la URL configurada. Si el Nexus necesita una CA corporativa, también debes instalarla en el truststore del JDK del runner.

### Empaquetar imagen después de las pruebas

Si el pipeline compila mediante Nexus, construye la imagen **después de** `bash ci/build.sh` usando [`Dockerfile.runtime`](Dockerfile.runtime); esta imagen incorpora el artefacto ya compilado sin volver a descargar dependencias:

```bash
podman build -f Dockerfile.runtime -t quarkus-game:latest .
```

El `Dockerfile` normal sigue siendo una opción para builds desde Git en OpenShift, pero su etapa Maven descarga dependencias dentro del build. Usa la opción de artefacto precompilado anterior cuando debas controlar la resolución mediante Nexus en el pipeline.

## API REST

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/game` | Información y controles |
| GET | `/api/game/scores` | Diez mejores puntuaciones |
| POST | `/api/game/scores` | Guarda `{"player":"Ada","points":5}` |
| GET | `/q/health/ready` | Disponibilidad |
| GET | `/q/health/live` | Vida |

```bash
curl http://localhost:8080/api/game
curl -X POST http://localhost:8080/api/game/scores \
  -H 'Content-Type: application/json' \
  -d '{"player":"Ada","points":5}'
curl http://localhost:8080/api/game/scores
```

El nombre admite 1 a 20 caracteres y los puntos van de 0 a 400; datos inválidos devuelven HTTP 400.

## Desplegar desde Git en OpenShift

Requisitos: `oc` autenticado y permisos para crear proyectos, builds, aplicaciones y rutas. El build Dockerfile desde Git requiere acceso a las imágenes base y a repositorios Maven. Para depender exclusivamente de Nexus usa el flujo de pipeline anterior y publica la imagen resultante en tu registro.

```bash
oc new-project quarkus-game
oc new-app https://github.com/psehgaft/openshift-quarkus-game.git \
  --strategy=docker --name=quarkus-game
oc logs -f bc/quarkus-game
oc rollout status deployment/quarkus-game
oc expose service/quarkus-game
oc get route quarkus-game
```

Abre el host indicado por la Route. Para comprobarla desde una terminal:

```bash
oc port-forward service/quarkus-game 8080:8080
# En otra terminal:
curl http://localhost:8080/q/health/ready
curl http://localhost:8080/api/game
```

## Archivos principales

- `pom.xml`: dependencias y plugins de Maven.
- `src/main/java/io/github/psehgaft/game/`: API y lógica del marcador.
- `src/main/resources/META-INF/resources/`: interfaz y juego.
- `src/test/java/io/github/psehgaft/game/`: pruebas unitarias y de integración.
- `ci/`: script de build y plantilla de configuración de Nexus.
- `Dockerfile.runtime`: imagen a partir del resultado compilado por el pipeline.

## Pipeline Jenkins

El [`Jenkinsfile`](Jenkinsfile) implementa las 12 etapas del flujo propuesto. Crea un **Pipeline from SCM** o **Multibranch Pipeline** que apunte a este repositorio y use `Jenkinsfile` como ruta del script. El checkout usa `checkout scm` para construir la revisión exacta del job; `GIT_REPO` queda como referencia de configuración. La rama o PR debe estar autorizada para usar las credenciales antes de habilitar publicación o despliegue.

**Primera ejecución:** deja todos los parámetros booleanos en `false` y `NEXUS_MAVEN_URL` vacío. El job hace checkout, comprobaciones estáticas, `bash ci/build.sh`, pruebas unitarias y REST, y publica los reportes JUnit. No requiere acceso a Quay ni OpenShift. Para resolver por Nexus, indica la URL del repositorio group en `NEXUS_MAVEN_URL` y configura la credencial `nexus-readonly`; el script usa `ci/settings-nexus.xml` para las dependencias y los plugins.

### Agente y configuración

El agente Jenkins Linux debe tener **JDK 21**, **Maven 3.9+**, `git` y `bash`. El job requiere los plugins **Pipeline**, **Git**, **Credentials Binding**, **JUnit**. Para SonarQube se necesitan **SonarQube Scanner for Jenkins** y un webhook de SonarQube a Jenkins para `waitForQualityGate`. Con `PUBLISH_IMAGE=true` también se necesitan **Podman** y **Syft**, espacio para guardar temporalmente una imagen OCI y acceso a Quay y a la imagen base. Con `DEPLOY_DEV=true` se necesitan `oc` y `curl`.

Configura en el entorno del job/agent estos valores **antes** de habilitar publicación/despliegue:

| Valor | Uso |
| --- | --- |
| `QUAY_REGISTRY` | Sustituye en el bloque `environment` del Jenkinsfile `quay.io/organization/app` por tu repositorio Quay real. |
| `RUNTIME_BASE_IMAGE` | Variable del job con la imagen de catálogo aprobada fijada con `@sha256:<digest>`; se pasa como argumento a `Dockerfile.runtime`. |
| `OPENSHIFT_PROJECT` | Sustituye `dev-environment` por el namespace DEV real si activas despliegue. |
| `OPENSHIFT_API` | Variable del job con la URL del API de OpenShift, por ejemplo `https://api.cluster.example:6443`. |

Crea estas credenciales de Jenkins con el **ID exacto** (o ajusta los IDs en el Jenkinsfile):

| ID | Tipo | Se usa cuando |
| --- | --- | --- |
| `nexus-readonly` | Username with password/token | `NEXUS_MAVEN_URL` tiene valor. |
| `quay-push` | Username with password/token de robot de Quay | `PUBLISH_IMAGE=true`. |
| `oc-dev-token` | Secret text con token de una service account con permisos de Deployment, Service y Route en DEV | `DEPLOY_DEV=true`. |
| `veracode-adapter` | Secret file con script Bash de integración | `ENABLE_VERACODE=true`. |
| `tpa-adapter` | Secret file con script Bash de integración | `ENABLE_TPA=true`. |
| `rhacs-adapter` | Secret file con script Bash de política | `ENABLE_RHACS=true`. |
| `tas-adapter` | Secret file con script Bash de firma y atestación | `ENABLE_SIGNING=true`. |

Los cuatro adaptadores de seguridad se entregan por Jenkins como archivos externos; **no están configurados ni incluidos en el repositorio** porque requieren los endpoints, credenciales, versiones de CLI y políticas de tu organización. Jenkins los ejecuta con `bash` y falla si faltan o retornan un código distinto de cero. Sus argumentos son, respectivamente: Veracode `target/quarkus-app`; TPA `sbom.cdx.json IMAGE_REF`; RHACS `IMAGE_REF`; TAS `IMAGE_REF sbom.cdx.json`. Cada adaptador debe validar su política y devolver un código de error si no se cumple. No actives sus parámetros hasta tenerlos. La opción `ENABLE_SONAR` usa directamente la configuración Jenkins llamada `SonarQubeServer`, analiza el bytecode tras el build y espera su quality gate en la etapa 9.

### Etapas y modos

| Etapas | Resultado |
| --- | --- |
| 1–4 | Valida parámetros, obtiene el código, hace comprobaciones estáticas y ejecuta Maven `clean verify`; guarda reportes JUnit. |
| 5 | Ejecuta Veracode si está habilitado. |
| 6–7 | Construye con `Dockerfile.runtime`, publica en Quay y lee el digest producido por `podman push --digestfile`. |
| 8 | Genera una SBOM CycloneDX desde un archivo OCI de la imagen candidata y, opcionalmente, la entrega a TPA. |
| 9–10 | Aplica los gates habilitados de SonarQube/RHACS y firma/atestación. |
| 11–12 | Crea o actualiza Deployment, Service y Route en DEV usando **el digest publicado**; espera rollout y comprueba `/q/health/ready`, `/api/game` y la página. |

Para publicar usa `PUBLISH_IMAGE=true`; para desplegar usa además `DEPLOY_DEV=true`. El repo de Quay debe existir y la cuenta de Quay debe poder publicar; si es privado, configura previamente un pull secret en el namespace de OpenShift y asígnalo a la service account usada por el Deployment. El token de OpenShift necesita acceso al namespace existente. La Route debe ser accesible desde el agente Jenkins y su certificado TLS debe ser de confianza para `curl`. Configura el despliegue DEV existente con la política de recursos y probes que use tu plataforma; este ejemplo verifica la salud HTTP después del rollout.

Las etapas desactivadas se muestran como **omitidas**; un build verde con las opciones en `false` solo acredita compilación y pruebas, no un escaneo, firma o despliegue. Si un gate activado falla, Jenkins detiene el flujo. Los reportes de pruebas, SBOM y digest quedan archivados cuando se generan. El pipeline no elimina imágenes publicadas ni modifica otras aplicaciones en el namespace.

**Nota sobre Nexus y Docker:** Jenkins compila con `ci/build.sh` antes de la imagen; `Dockerfile.runtime` copia `target/quarkus-app/` y no ejecuta Maven. La imagen base aprobada se proporciona en `RUNTIME_BASE_IMAGE`. El `Dockerfile` original continúa sirviendo al build directo desde Git en OpenShift, con su propia resolución de dependencias.

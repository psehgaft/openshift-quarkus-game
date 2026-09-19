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

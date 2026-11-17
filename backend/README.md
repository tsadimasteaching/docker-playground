# Backend: Tickets API (Spring Boot)

A REST API for a small ticketing system. It is built with Spring Boot 4, Java 25 and Maven, and stores data in PostgreSQL.

```
src/main/java/gr/hua/dit/tickets
├── TicketsApplication.java
├── model/        Ticket (@Entity), TicketStatus, TicketPriority
├── repository/   TicketRepository (Spring Data JPA)
├── service/      TicketService (business rules per role)
├── web/          TicketController, MeController, request DTOs
└── config/       SecurityConfig (Spring Security), OpenApiConfig (Swagger), DataSeeder
```

## Roles and users

Users are kept in memory ([SecurityConfig.java](src/main/java/gr/hua/dit/tickets/config/SecurityConfig.java)) and log in with **HTTP Basic**. The password for every user is `password`.

| User            | Role  | Permissions                                                                  |
|-----------------|-------|------------------------------------------------------------------------------|
| `alice`, `bob`  | USER  | create tickets; list / read / update / delete **own** tickets while `OPEN`    |
| `agent`         | AGENT | list / read **all** tickets; assign to self (`IN_PROGRESS`); resolve (`RESOLVED`) |

On first start, when the database is empty, [DataSeeder](src/main/java/gr/hua/dit/tickets/config/DataSeeder.java) inserts 3 demo tickets.

## API

| Method | Path                         | Role        | Description                                   |
|--------|------------------------------|-------------|-----------------------------------------------|
| GET    | `/api/me`                    | any         | current user and roles                        |
| GET    | `/api/tickets?status=OPEN`   | USER, AGENT | USER: own tickets, AGENT: all (`status` optional) |
| GET    | `/api/tickets/{id}`          | USER, AGENT | one ticket (USER: only own, else 404)         |
| POST   | `/api/tickets`               | USER        | create `{title, description, priority}`       |
| PUT    | `/api/tickets/{id}`          | USER        | update own `OPEN` ticket                      |
| DELETE | `/api/tickets/{id}`          | USER        | delete own `OPEN` ticket                      |
| POST   | `/api/tickets/{id}/assign`   | AGENT       | assign to me → `IN_PROGRESS`                  |
| POST   | `/api/tickets/{id}/resolve`  | AGENT       | `{resolution}` → `RESOLVED`                   |
| GET    | `/actuator/health`           | public      | health check (for Docker `HEALTHCHECK`)     |

**Swagger UI:** <http://localhost:8080/swagger-ui.html>. Click **Authorize** and enter a user. The OpenAPI JSON is at `/v3/api-docs`.

Example with curl:

```bash
curl -u alice:password http://localhost:8080/api/tickets

curl -u alice:password -H 'Content-Type: application/json' \
  -d '{"title":"Laptop is slow","priority":"HIGH"}' http://localhost:8080/api/tickets

curl -u agent:password -X POST http://localhost:8080/api/tickets/4/assign

curl -u agent:password -H 'Content-Type: application/json' \
  -d '{"resolution":"Removed startup apps"}' http://localhost:8080/api/tickets/4/resolve
```

## Configuration

Configuration comes from **environment variables, read when the app starts**. The image never contains the DB address or password, so the same image works with any database.

### 1. Placeholders with defaults

[application.properties](src/main/resources/application.properties) uses `${NAME:default}` placeholders:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tickets}
spring.datasource.username=${DB_USER:tickets}
spring.datasource.password=${DB_PASSWORD:tickets}
```

`${DB_HOST:localhost}` means "the environment variable `DB_HOST`, or `localhost` if it is not set". With the defaults, `mvn spring-boot:run` works against a local Postgres container without any setup.

| Variable      | Default     | Set to, in a container                                  |
|---------------|-------------|---------------------------------------------------------|
| `DB_HOST`     | `localhost` | name of the Postgres container/service (`tickets-db`, `db`) |
| `DB_PORT`     | `5432`      |                                                         |
| `DB_NAME`     | `tickets`   | same as `POSTGRES_DB` of the db container               |
| `DB_USER`     | `tickets`   | same as `POSTGRES_USER`                                 |
| `DB_PASSWORD` | `tickets`   | same as `POSTGRES_PASSWORD`                             |

### 2. Overriding any Spring property (relaxed binding)

Without any placeholder, Spring Boot maps environment variables to properties: uppercase, `.` → `_`, `-` removed.

| Environment variable           | Property                         |
|--------------------------------|----------------------------------|
| `SERVER_PORT=9090`             | `server.port`                    |
| `SPRING_JPA_SHOW_SQL=true`     | `spring.jpa.show-sql`            |
| `SPRING_DATASOURCE_URL=jdbc:...` | `spring.datasource.url` (replaces the whole URL) |
| `LOGGING_LEVEL_ORG_HIBERNATE_SQL=debug` | `logging.level.org.hibernate.SQL` |

Precedence, highest first: command-line args (`--server.port=9090`) → environment variables → `application.properties`.

### 3. Passing the variables

```bash
# locally (Linux/macOS shell)
DB_PASSWORD=secret SPRING_JPA_SHOW_SQL=true mvn spring-boot:run

# in a container: docker run -e NAME=value ...   or   docker run --env-file <file> ...
```

### 4. JVM options

The JVM automatically reads the `JAVA_TOOL_OPTIONS` environment variable. In containers it is a convenient way to pass JVM flags without changing the start command, e.g. `-XX:MaxRAMPercentage=75.0`, which sizes the heap from the container's memory limit.

> Spring does **not** read `.env` files. For `mvn spring-boot:run`, use the defaults or export the variables in your shell.

## Run locally

Requirements: JDK 25 and Maven 3.9+ (both are already in the dev container), plus a running PostgreSQL:

```bash
docker run -d --name tickets-db \
  -e POSTGRES_DB=tickets -e POSTGRES_USER=tickets -e POSTGRES_PASSWORD=tickets \
  -p 5432:5432 postgres:18-alpine

mvn spring-boot:run
```

## Tests

The tests use an in-memory **H2** database ([src/test/resources/application.properties](src/test/resources/application.properties)), so PostgreSQL is **not** needed:

```bash
mvn test
```

| Test class                                                                              | Type                                  |
|-----------------------------------------------------------------------------------------|---------------------------------------|
| [TicketServiceTest](src/test/java/gr/hua/dit/tickets/service/TicketServiceTest.java)     | unit test, Mockito, no Spring context |
| [TicketRepositoryTest](src/test/java/gr/hua/dit/tickets/repository/TicketRepositoryTest.java) | `@DataJpaTest` slice (JPA only)  |
| [TicketControllerTest](src/test/java/gr/hua/dit/tickets/web/TicketControllerTest.java)   | `@SpringBootTest` + MockMvc, incl. security |
| [TicketsApplicationTests](src/test/java/gr/hua/dit/tickets/TicketsApplicationTests.java) | context loads                         |

## Containerizing the backend

There is no Dockerfile yet. You write it in [steps 2–4 of the main README](../README.md#4-containerizing-the-app-step-by-step). The reference solution is on the `docker` branch.

### Facts you need for the Dockerfile

| What | Value |
|---|---|
| Java version | **25** (build with a JDK 25, run with a JRE 25) |
| Build tool | Maven 3.9 |
| Build command | `mvn -B package` (add `-DskipTests` to skip tests; they don't need a DB either way) |
| Download dependencies only | `mvn -B dependency:go-offline` (needs only `pom.xml`) |
| Build output | **`target/app.jar`** (fixed name via `<finalName>` in [pom.xml](pom.xml)), a self-contained "fat" jar |
| Start command | `java -jar app.jar` |
| Port | **8080** |
| Health endpoint | `GET /actuator/health` (public, returns `{"status":"UP"}`) |
| Required at runtime | a reachable PostgreSQL, configured with the `DB_*` variables ([Configuration](#configuration)) |
| Writes files? | no; the app needs no writable directory (good for a non-root user) |
| Shutdown | graceful on `SIGTERM` (`server.shutdown=graceful`) |

### Step by step

1. **Run the tests locally** (`mvn test`) so you know the code is fine before you blame Docker.
2. **Naive Dockerfile** (`Dockerfile`): one stage with Maven + JDK, copy everything, package, run the jar. Build and run it, then note the image size and user (`id`).
3. **`.dockerignore`**: exclude at least `target/`, `.idea/`, `.vscode/`, `*.iml`, `.git/`.
4. **Multi-stage Dockerfile** (`Dockerfile.multistage`):
   1. build stage: copy `pom.xml` → download dependencies → copy `src/` → package
   2. runtime stage: JRE-only base image → create a non-root user → copy **only** `app.jar` from the build stage → `USER` → `EXPOSE 8080` → `HEALTHCHECK` → start command
5. **Run it with a database** on a user-defined network, passing `DB_HOST` etc. with `-e`.

### What to take care of

- **`localhost` is not your database** inside a container. `DB_HOST` must be the DB container's name (or the Compose service name).
- The **credentials must match** the ones the Postgres container was created with.
- **Order for caching:** `pom.xml` and the dependency download first, `src/` afterwards. Otherwise every code change re-downloads all of Maven Central.
- **JRE, not JDK**, in the runtime image. No Maven, no sources, no `~/.m2` in the final image.
- **Non-root:** create a system user/group (on Alpine: `addgroup`/`adduser`; on Debian/Ubuntu: `groupadd`/`useradd`) and switch with `USER`. Check with `docker exec <container> id`.
- **Exec form** for the start command, so `docker stop` triggers the graceful shutdown instead of a kill after 10 s.
- **Healthcheck tool:** JRE images have no `curl`. Alpine has BusyBox `wget`. Give the app time to start (`--start-period`).
- **Memory:** by default the JVM sizes its heap from the machine's memory, not the container's limit. Use `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"`.
- **No secrets in the image:** the DB password is passed at `docker run` / Compose time, never with `ENV` in the Dockerfile.

### How to check

```bash
docker build -t tickets-backend:naive .
docker build -f Dockerfile.multistage -t tickets-backend:multistage .
docker images | grep tickets-backend          # compare sizes
docker run --rm tickets-backend:multistage id # must not be root
docker ps                                     # (healthy) once the DB is reachable
curl http://localhost:8080/actuator/health
curl -u alice:password http://localhost:8080/api/tickets
```

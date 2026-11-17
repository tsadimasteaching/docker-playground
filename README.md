# Docker Playground

A hands-on repo for learning Docker with a realistic three-tier app: a **Vue.js** frontend, a **Spring Boot** (Maven) REST API and a **PostgreSQL** database.

> **This `main` branch is the starting point.** The application works, but there are **no Dockerfiles and no Compose file yet**. You will write them step by step in [section 4](#4-containerizing-the-app-step-by-step).
> A complete reference solution is on the **[`docker`](../../tree/docker) branch** (`git checkout docker`).

The app is a small **ticketing system**:

| Role      | Users (password `password`) | Can do                                                        |
|-----------|-----------------------------|---------------------------------------------------------------|
| **USER**  | `alice`, `bob`              | create tickets, see / edit / delete **their own** OPEN tickets |
| **AGENT** | `agent`                     | see **all** tickets, assign a ticket to themselves, resolve it |

What we are going to containerize:

```
                 ┌──────────────────────┐      ┌──────────────────────┐      ┌──────────────┐
 browser ──────► │ frontend             │ /api │ backend              │ JDBC │ db           │
                 │ Vue (Vite)           │ ───► │ Spring Boot :8080    │ ───► │ PostgreSQL   │
                 └──────────────────────┘      └──────────────────────┘      └──────────────┘
```

## Repository structure

| Path                              | What it is                                                              |
|-----------------------------------|-------------------------------------------------------------------------|
| [`.devcontainer/`](.devcontainer) | Dev container / GitHub Codespaces with Docker, JDK 25, Maven and Node 24 |
| [`backend/`](backend)             | Spring Boot API: entities, repositories, services, Spring Security, Swagger, tests. See [backend/README.md](backend/README.md) |
| [`frontend/`](frontend)           | Vue 3 + Vite single-page app with Vitest tests. See [frontend/README.md](frontend/README.md) |

---

## 1. Environment setup

Choose **one** of the options.

### Option A — Dev container / GitHub Codespaces (recommended)

Everything (Docker, JDK, Maven, Node) is pre-installed.

- **Codespaces:** on GitHub click **Code → Codespaces → Create codespace on main**.
- **VS Code locally:** install Docker (see option B) and the *Dev Containers* extension, open the folder and choose **Reopen in Container**.

The dev container uses *docker-in-docker*: there is a full Docker engine **inside** the container, so `docker build`, `docker run` and `docker compose` work as on a normal machine. Ports 8080, 8081 and 5173 are forwarded automatically.

Check it:

```bash
docker version
docker compose version
java -version && mvn -v && node -v
```

### Option B — Install Docker on your machine

#### Linux — Ubuntu / Debian

Install Docker Engine from Docker's official apt repository (the `docker.io` package in the distro is usually older):

```bash
# remove old/unofficial packages (it's fine if none are installed)
for pkg in docker.io docker-doc docker-compose podman-docker containerd runc; do sudo apt-get remove -y $pkg; done

# add Docker's GPG key and repository
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# install engine, CLI, buildx and the compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

> On **Debian** replace `linux/ubuntu` with `linux/debian` in both URLs.

#### Linux — Fedora

```bash
sudo dnf -y install dnf-plugins-core
sudo dnf config-manager addrepo --from-repofile https://download.docker.com/linux/fedora/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

#### Linux — Arch / Manjaro

```bash
sudo pacman -S docker docker-buildx docker-compose
sudo systemctl enable --now docker
```

#### Linux — post-installation (all distros)

Run Docker without `sudo` by adding yourself to the `docker` group, then log out and back in (or run `newgrp docker`):

```bash
sudo usermod -aG docker $USER
newgrp docker
```

> Being in the `docker` group is effectively root access on the machine. That is fine on your own laptop, but not on a shared server.

#### macOS

Install **Docker Desktop** from <https://docs.docker.com/desktop/setup/install/mac-install/> (pick Apple silicon or Intel), or with Homebrew:

```bash
brew install --cask docker
```

Start *Docker* from Applications once so it can finish its setup.

#### Windows

1. Enable WSL 2: open PowerShell **as Administrator** and run `wsl --install`, then reboot.
2. Install **Docker Desktop** from <https://docs.docker.com/desktop/setup/install/windows-install/> and keep **Use WSL 2 based engine** checked.
3. Run the commands in this README from a WSL terminal (e.g. Ubuntu) for the best experience.

#### Verify the installation

```bash
docker version            # client and server (engine) versions
docker compose version    # compose v2 plugin
docker run --rm hello-world
```

---

## 2. Docker in 2 minutes

| Concept       | Meaning                                                                          |
|---------------|----------------------------------------------------------------------------------|
| **Image**     | A read-only template (filesystem + metadata), built from a `Dockerfile`          |
| **Container** | A running instance of an image, with its own process, filesystem and network     |
| **Registry**  | A place where images are stored (Docker Hub, `ghcr.io`, ...)                     |
| **Volume**    | Persistent storage that outlives a container (we use one for PostgreSQL data)    |
| **Network**   | Containers on the same user-defined network reach each other **by name**         |

Commands you will use all the time:

```bash
docker images                         # list images
docker ps / docker ps -a              # running / all containers
docker logs -f <container>            # follow logs
docker exec -it <container> sh        # shell inside a running container
docker stop <container> && docker rm <container>
docker system df                      # disk usage
docker system prune                   # clean up stopped containers, dangling images, ...
```

---

## 3. Run the application (without Dockerfiles)

Before containerizing anything, make sure the app runs. The backend needs PostgreSQL, and we already use Docker for that. It is your first container:

```bash
docker run -d --name tickets-db \
  -e POSTGRES_DB=tickets -e POSTGRES_USER=tickets -e POSTGRES_PASSWORD=tickets \
  -p 5432:5432 \
  postgres:18-alpine

docker ps                  # is it running?
docker logs -f tickets-db  # wait for "database system is ready to accept connections", Ctrl+C
```

Then, in two terminals:

```bash
cd backend  && mvn spring-boot:run                  # http://localhost:8080/swagger-ui.html
cd frontend && npm install && npm run dev           # http://localhost:5173
```

Log in as `alice`, `bob` or `agent` (password `password`).

Tests do not need the database (they use H2 and mocks):

```bash
cd backend  && mvn test
cd frontend && npm test
```

Stop and remove the database container when you are done:

```bash
docker rm -f tickets-db
```

---

## 4. Containerizing the app: step by step

Work through the steps **in order**. Each step builds on the previous one. Every step has:

- **Tasks:** what to do
- **Take care:** things that commonly go wrong or that make the difference between a working image and a *good* one
- **Check:** how to verify it works, and questions to think about

There is no ready-made code here: you write the Dockerfiles and the Compose file yourself. A reference solution is on the [`docker`](../../tree/docker) branch. Try first, then compare.

Component-specific facts you need (ports, build commands, output paths, environment variables) are in [backend/README.md](backend/README.md#containerizing-the-backend) and [frontend/README.md](frontend/README.md#containerizing-the-frontend).

### Before you start: Dockerfile instructions you will need

| Instruction    | Purpose                                                                                      |
|----------------|----------------------------------------------------------------------------------------------|
| `FROM`         | base image (`FROM image AS name` starts a named **stage**)                                   |
| `WORKDIR`      | working directory for the following instructions (created if missing)                       |
| `COPY`         | copy files from the **build context** (or `--from=<stage>` from another stage) into the image |
| `RUN`          | run a command **at build time** and save the result as a new layer                          |
| `ENV`          | environment variable available at build time **and** in the running container               |
| `ARG`          | variable available **only at build time**                                                    |
| `EXPOSE`       | documents the port the app listens on (it does **not** publish it; `-p` does)               |
| `USER`         | user for the following `RUN`, `CMD` and `ENTRYPOINT`                                         |
| `HEALTHCHECK`  | command Docker runs periodically to decide if the container is *healthy*                    |
| `CMD` / `ENTRYPOINT` | what runs when the container **starts**                                                |

Three ideas to keep in mind the whole time:

1. **Build context:** `docker build <dir>` sends `<dir>` to the builder. `COPY` can only see files inside it, and `.dockerignore` controls what is sent.
2. **Layer cache:** each instruction produces a layer. If an instruction and its inputs did not change, Docker reuses the cached layer. As soon as one layer changes, **all following layers are rebuilt**. Order instructions from *rarely changes* to *often changes*.
3. **Build once, configure at start-up:** never bake addresses or passwords into an image. Pass them as environment variables when the container starts.

---

### Step 1: PostgreSQL in a container, by hand

**Tasks**

1. Pull and run the official `postgres` image (pick a fixed major version, e.g. `18-alpine`).
2. Set the database name, user and password with the environment variables documented on the image's Docker Hub page.
3. Publish port 5432 so the backend running on your machine (`mvn spring-boot:run`) can reach it.
4. Connect with `psql` **inside** the container (`docker exec`) and list the databases.

**Take care**

- Use a **fixed tag** (`postgres:18-alpine`), not `latest`. `latest` changes over time and can break your setup.
- The `POSTGRES_*` variables are only applied the **first time**, when the data directory is empty.
- Without a volume the data lives inside the container and is lost with `docker rm`.

**Check**

- `docker ps` shows the container as *Up*, and `docker logs` shows *ready to accept connections*.
- The backend starts with `mvn spring-boot:run` and you can log in to the frontend.
- Remove the container and start a new one: are your tickets gone? Why?

---

### Step 2: a first, naive Dockerfile for the backend

Goal: get *something* working. Writing it simply first makes the improvements in step 3 easy to see.

**Tasks**

1. Create `backend/Dockerfile` with **one stage** based on an image that has Maven and JDK 25 (e.g. `maven:3.9-eclipse-temurin-25`).
2. Copy the project into the image, build the jar with Maven, and start it with `java -jar`.
3. Build it with the tag `tickets-backend:naive` and run it.

**Take care**

- Use `-DskipTests` (or run the tests) **consciously**: the tests do not need a database, so both work.
- Use the **exec form** for `CMD` (`["java", "-jar", "..."]`), not the shell form. Then Java is PID 1 and receives the `SIGTERM` from `docker stop` (graceful shutdown instead of being killed after 10 s).
- Publish the port with `-p 8080:8080`. `EXPOSE` alone is not enough.
- If you run it now, the backend **cannot reach the database** on `localhost`: inside a container, `localhost` is the container itself. That is fixed in step 4, so for now just observe the error in `docker logs`.

**Check**

- `docker images`: how big is the image?
- `docker run --rm tickets-backend:naive id`: which user does the app run as?
- `docker history tickets-backend:naive`: which layers are big?
- Change one line in a `.java` file and rebuild. Are all Maven dependencies downloaded again? Why?

---

### Step 3: a better backend image (multi-stage, non-root)

**Tasks**

1. Create `backend/Dockerfile.multistage` with two stages:
   - **build stage**: Maven + JDK, produces the jar
   - **runtime stage**: only a **JRE** (e.g. `eclipse-temurin:25-jre-alpine`) plus the jar copied from the build stage
2. Make the dependency download **cacheable**: copy `pom.xml` first, download the dependencies (`mvn dependency:go-offline`), and only then copy `src/` and package.
3. Create a **non-root** user and group in the runtime stage and switch to it with `USER`.
4. Add a `HEALTHCHECK` that calls `/actuator/health`.
5. Add `backend/.dockerignore`.
6. Build it with `docker build -f Dockerfile.multistage -t tickets-backend:multistage .`

**Take care**

- **Only the last stage ends up in the image.** Everything you need at runtime must be copied into it with `COPY --from=<build stage>`.
- Put `USER` **after** the steps that need root (creating users, installing packages).
- The jar can stay owned by root: the app user only needs to *read* it. Do not give the app write access to its own code.
- Slim images often have **no `curl`**. Alpine has `wget` (BusyBox). Use what is actually in the image for the `HEALTHCHECK`.
- `.dockerignore` should exclude `target/`, IDE folders and `.git`. Smaller context = faster builds, and no local build output leaks into the image.
- The JVM should respect the container's memory limit: look at `-XX:MaxRAMPercentage` (through `JAVA_TOOL_OPTIONS`).
- Never put passwords in the Dockerfile (`ENV`, `ARG`). They stay in the image and are visible with `docker history`.

**Check**

- Compare the size with the naive image (`docker images`).
- `docker run --rm tickets-backend:multistage id` must **not** show `uid=0(root)`.
- Change a `.java` file and rebuild: the dependency layer must say `CACHED`.
- `docker ps` shows `(healthy)` after start-up. Note that this needs the database from step 4.

---

### Step 4: connect the backend and the database through a network

**Tasks**

1. Create a **user-defined network** (`docker network create ...`).
2. Create a **named volume** for the database data.
3. Run PostgreSQL on that network with the volume mounted on its data directory (see the image docs for the path).
4. Run the backend on the same network and set `DB_HOST` (and the other `DB_*` variables) with `-e`.
5. Publish only what you need from your machine: the backend (8080). The database does **not** need `-p` any more.

**Take care**

- On a user-defined network, containers reach each other **by container name** (built-in DNS). The default `bridge` network does not offer this.
- `DB_HOST` must be the **name of the database container**, not `localhost`.
- The DB user, password and database name of the backend must **match** the `POSTGRES_*` values of the database.
- Give your containers a `--name`, otherwise Docker invents one and you cannot address them.

**Check**

- `docker network inspect <network>` lists both containers.
- `curl -u alice:password http://localhost:8080/api/tickets` returns tickets.
- Swagger UI works at <http://localhost:8080/swagger-ui.html>.
- Remove **and recreate** the database container with the same volume: are the tickets still there?
- `docker exec <backend> id`: still not root?

---

### Step 5: the frontend, naive version

**Tasks**

1. Create `frontend/Dockerfile` based on a Node image (Node 24).
2. Copy the project, install the dependencies, and start the **Vite dev server**.
3. Run it on the same network as the backend and tell the dev server where the backend is (`BACKEND_URL`).

**Take care**

- The dev server must listen on `0.0.0.0`, not only `localhost`, to be reachable from outside the container. This repo's `vite.config.js` already does that.
- Exclude your local `node_modules/` with `.dockerignore`. It may contain binaries built for **your** OS, which can break inside a Linux container.
- `BACKEND_URL` must use the backend **container name** (as in step 4).

**Check**

- The UI opens on the published port and you can log in.
- How big is this image compared to the size of the built app (`npm run build` → `dist/`)?
- This is a **development** server. Why should it not be used in production?

---

### Step 6: the frontend, production version (multi-stage, non-root web server)

**Tasks**

1. Create `frontend/Dockerfile.multistage` with:
   - **build stage**: Node, `npm ci`, `npm run build` → static files in `dist/`
   - **runtime stage**: a web server that serves `dist/` **as non-root**, e.g. `nginxinc/nginx-unprivileged:alpine`
2. Configure the web server to:
   - serve the static files
   - **proxy `/api/`** to the backend
   - send `index.html` for unknown paths (single-page app fallback)
3. Make the backend address configurable with `BACKEND_URL` at **container start** (the nginx image can fill environment variables into config *templates*: read the "Using environment variables in nginx configuration" part of the image documentation).
4. Add a `HEALTHCHECK` and a `.dockerignore`.

**Take care**

- Copy `package.json` **and** `package-lock.json` first and use `npm ci` (exact versions from the lock file, cacheable layer). Copy the sources afterwards.
- A non-root process **cannot bind ports below 1024**. The unprivileged nginx listens on **8080**, so map it with `-p 8081:8080`.
- Do **not** bake the backend URL into the JS bundle (`VITE_*` variables are fixed at build time). The browser code only uses relative `/api/...` URLs.
- In `HEALTHCHECK`s, `localhost` can resolve to IPv6 (`::1`). If your server only listens on IPv4, use `127.0.0.1` or make the server listen on both.

**Check**

- Compare the image size with step 5.
- `docker exec <frontend> id`: not root?
- Open the UI, log in as `agent` and resolve a ticket: requests go *browser → nginx → backend*.
- Change `BACKEND_URL` and restart **without** rebuilding: does it still work?

---

### Step 7: Docker Compose, the whole stack with one command

**Tasks**

1. Create `compose.yaml` in the repository root with three services: `db`, `backend`, `frontend`.
2. `backend` and `frontend` are **built** from your `Dockerfile.multistage` files (`build.context`, `build.dockerfile`).
3. Add a **named volume** for the database.
4. Set the environment variables of each service. Remember that service names are host names on the Compose network.
5. Control the start-up order with `depends_on` and `condition: service_healthy`. Give the `db` service a healthcheck (`pg_isready`).
6. Publish only the ports you need from your browser.
7. Move the credentials into a **`.env`** file:
   - `.env` holds the real values and is in `.gitignore` (already there)
   - commit a `.env.example` with the variable names and dummy values
   - reference them in `compose.yaml` as `${POSTGRES_PASSWORD}`, with `${VAR:?message}` for required ones

**Take care**

- Use **`docker compose`** (v2, CLI plugin) and name the file **`compose.yaml`**. The `version:` key at the top is obsolete, so leave it out.
- Compose creates a network for you. Do not use `localhost` between services. Use the **service name** (`db`, `backend`).
- `depends_on` alone only waits for the container to **start**, not for the app to be **ready**. That is why you need healthchecks + `condition: service_healthy`.
- Define a password **once** in `.env` and use it for both `db` and `backend`.
- `$` in commands that should be evaluated *inside* the container must be written as `$$`.
- `docker compose down` keeps volumes. `docker compose down -v` deletes them (and your data).

**Check**

- `docker compose config` shows the final configuration with all variables filled in.
- `docker compose up --build` starts everything. `docker compose ps` shows all services *healthy*.
- Delete `.env` (or run with `--env-file /dev/null`): does Compose refuse to start with a clear message?
- `docker compose down` then `up` again: are the tickets still there? And after `down -v`?

---

### Checklist: a good Dockerfile

- [ ] fixed, specific base image tags (no `latest`)
- [ ] multi-stage: build tools are **not** in the final image
- [ ] small runtime base (JRE instead of JDK, `alpine`/slim variants)
- [ ] dependency files copied and installed **before** the source code (layer cache)
- [ ] `.dockerignore` excludes build output, `node_modules`, `.git`, IDE files, secrets
- [ ] runs as a **non-root** user (`USER`)
- [ ] no secrets in `ENV`, `ARG` or copied files
- [ ] configuration through environment variables at start-up
- [ ] `EXPOSE` documents the port; the app listens on `0.0.0.0`
- [ ] exec form `CMD`/`ENTRYPOINT` so the app receives signals
- [ ] `HEALTHCHECK` using a tool that exists in the image

### Checklist: a good compose.yaml

- [ ] one service per component; images built from the right Dockerfile
- [ ] services talk to each other by **service name**
- [ ] named volume for persistent data
- [ ] healthchecks + `depends_on: condition: service_healthy`
- [ ] only the necessary ports published
- [ ] secrets in `.env` (git-ignored) + committed `.env.example`; required variables use `${VAR:?...}`

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| backend: `Connection refused` to `localhost:5432` | inside a container `localhost` is the container itself; use the DB container/service name |
| backend: `password authentication failed` | DB credentials differ between backend and db, or the volume was created with an older password |
| `port is already allocated` | another container/process uses the host port; change the left side of `-p` or stop the other one |
| `permission denied` when the app writes a file | the non-root user may not write there; use a directory it owns (or a volume) |
| container shows `unhealthy` | run the healthcheck command yourself with `docker exec`; check the tool exists and the address (`127.0.0.1` vs `localhost`) |
| your code changes are not visible | the image was not rebuilt: `docker build ...` again, or `docker compose up --build` |
| `COPY failed: file not found` | the file is outside the build context or excluded by `.dockerignore` |

Debugging commands:

```bash
docker logs -f <container>              # application output
docker exec -it <container> sh          # look around inside
docker inspect <container>              # env, network, mounts, health log
docker compose logs -f <service>
docker build --no-cache ...             # rebuild ignoring the cache
docker system df / docker system prune  # disk usage / clean up
```

---

## Links

- [Dockerfile reference](https://docs.docker.com/reference/dockerfile/)
- [Docker Engine install](https://docs.docker.com/engine/install/)
- [Dockerfile best practices](https://docs.docker.com/build/building/best-practices/)
- [Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Compose file reference](https://docs.docker.com/reference/compose-file/)
- [Compose: variable interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/) and [precedence](https://docs.docker.com/compose/how-tos/environment-variables/envvars-precedence/)
- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Vite env variables](https://vite.dev/guide/env-and-mode)
- [Migrate from Compose v1 to v2](https://docs.docker.com/compose/releases/migrate/)
- [Official postgres image](https://hub.docker.com/_/postgres)
- [nginx-unprivileged image](https://hub.docker.com/r/nginxinc/nginx-unprivileged)
- [Spring Boot container images](https://docs.spring.io/spring-boot/reference/packaging/container-images/index.html)
- [Dev Containers](https://containers.dev/)

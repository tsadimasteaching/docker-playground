# Docker Playground

A hands-on repo for learning Docker with a realistic three-tier app: a **Vue.js** frontend, a **Spring Boot** (Maven) REST API and a **PostgreSQL** database, each running in its own container.

The app is a small **ticketing system**:

| Role      | Users (password `password`) | Can do                                                        |
|-----------|-----------------------------|---------------------------------------------------------------|
| **USER**  | `alice`, `bob`              | create tickets, see / edit / delete **their own** OPEN tickets |
| **AGENT** | `agent`                     | see **all** tickets, assign a ticket to themselves, resolve it |

```
                 ┌──────────────────────┐      ┌──────────────────────┐      ┌──────────────┐
 browser ──────► │ frontend             │ /api │ backend              │ JDBC │ db           │
  :8081          │ nginx + Vue (static) │ ───► │ Spring Boot :8080    │ ───► │ PostgreSQL   │
                 └──────────────────────┘      └──────────────────────┘      └──────────────┘
```

## Repository structure

| Path                              | What it is                                                              |
|-----------------------------------|-------------------------------------------------------------------------|
| [`.devcontainer/`](.devcontainer) | Dev container / GitHub Codespaces with Docker, JDK 25, Maven and Node 24 |
| [`backend/`](backend)             | Spring Boot API: entities, repositories, services, Spring Security, Swagger, tests. See [backend/README.md](backend/README.md) |
| [`frontend/`](frontend)           | Vue 3 + Vite single-page app with Vitest tests. See [frontend/README.md](frontend/README.md) |
| [`compose.yaml`](compose.yaml)    | Starts the whole stack (db + backend + frontend) with one command        |
| [`.env.example`](.env.example)    | Template for `.env` (DB credentials, ports) used by Compose              |

Each component has **two Dockerfiles** so you can compare them:

| File                    | Idea                                                                                   |
|-------------------------|----------------------------------------------------------------------------------------|
| `Dockerfile`            | The *naive* version: one stage, runs as **root**, ships build tools and source code    |
| `Dockerfile.multistage` | The *better* version: **multi-stage** build, **non-root** user, layer caching, healthcheck |

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

## 3. Walkthrough: one container at a time

We first run everything by hand with `docker run`, so you can see what Compose automates later.

### 3.1 Create a network

Containers on a **user-defined** network can reach each other by container name (built-in DNS):

```bash
docker network create tickets-net
```

### 3.2 PostgreSQL

```bash
docker volume create tickets-db-data

docker run -d --name tickets-db --network tickets-net \
  -e POSTGRES_DB=tickets -e POSTGRES_USER=tickets -e POSTGRES_PASSWORD=tickets \
  -v tickets-db-data:/var/lib/postgresql \
  -p 5432:5432 \
  postgres:18-alpine

docker logs -f tickets-db       # wait for "database system is ready to accept connections", then Ctrl+C
docker exec -it tickets-db psql -U tickets -c '\l'
```

`-p 5432:5432` is only needed if you want to run the backend **outside** Docker (`mvn spring-boot:run`) or connect with a DB client.

### 3.3 Backend — naive Dockerfile vs. multi-stage Dockerfile

```bash
cd backend

# Step 1: naive image
docker build -t tickets-backend:naive .

# Step 2: multi-stage, non-root image
docker build -f Dockerfile.multistage -t tickets-backend:multistage .

docker images | grep tickets-backend
```

Run the better one on the same network. `DB_HOST=tickets-db` is the **container name** of PostgreSQL:

```bash
docker run -d --name tickets-backend --network tickets-net \
  -e DB_HOST=tickets-db \
  -p 8080:8080 \
  tickets-backend:multistage

curl http://localhost:8080/actuator/health
curl -u alice:password http://localhost:8080/api/tickets
```

Swagger UI: <http://localhost:8080/swagger-ui.html> (click **Authorize** and log in as `alice` / `password` or `agent` / `password`).

Who is the process running as?

```bash
docker exec tickets-backend id      # uid=100(spring) ... -> not root
docker run --rm tickets-backend:naive id   # uid=0(root)
```

Details: [backend/README.md](backend/README.md).

### 3.4 Frontend

```bash
cd ../frontend
docker build -t tickets-frontend:naive .
docker build -f Dockerfile.multistage -t tickets-frontend:multistage .

docker run -d --name tickets-frontend --network tickets-net \
  -e BACKEND_URL=http://tickets-backend:8080 \
  -p 8081:8080 \
  tickets-frontend:multistage
```

Open <http://localhost:8081> and log in as `alice`, `bob` or `agent`.

Details: [frontend/README.md](frontend/README.md).

### 3.5 Compare the images

Sizes measured on linux/amd64 (yours may differ slightly):

| Image                          | Size        | Runs as             | Contains                              |
|--------------------------------|-------------|---------------------|---------------------------------------|
| `tickets-backend:naive`        | **~1 GB**   | root                | Maven, full JDK, sources, `~/.m2` cache, jar |
| `tickets-backend:multistage`   | **~420 MB** | `spring` (uid 100)  | Alpine + JRE + jar                    |
| `tickets-frontend:naive`       | **~1.8 GB** | root                | Node, `node_modules`, sources, Vite dev server |
| `tickets-frontend:multistage`  | **~90 MB**  | `nginx` (uid 101)   | nginx + built static files            |

Things to try:

```bash
docker history tickets-backend:multistage      # see the layers
# change a .java file, rebuild both images and compare the build time:
# the multi-stage build reuses the cached dependency layer
```

### 3.6 Clean up

```bash
docker rm -f tickets-frontend tickets-backend tickets-db
docker network rm tickets-net
docker volume rm tickets-db-data      # deletes the database data!
```

---

## 4. Docker Compose: the whole stack with one command

[`compose.yaml`](compose.yaml) describes the same three containers, their network, the volume, environment variables and the start-up order (`depends_on` + healthchecks). It builds the images from the `Dockerfile.multistage` files.

```bash
cp .env.example .env               # first time only: DB credentials and ports (see section 5)
docker compose up --build          # foreground, Ctrl+C to stop
docker compose up -d --build       # in the background
docker compose ps                  # status (incl. health)
docker compose logs -f backend     # logs of one service
docker compose exec db psql -U tickets   # shell into a service
docker compose down                # stop and remove containers (data volume is kept)
docker compose down -v             # ... and delete the database volume
```

| URL                                      | What                     |
|------------------------------------------|--------------------------|
| <http://localhost:8081>                  | Vue frontend             |
| <http://localhost:8080/swagger-ui.html>  | Swagger UI (backend)     |
| <http://localhost:8080/actuator/health>  | Backend health           |

Compose creates a network called `tickets_default`, and each **service name** (`db`, `backend`, `frontend`) is the hostname on it. That is why the backend uses `DB_HOST: db` and the frontend uses `BACKEND_URL: http://backend:8080`.

### `docker compose` or `docker-compose`?

- **`docker-compose`** (with a hyphen) is **Compose v1**, a separate Python program. It has been deprecated since 2023 and no longer receives updates.
- **`docker compose`** (with a space) is **Compose v2**, written in Go and installed as a Docker CLI plugin (`docker-compose-plugin`, included in Docker Desktop). Use this one.
- The file name changed the same way: **`compose.yaml`** is the preferred name in the [Compose Specification](https://compose-spec.io/). `docker-compose.yml` still works. The top-level `version:` key is obsolete and can be removed.

### Why is there an nginx in the frontend container?

It is not strictly required, but it is the usual way to serve a Vue app in production:

1. `npm run build` produces **static files** (HTML/JS/CSS). They need a web server. Running the Vite dev server in production (what the naive `Dockerfile` does) is slow, heavy (~1.8 GB) and not meant for production.
2. nginx also acts as a **reverse proxy**: the browser calls `/api/...` on the same origin, and nginx forwards it to `backend:8080`. This avoids **CORS** configuration, and the backend does not need to be reachable from the browser.

nginx is part of the **frontend image** (see [frontend/Dockerfile.multistage](frontend/Dockerfile.multistage)), so it is *not* a separate service in `compose.yaml`. In a bigger setup you might add a dedicated reverse proxy (nginx, Traefik, Caddy) in front of everything. In Kubernetes that job is done by an Ingress controller.

---

## 5. Environment variables and configuration

The rule: **build the image once, configure it at start-up.** No address or password is written into an image. The same image runs on your laptop, in Compose and later in Kubernetes, with different environment variables.

### Where variables come from

| Mechanism | Example in this repo | Applies |
|---|---|---|
| `ENV` in a Dockerfile | `ENV BACKEND_URL=...` (frontend), `ENV JAVA_TOOL_OPTIONS=...` (backend) | a **default** stored in the image, can be overridden |
| `ARG` in a Dockerfile | (not used) | **build time only**, not present in the running container |
| `docker run -e` / `--env-file` | `docker run -e DB_HOST=tickets-db ...` | container start |
| Compose `environment:` | `DB_HOST: db`, `BACKEND_URL: http://backend:8080` | container start |
| Compose `env_file:` | (not used) | container start, values loaded from a file |
| `.env` next to `compose.yaml` | `POSTGRES_PASSWORD=change-me` | fills `${VAR}` placeholders **in compose.yaml itself** |

When the same variable is set in several places, Compose `environment:` wins over `env_file:`, which wins over the image's `ENV`. A default inside the app (e.g. `${DB_HOST:localhost}` in Spring) is used only if none of them sets it.

Look at the variables of a running container:

```bash
docker compose exec backend printenv | grep DB_
docker inspect tickets-backend-1 --format '{{json .Config.Env}}'
```

### The `.env` file

[`.env.example`](.env.example) is committed and lists every variable. **`.env`** holds the real values and is in `.gitignore`. The dev container creates it automatically.

```bash
cp .env.example .env    # then edit the password
```

Compose reads `.env` automatically and substitutes the values into [`compose.yaml`](compose.yaml):

| Syntax | Meaning | Example |
|---|---|---|
| `${VAR:-default}` | use `VAR`, or `default` if it is unset or empty | `"${FRONTEND_PORT:-8081}:8080"` |
| `${VAR:?message}` | **required**: Compose refuses to start and prints `message` | `${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}` |
| `$${VAR}` | a literal `$`: the **container's** shell evaluates it, not Compose | `pg_isready -U $${POSTGRES_USER}` |

```bash
docker compose config                         # final config, all variables substituted
docker compose --env-file /dev/null config    # shows the error for the missing password
POSTGRES_PASSWORD=other docker compose config # shell variables override .env
```

The password is defined **once** in `.env` and used by two services: `db` (`POSTGRES_PASSWORD`) and `backend` (`DB_PASSWORD`).

> **Note:** PostgreSQL applies `POSTGRES_*` only when it **creates** the database, the first time the volume is empty. If you change the password later, run `docker compose down -v` (this deletes the data) or change it in `psql` with `ALTER USER`.

### How each component reads its configuration

- **Backend:** Spring Boot reads environment variables at start-up (`DB_HOST`, `DB_PASSWORD`, ..., or any property such as `SPRING_JPA_SHOW_SQL`). See [backend/README.md](backend/README.md#configuration).
- **Frontend:** the browser cannot read environment variables. The app calls relative `/api` URLs, and the **Vite dev server** or **nginx** reads `BACKEND_URL` and proxies the calls. See [frontend/README.md](frontend/README.md#configuration).

### Secrets

Environment variables are fine for a playground, but anyone with Docker access can read them with `docker inspect`. They can also end up in logs. For real secrets use [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/), which are mounted as files under `/run/secrets/`, or Kubernetes Secrets / a secret manager. **Never** put secrets in a Dockerfile (`ENV` or `ARG`): they become part of the image and are visible with `docker history`.

---

## 6. Running without Docker (for development)

You still need PostgreSQL, so start just the DB container:

```bash
docker compose up -d db     # needs "ports: 5432:5432" uncommented in compose.yaml
# or use the docker run command from 3.2

cd backend  && mvn spring-boot:run      # http://localhost:8080
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```

Tests (no database needed, they use H2 / mocks):

```bash
cd backend  && mvn test
cd frontend && npm test
```

---

## Links

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

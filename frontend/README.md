# Frontend: Tickets UI (Vue 3 + Vite)

A single-page app for the [Tickets API](../backend/README.md).

- **USER** (`alice`, `bob`): create tickets, and edit or delete their own `OPEN` tickets.
- **AGENT** (`agent`): see all tickets, **Assign to me**, and **Resolve** them with a resolution text.

The password for every user is `password`.

```
src/
├── main.js
├── App.vue                    header, login / logout
├── api.js                     fetch() wrapper, HTTP Basic auth
├── style.css
├── components/
│   ├── LoginForm.vue
│   └── TicketBoard.vue        list + form, buttons depend on the role
└── __tests__/                 Vitest tests
```

All API calls use **relative** URLs (`/api/...`). The frontend never needs the backend's address itself:

- in development, the **Vite dev server** proxies `/api` to the backend ([vite.config.js](vite.config.js))
- in production, the **web server** that serves the built files must do the same (that is part of the Docker exercises)

Because the browser only talks to one origin, no CORS configuration is needed.

## Run locally

Requirements: Node.js 22+ (24 recommended; it is already in the dev container) and the backend running on `http://localhost:8080` (see the [backend README](../backend/README.md)).

```bash
npm install
npm run dev          # http://localhost:5173
```

If the backend runs somewhere else:

```bash
BACKEND_URL=http://some-host:8080 npm run dev
```

Production build (static files in `dist/`):

```bash
npm run build
npm run preview
```

## Configuration

The frontend has a single setting: **`BACKEND_URL`**, the address of the Spring Boot API. The browser never sees it.

### Why the browser code has no backend URL

The Vue app is just static JS running in the user's browser, and a browser cannot read environment variables. So [api.js](src/api.js) only uses **relative** URLs (`/api/tickets`), and the server that delivers the page forwards `/api` to the backend:

In development, [vite.config.js](vite.config.js) reads `process.env.BACKEND_URL` when the dev server starts, and proxies `/api` to it. The default is `http://localhost:8080`:

```bash
BACKEND_URL=http://some-host:8080 npm run dev
```

After `npm run build` there is no Vite server any more, only static files in `dist/`. Whatever serves them (e.g. nginx in a container) has to proxy `/api` to the backend, with the backend address set when the container **starts**.

### Why not `VITE_API_URL`?

Vite supports `import.meta.env.VITE_*` variables (from `.env` files or the shell). **They are replaced with their values during `npm run build`**, so the URL is baked into the JS bundle inside the image. A different backend address would then need a **new image** for every environment. That breaks the "build once, configure at start-up" rule.

Use `VITE_*` only for values that really are fixed at build time (app version, feature flags), and **never for secrets**: everything in the bundle can be read by anyone who opens the page.

## Tests

Tests use [Vitest](https://vitest.dev/) and [Vue Test Utils](https://test-utils.vuejs.org/) with jsdom. They mock the API, so **no backend is needed**:

```bash
npm test             # run once
npm run test:watch   # re-run on file changes
```

| Test file                                                       | What it checks                                              |
|-----------------------------------------------------------------|-------------------------------------------------------------|
| [api.test.js](src/__tests__/api.test.js)                         | Basic auth header, JSON body, error handling (mocked `fetch`) |
| [TicketBoard.test.js](src/__tests__/TicketBoard.test.js)         | USER sees the form and edit buttons; AGENT can assign; creating a ticket |

## Containerizing the frontend

There is no Dockerfile yet. You write it in [steps 5–6 of the main README](../README.md#4-containerizing-the-app-step-by-step). The reference solution is on the `docker` branch.

### Facts you need for the Dockerfile

| What | Value |
|---|---|
| Node version | **24** (22+ works) |
| Install dependencies | `npm ci` (exact versions from `package-lock.json`, needs `package.json` + `package-lock.json` only) |
| Dev server | `npm run dev` → port **5173**, already listens on all interfaces (`host: true` in [vite.config.js](vite.config.js)) |
| Dev server backend address | env var `BACKEND_URL` (default `http://localhost:8080`) |
| Production build | `npm run build` → static files in **`dist/`** (`index.html` + `assets/`) |
| What the production server must do | serve `dist/`; proxy **`/api/`** to the backend; return `index.html` for unknown paths |
| Tests | `npm test` (no backend needed) |

### Step by step

1. **Run it locally** (`npm install`, `npm test`, `npm run dev`) against a running backend.
2. **`.dockerignore`**: exclude at least `node_modules/`, `dist/`, `.git/`, IDE folders.
3. **Naive Dockerfile** (`Dockerfile`): Node image, copy everything, install, start the dev server. Run it on the backend's network with `BACKEND_URL=http://<backend container name>:8080`.
4. **Multi-stage Dockerfile** (`Dockerfile.multistage`):
   1. build stage: Node → copy `package.json` + `package-lock.json` → `npm ci` → copy sources → `npm run build`
   2. runtime stage: a non-root web server (e.g. `nginxinc/nginx-unprivileged:alpine`) → your server config → copy **only** `dist/` from the build stage → `HEALTHCHECK`
5. **Web server config** (e.g. `nginx/default.conf.template`): static files, `/api/` proxy to `${BACKEND_URL}`, SPA fallback to `index.html`.

### What to take care of

- **Never copy your local `node_modules`** into the image. It can contain binaries for your OS/CPU. Install inside the image.
- **`npm ci`, not `npm install`**, in Dockerfiles: reproducible builds, and it fails if the lock file is out of date.
- **Layer order:** package files + `npm ci` first, sources afterwards, so a code change does not reinstall everything.
- **The browser cannot read environment variables.** Keep the API calls relative (`/api/...`) and let the web server proxy them. Do **not** put the backend URL in a `VITE_*` variable: it would be fixed at build time inside the JS bundle.
- **Configure the backend URL at container start:** the official nginx images render `/etc/nginx/templates/*.template` with `envsubst` at start-up (see the *Using environment variables in nginx configuration* section of the image docs).
- **Non-root ⇒ port ≥ 1024.** The unprivileged nginx listens on **8080**, not 80. Publish with e.g. `-p 8081:8080`.
- **SPA fallback:** without it, reloading the page on a client-side route returns 404.
- **Healthcheck address:** use `127.0.0.1` (or make nginx listen on IPv6 too), because `localhost` may resolve to `::1`.

### How to check

```bash
docker build -t tickets-frontend:naive .
docker build -f Dockerfile.multistage -t tickets-frontend:multistage .
docker images | grep tickets-frontend            # compare sizes
docker run --rm tickets-frontend:multistage id   # must not be root
docker exec <frontend container> cat /etc/nginx/conf.d/default.conf   # is BACKEND_URL filled in?
curl -u alice:password http://localhost:8081/api/tickets             # proxied to the backend
```

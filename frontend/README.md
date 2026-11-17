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
nginx/default.conf.template    nginx config used by the Docker image
```

All API calls use **relative** URLs (`/api/...`). The frontend never needs the backend's address itself:

- in development, the **Vite dev server** proxies `/api` to the backend ([vite.config.js](vite.config.js))
- in Docker, **nginx** proxies `/api` to the backend ([nginx/default.conf.template](nginx/default.conf.template))

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

| Mode | Who reads `BACKEND_URL` | When | Default |
|---|---|---|---|
| `npm run dev` | [vite.config.js](vite.config.js) via `process.env.BACKEND_URL` → dev-server proxy | dev server start | `http://localhost:8080` |
| Docker, naive image | same Vite dev server inside the container | container start | `http://localhost:8080` |
| Docker, multistage image | nginx: [default.conf.template](nginx/default.conf.template) contains `proxy_pass ${BACKEND_URL};` | container start | `ENV BACKEND_URL=http://tickets-backend:8080` in the Dockerfile |

How nginx gets the value: the nginx image runs `envsubst` on every `/etc/nginx/templates/*.template` file at start-up and writes the result to `/etc/nginx/conf.d/`. Only variables that are actually set are replaced, so nginx's own `$uri`, `$host`, ... are left alone. Check the result:

```bash
docker compose exec frontend cat /etc/nginx/conf.d/default.conf | grep proxy_pass
```

```bash
# override at start-up, no rebuild needed
docker run -e BACKEND_URL=http://my-backend:8080 -p 8081:8080 tickets-frontend:multistage
```

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

## Docker

### Step 1: naive [Dockerfile](Dockerfile)

```bash
docker build -t tickets-frontend:naive .
docker run --rm -p 5173:5173 -e BACKEND_URL=http://<backend-host>:8080 tickets-frontend:naive
```

This image runs the **Vite dev server** as **root**. It contains Node, all of `node_modules` and the sources, which makes it about **1.8 GB**.

### Step 2: [Dockerfile.multistage](Dockerfile.multistage)

```bash
docker build -f Dockerfile.multistage -t tickets-frontend:multistage .
```

- **Build stage** (`node:24-alpine`): copies `package*.json` first and runs `npm ci` (a cached layer), then runs `npm run build`.
- **Runtime stage** (`nginxinc/nginx-unprivileged:alpine`): only nginx and `dist/`, about **90 MB**.
- Runs as the non-root user `nginx` (uid 101). It therefore listens on port **8080**, because a non-root user cannot bind to ports below 1024.
- At start-up the nginx image renders `/etc/nginx/templates/*.template` with `envsubst`, which is how the `BACKEND_URL` variable ends up in the nginx config.

### Run it

The frontend container must be on the same network as the backend container:

```bash
docker run -d --name tickets-frontend --network tickets-net \
  -e BACKEND_URL=http://tickets-backend:8080 \
  -p 8081:8080 \
  tickets-frontend:multistage
```

Open <http://localhost:8081>.

Or start the full stack from the repository root with `docker compose up --build` (see the [main README](../README.md)).

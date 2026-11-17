# ---------------------------------------------------------------------------
# Dockerfile — the "naive" version (step 1)
#
# Runs the Vite *development* server inside the container. It works, but:
#   * the image contains node_modules, all dev tooling and the source -> big
#   * it serves an unoptimised dev build (not meant for production)
#   * runs as root
#   * COPY . . before npm install -> any file change reinstalls all packages
#
# Build:  docker build -t tickets-frontend:naive .
# Run:    docker run --rm -p 5173:5173 -e BACKEND_URL=http://<backend>:8080 tickets-frontend:naive
# ---------------------------------------------------------------------------
FROM node:24

WORKDIR /app

COPY . .

RUN npm install

EXPOSE 5173

CMD ["npm", "run", "dev"]

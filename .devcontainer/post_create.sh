#!/usr/bin/env bash
# Runs once after the dev container is created.
set -euo pipefail

# Debug tools: netcat (port checks, e.g. `nc -zv localhost 5432`) and psql client.
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  netcat-openbsd postgresql-client

# Handy Docker aliases.
cat >> ~/.bashrc <<'ALIASES'

# --- docker-playground aliases ---
alias d='docker'
alias dps='docker ps'
alias dpsa='docker ps -a'
alias di='docker images'
alias dlogs='docker logs -f'
alias dprune='docker system prune -f'
alias dc='docker compose'
alias c=clear
ALIASES

# Pre-fetch Maven dependencies so the first `mvn spring-boot:run` is fast.
if [ -f backend/pom.xml ]; then
  (cd backend && mvn -B -q dependency:go-offline) || echo "Maven pre-fetch failed (not fatal)."
fi

# Install frontend dependencies.
if [ -f frontend/package.json ]; then
  (cd frontend && npm ci) || echo "npm ci failed (not fatal)."
fi

echo "Dev container ready. Try: docker version && docker run --rm hello-world"

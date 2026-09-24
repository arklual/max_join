#!/bin/bash
# Запускать на VPS после rsync
set -e

echo "=== Checking .env..."
if [ ! -f /opt/join/.env ]; then
  echo "ERROR: .env not found at /opt/join/.env"
  exit 1
fi

echo "=== Checking certificates..."
if [ ! -f /opt/join/nginx/certs/cert.crt ] || [ ! -f /opt/join/nginx/certs/cert.key ]; then
  echo "ERROR: cert files missing. Place cert.crt and cert.key into /opt/join/nginx/certs/"
  exit 1
fi

# Install Docker if needed
if ! command -v docker &> /dev/null; then
  echo "=== Installing Docker..."
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | tee /etc/apt/sources.list.d/docker.list > /dev/null
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  systemctl enable --now docker
  echo "--- Docker installed: $(docker --version)"
else
  echo "--- Docker already installed: $(docker --version)"
fi

cd /opt/join
echo "=== Building and starting services..."
docker compose up --build -d

echo ""
echo "Done! Check: docker compose ps"

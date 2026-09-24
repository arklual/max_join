#!/bin/bash
# Запускать ЛОКАЛЬНО: скопирует проект + сертификаты на VPS и запустит setup
# Использование: ./deploy.sh <vps-ip> <путь/к/cert.crt> <путь/к/cert.key>
set -e

VPS_IP="${1:?Usage: ./deploy.sh <vps-ip> <cert.crt> <cert.key>}"
CERT_CRT="${2:?Provide path to cert.crt}"
CERT_KEY="${3:?Provide path to cert.key}"
VPS_USER="root"
REMOTE_DIR="/opt/join"

echo "=== Syncing project to $VPS_IP:$REMOTE_DIR ..."
ssh "$VPS_USER@$VPS_IP" "mkdir -p $REMOTE_DIR/nginx/certs"

rsync -az --delete \
  --exclude='.git' \
  --exclude='.env' \
  --exclude='front/node_modules' \
  --exclude='front/dist' \
  --exclude='back/build' \
  --exclude='back/.gradle' \
  ./ "$VPS_USER@$VPS_IP:$REMOTE_DIR/"

echo "=== Uploading SSL certificates..."
scp "$CERT_CRT" "$VPS_USER@$VPS_IP:$REMOTE_DIR/nginx/certs/cert.crt"
scp "$CERT_KEY" "$VPS_USER@$VPS_IP:$REMOTE_DIR/nginx/certs/cert.key"
# restrict private key permissions (owner read-only)
ssh "$VPS_USER@$VPS_IP" "chmod u=r,go= $REMOTE_DIR/nginx/certs/cert.key"

echo "=== Running VPS setup..."
ssh "$VPS_USER@$VPS_IP" "chmod +x $REMOTE_DIR/setup-vps.sh && $REMOTE_DIR/setup-vps.sh"

echo ""
echo "=== Deploy complete! ==="

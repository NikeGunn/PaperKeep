# ScanVault — Staging Deployment Guide

Manual steps to deploy the Go API on a fresh Ubuntu 24.04 VPS with Caddy + systemd.

---

## Prerequisites

- Ubuntu 24.04 VPS (2 vCPU / 2 GB RAM minimum)
- Root SSH access
- Domain `api.scanvault.app` pointed at the VPS IP (A record)
- Postgres 16 running locally (or accessible over private network)

---

## 1. Create system user

```bash
useradd --system --shell /usr/sbin/nologin --create-home --home-dir /opt/scanvault scanvault
mkdir -p /opt/scanvault/bin /var/log/scanvault
chown -R scanvault:scanvault /opt/scanvault /var/log/scanvault
```

---

## 2. Build and upload the binary

On your dev machine:

```bash
cd backend
make build
# Produces bin/scanvault-api (statically linked, CGO_ENABLED=0)

scp bin/scanvault-api root@<VPS_IP>:/opt/scanvault/bin/scanvault-api
ssh root@<VPS_IP> "chmod 755 /opt/scanvault/bin/scanvault-api"
```

---

## 3. Create the environment file

The systemd unit references `EnvironmentFile=/etc/scanvault/scanvault.env`.
Create that file with all required variables:

```bash
mkdir -p /etc/scanvault
cat > /etc/scanvault/scanvault.env <<'EOF'
SERVER_PORT=8080
DATABASE_URL=postgres://scanvault:<password>@localhost:5432/scanvault?sslmode=disable
PASETO_KEY=<32-byte base64 key>
ARGON2_TIME=2
ARGON2_MEMORY=65536
ARGON2_THREADS=2
POSTMARK_TOKEN=<postmark server token>
R2_ENDPOINT=https://<account>.r2.cloudflarestorage.com
R2_ACCESS_KEY=<r2 access key>
R2_SECRET_KEY=<r2 secret key>
R2_BUCKET=scanvault-docs
IP_HASH_KEY=<32-byte random string>
ENVIRONMENT=production
EOF

# Lock down permissions — readable only by root and the scanvault user
chmod 0600 /etc/scanvault/scanvault.env
chown root:scanvault /etc/scanvault/scanvault.env
```

Generate the PASETO_KEY:

```bash
openssl rand -base64 32
```

---

## 4. Install the systemd unit

```bash
cp deploy/scanvault.service /etc/systemd/system/scanvault.service
systemctl daemon-reload
systemctl enable scanvault
systemctl start scanvault
systemctl status scanvault
```

Check logs:

```bash
journalctl -u scanvault -f
```

---

## 5. Install Caddy

```bash
apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | tee /etc/apt/sources.list.d/caddy-stable.list
apt update
apt install -y caddy
```

---

## 6. Deploy the Caddyfile

```bash
cp deploy/Caddyfile /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile
systemctl restart caddy
systemctl status caddy
```

Caddy automatically provisions a Let's Encrypt TLS certificate for `api.scanvault.app` on first start.

---

## 7. Verify the deployment

```bash
# Health check
curl -s https://api.scanvault.app/health | jq .

# TLS version — must report TLSv1.3
curl -v https://api.scanvault.app/health 2>&1 | grep "SSL connection"

# HSTS header present
curl -sI https://api.scanvault.app/health | grep -i strict-transport
```

Expected health response:

```json
{"status":"ok","version":"x.y.z","commit":"abc1234"}
```

---

## 8. Postgres setup

```bash
apt install -y postgresql-16
sudo -u postgres psql -c "CREATE USER scanvault WITH PASSWORD '<password>';"
sudo -u postgres psql -c "CREATE DATABASE scanvault OWNER scanvault;"

# Run migrations
cd /opt/scanvault
DATABASE_URL="postgres://scanvault:<password>@localhost:5432/scanvault?sslmode=disable" \
  goose -dir /opt/scanvault/migrations postgres "$DATABASE_URL" up
```

---

## 9. Updates and rollback

**Deploy a new version:**

```bash
# On dev machine
make build
scp bin/scanvault-api root@<VPS_IP>:/opt/scanvault/bin/scanvault-api-new
ssh root@<VPS_IP> "
  mv /opt/scanvault/bin/scanvault-api /opt/scanvault/bin/scanvault-api-prev &&
  mv /opt/scanvault/bin/scanvault-api-new /opt/scanvault/bin/scanvault-api &&
  systemctl restart scanvault &&
  systemctl status scanvault
"
```

**Rollback:**

```bash
ssh root@<VPS_IP> "
  mv /opt/scanvault/bin/scanvault-api /opt/scanvault/bin/scanvault-api-bad &&
  mv /opt/scanvault/bin/scanvault-api-prev /opt/scanvault/bin/scanvault-api &&
  systemctl restart scanvault
"
```

---

## File permissions summary

| Path | Owner | Mode |
|------|-------|------|
| `/opt/scanvault/bin/scanvault-api` | `scanvault:scanvault` | `755` |
| `/etc/scanvault/scanvault.env` | `root:scanvault` | `0600` |
| `/etc/caddy/Caddyfile` | `root:root` | `644` |
| `/etc/systemd/system/scanvault.service` | `root:root` | `644` |
| `/var/log/scanvault/` | `scanvault:scanvault` | `755` |

# SentinelCore SecureOps — Deployment Checklist

This source package is prepared for a typical split deployment:
- Frontend: Vercel (React/Vite)
- Backend: Render/Railway/Fly.io or another Docker host (Spring Boot)
- Database: Managed PostgreSQL

## 1. Never deploy secrets

Create environment variables in the hosting platform. Do **not** upload `backend/.env` or place passwords/API keys in source control.

## 2. Backend environment variables

Required:

```text
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
FRONTEND_URL=https://<your-frontend-domain>
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<strong-unique-password>
BOOTSTRAP_ADMIN_EMAIL=<admin-email>
SESSION_COOKIE_SAME_SITE=none
SESSION_COOKIE_SECURE=true
```

Optional SMTP:

```text
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=<sender-account>
SMTP_PASSWORD=<app-password>
SMTP_FROM=<sender-account>
```

Optional monitoring settings:

```text
TELEMETRY_INTERVAL_SECONDS=5
CPU_ALERT_THRESHOLD=90
MEMORY_ALERT_THRESHOLD=90
DISK_ALERT_THRESHOLD=90
ASSET_OFFLINE_TIMEOUT_SECONDS=60
AUTO_CREATE_INCIDENTS=false
NVD_API_KEY=
```

## 3. Frontend environment variables

On Vercel, set:

```text
VITE_API_URL=https://<your-backend-domain>
```

Then build with `npm run build`.

## 4. Database

Create a PostgreSQL database and use its JDBC connection URL. Hibernate is configured with `ddl-auto=update`, so tables are created/updated by the application. For a mature production environment, move to versioned migrations before making schema changes.

## 5. Backend health check

The public health endpoint is:

```text
/actuator/health
```

It is permitted without authentication so the hosting platform can perform health checks.

## 6. Monitoring limitation

OSHI reports metrics for the machine/container where the SentinelCore backend process is running. It does not automatically monitor every end-user laptop. Multi-device monitoring requires an endpoint agent or cloud-provider monitoring integration.

## 7. Pre-production verification

- [ ] Revoke any previously exposed Gmail App Password and create a fresh one.
- [ ] Configure all production environment variables in the hosting provider.
- [ ] Confirm PostgreSQL connectivity.
- [ ] Confirm `/actuator/health` returns UP.
- [ ] Login/logout works.
- [ ] RBAC returns 403 for unauthorized actions.
- [ ] Infrastructure SSE stream connects.
- [ ] CPU/RAM/disk telemetry changes on the monitored backend host.
- [ ] SMTP alert/report delivery works.
- [ ] Vercel frontend can call the backend.
- [ ] No secrets are present in Git history or deployment artifacts.

## Endpoint Agent / Multi-Device Monitoring

The hosted backend can receive telemetry from user-owned devices through the `agent/` module.

1. Sign in to SentinelCore and open **My Devices**.
2. Click **Generate Device Token**. The token is shown once.
3. Build the agent with Java 17+ and Maven: `mvnw.cmd clean package` from `agent/`.
4. Copy the shaded JAR to the user's device.
5. Configure `SENTINELCORE_URL` with the hosted backend URL and `SENTINELCORE_AGENT_TOKEN` with the device token.
6. Run the JAR. The agent sends telemetry every 5 seconds by default.
7. The device appears under the user's account and live telemetry is available through the My Devices page.

The agent token is stored as a SHA-256 hash on the server and can be revoked from the My Devices page. The agent does not require the user's SentinelCore password.

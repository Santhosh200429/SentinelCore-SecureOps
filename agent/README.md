# SentinelCore Endpoint Agent

This agent lets a user connect their own Windows/Linux/macOS machine to a hosted SentinelCore installation.

## What it does
- Collects CPU, RAM, disk, network totals, process count, OS, hostname, processor and uptime using OSHI.
- Sends telemetry every 5 seconds by default over HTTPS.
- Uses a revocable per-device bearer token; it never needs the user's SentinelCore password.
- Creates/updates the device under the account that enrolled the token.

## Build
Requirements: Java 17+ and Maven.

    mvnw.cmd clean package

The shaded JAR is created under `target/sentinelcore-agent-1.0.0.jar`.

## Configure
Set:

    SENTINELCORE_URL=https://your-backend.example.com
    SENTINELCORE_AGENT_TOKEN=<token from the SentinelCore Devices page>
    SENTINELCORE_INTERVAL_SECONDS=5

On Windows you can use `run-agent.bat` after copying the built JAR beside it.

## Security
Use HTTPS in production. Treat the device token like a password: do not commit it to Git, screenshots or support tickets. Revoke it from SentinelCore when the device is removed.

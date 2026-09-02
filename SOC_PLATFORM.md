# SentinelCore SOC Platform

## Operational flow
Endpoint Agent -> authenticated telemetry/security events -> Spring Boot ingestion -> PostgreSQL -> detection/correlation -> alerts -> incidents -> audit/compliance/reporting.

## Security events
The Windows endpoint agent collects selected Windows Security events (4624 successful logon, 4625 failed logon, 4688 process creation) when running on Windows. Events are deduplicated with a machine/record fingerprint and stored as evidence.

## Detection rule
Five or more Windows failed-logon events (Event ID 4625) from the same endpoint within five minutes create a High alert. Set `AUTO_CREATE_INCIDENTS=true` to automatically create a High incident from this correlation rule.

## Production notes
- Use HTTPS for the agent URL.
- Keep agent tokens, database passwords and SMTP credentials in deployment environment variables.
- Rotate/revoke exposed development tokens.
- Add rate limiting/WAF controls to the public agent ingestion endpoint before production scale.
- Use managed PostgreSQL backups and schema migration tooling for production.
- The current application is a Spring Boot monolith; Kafka/Keycloak/microservices are not required for the working SOC flow and are not claimed as implemented.

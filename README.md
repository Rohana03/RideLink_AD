# RideLink — Backend Microservices for a Ride-Sharing Platform

IT3130 Application Development | Group Assignment

This is the **shared starting structure** — four bare Spring Boot projects
(pom.xml + application.yml + main class only), plus the infra each service
will plug into. No business logic is implemented yet; each member builds out
their own service inside their own folder.

## Service ownership

| # | Service | Port | Owner | Responsibility |
|---|---------|------|-------|-----------------|
| 1 | account-service | 8081 | *(assign name)* | Registration, login, JWT issuance, roles, profile, account status |
| 2 | driver-vehicle-service | 8082 | *(assign name)* | Driver/vehicle profile, availability, location, eligible-driver search |
| 3 | ride-management-service | 8083 | Kavinda (IT24102171) | Ride requests, driver assignment, ride lifecycle, cancellation |
| 4 | fare-payment-service | 8084 | *(assign name)* | Fare estimation, final fare calculation, simulated payment, receipts |

## Shared conventions (agree on these before splitting off to work solo)

- **Package base:** `lk.sliit.ridelink.<service>` — `account`, `driver`, `ride`, `fare`.
  Each service's folders under `src/main/java/.../` (`config`, `controller`,
  `dto`, `entity`, `repository`, `service`, `exception`, `client`) are already
  there as empty placeholders — build into them.
- **Auth:** Account Service issues a JWT (HS256). Every other service should
  verify it locally using the same `JWT_SECRET` env var — no per-request call
  back to Account Service. Put a `JwtUtil` + a `OncePerRequestFilter` in each
  service's `config/` package.
- **Data ownership:** each service gets its own database — never query
  another service's data directly. Account and Driver & Vehicle use MongoDB
  (`account_db`, `driver_db`) on a local server at `localhost:27017`.
- **Interservice calls:** Ride Management needs to call Driver & Vehicle
  (find eligible drivers) and Fare & Payment (get an estimate) — plan whether
  each interaction is synchronous REST or asynchronous messaging, and be
  ready to justify the choice in the report. `spring-boot-starter-amqp` is
  already in the Ride Management and Fare & Payment poms if you want RabbitMQ
  for the async side.
- **Errors:** keep error responses consistent — `{timestamp, status, error,
  message, path}` — via a `@RestControllerAdvice` in each service's
  `exception/` package.
- **Internal-only endpoints** (e.g. Driver & Vehicle exposing "eligible
  drivers" only to Ride Management) should sit under their own path prefix
  and be protected differently from user-facing JWT endpoints — e.g. a shared
  `X-Internal-Api-Key` header (env var already scaffolded in
  `driver-vehicle-service`'s and `ride-management-service`'s `application.yml`).

See `docs/architecture-diagram.md` and `docs/sequence-diagram.md` for
diagrams to paste into [mermaid.live](https://mermaid.live) — fill these in
once the real flow is settled.

## Prerequisites

- JDK 17, Maven 3.9+
- MongoDB Community Server running locally on port 27017 (no Docker needed)
- Postman and a browser (for Swagger UI, already wired via springdoc)

## Configuration

Copy `.env.example` to `.env` and agree on real values as a group — `JWT_SECRET`
and `INTERNAL_API_KEY` must be identical across all four services or nothing
will authenticate correctly between them. `.env` is git-ignored; never commit it.

## Running what's here so far

```bash
# MongoDB must be running locally; databases are created on first use.
cd account-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
The `dev` profile reads a git-ignored `src/main/resources/application-dev.yml`
holding local secrets — see each service's README.
Each service currently boots to an empty Spring Boot app on its assigned
port with Swagger UI at `/swagger-ui.html` — there's nothing to call yet
until controllers are added.

## Workflow

1. Push this structure to `main` first.
2. Each member clones, branches (`feature/<student-id>/<service>`), and
   builds out their own service folder only.
3. Open a PR into `main` when a piece is ready; get it reviewed before merging.
4. CI (`.github/workflows/ci.yml`) runs `mvn clean verify` for all four
   services on every push/PR — keep it green.

## Repository structure

```
ridelink/
├── account-service/
├── driver-vehicle-service/
├── ride-management-service/
├── fare-payment-service/
├── docs/
├── .env.example
└── .github/workflows/ci.yml
```

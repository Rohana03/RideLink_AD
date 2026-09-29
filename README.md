# RideLink — Backend Microservices for a Ride-Sharing Platform

IT3130 Application Development | Group Assignment

RideLink is a fictional ride-sharing backend made of four independently runnable Spring Boot microservices.
Passengers register, get a fare estimate, request a ride and are matched with the nearest available driver;
drivers accept, start and complete the ride; the final fare is calculated and a simulated payment and receipt
are recorded. There is no frontend: Swagger UI and the Postman collection are the clients.

## Service ownership

| # | Service | Port | Database | Owner | Responsibility |
|---|---------|------|----------|-------|----------------|
| 1 | [account-service](account-service/README.md) | 8081 | `account_db` | *(name, student ID)* | Registration, login and JWT issuance, roles, profiles, account status |
| 2 | [driver-vehicle-service](driver-vehicle-service/README.md) | 8082 | `driver_db` | *(name, student ID)* | Driver profile, vehicle, availability, service area, location, eligible-driver search |
| 3 | [ride-management-service](ride-management-service/README.md) | 8083 | `ride_db` | Kavinda (IT24102171) | Ride requests, driver assignment, ride lifecycle, cancellation, retrieval |
| 4 | [fare-payment-service](fare-payment-service/README.md) | 8084 | `fare_payment_db` | *(name, student ID)* | Fare estimate, final fare, simulated payment, payment status, receipts |

Each service's README documents its endpoints, business rules, error codes and tests.

## Architecture

- **Stack:** Java 17, Spring Boot 3.3, Spring Security, Spring Data MongoDB, springdoc-openapi, JUnit 5 + Mockito.
- **Data ownership:** one MongoDB database per service; no service reads another's data. Services share
  only stable IDs (`userId`, `driverId`, `rideId`).
- **Authentication:** the Account Service issues an HS256 JWT (`userId`, `roles`); every service verifies it
  locally with the shared `JWT_SECRET` and enforces roles (`PASSENGER`, `DRIVER`, `ADMIN`).
- **Service-to-service calls:** only Ride Management calls other services, with the shared `X-Internal-Api-Key`:

| Interaction | Style | Why |
|---|---|---|
| Ride requested → Fare & Payment estimate | sync REST | the passenger needs the number in the response |
| Driver assignment → Driver & Vehicle eligible search + `ON_TRIP` | sync REST | assignment cannot continue without the answer |
| Ride completed → Fare & Payment final fare and payment | REST by default, or async RabbitMQ `RideCompletedEvent` | payment set-up must not block or fail the driver's action |

Diagrams: [architecture](docs/architecture-diagram.md) · [sequence](docs/sequence-diagram.md)

## Prerequisites

- JDK 17 and Maven 3.9+
- MongoDB Community Server running locally on `localhost:27017` (no authentication needed for local use).
  The four databases are created automatically on first use.
- Postman (or Newman) for the end-to-end collection; a browser for Swagger UI
- Optional: RabbitMQ on `localhost:5672`, only if you want the asynchronous completion event

## Configuration

Secrets have **no defaults** in the committed `application.yml` files, so a service will not start without them.
See [.env.example](.env.example) for every variable. For local development, create a git-ignored
`src/main/resources/application-dev.yml` in each service:

```yaml
ridelink:
  jwt:
    secret: <same random string of 32+ characters in all four services>
  internal:
    api-key: <same random string in all four services>
  # account-service only - bootstrap admin (ADMIN cannot self-register)
  admin:
    email: admin@ridelink.lk
    password: <choose one>
```

`application-dev.yml` is listed in `.gitignore`; never commit it.

## Start-up

Start MongoDB, then the services. Account, Driver & Vehicle and Fare & Payment have no start-up dependencies;
start Ride Management last because it calls Driver & Vehicle and Fare & Payment.

```bash
cd account-service         && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # :8081
cd driver-vehicle-service  && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # :8082
cd fare-payment-service    && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # :8084
cd ride-management-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # :8083
```

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Account | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| Driver & Vehicle | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| Ride Management | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| Fare & Payment | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |

In Swagger UI, log in with `POST /api/auth/login` on the Account Service, copy `accessToken`, then click
**Authorize** on any service and paste it.

## Tests

Unit and controller tests need no database or broker:

```bash
cd <service> && mvn clean verify
```

| Service | Tests |
|---|---|
| account-service | 59 |
| driver-vehicle-service | 47 |
| ride-management-service | 59 |
| fare-payment-service | 55 |

**End-to-end:** with all four services running, import `postman/RideLink.postman_collection.json` and
`postman/RideLink-Local.postman_environment.json`, set `internalApiKey` and `adminPassword` to your local values,
and run the collection. It walks all seven required workflows (54 requests, each asserting its status) and can
also be run from the command line:

```bash
npx newman run postman/RideLink.postman_collection.json -e postman/RideLink-Local.postman_environment.json \
  --env-var internalApiKey=<your key> --env-var adminPassword=<your admin password>
```

## Sample test data

The Postman collection creates its own data on every run (a fresh run ID keeps emails and plates unique):

| Who | Values |
|---|---|
| Passenger | `amaya.<run>@example.com` / `Passw0rd123` |
| Driver | `nimal.<run>@example.com` / `Passw0rd123`, Toyota Aqua (CAR) at Galle Fort `6.0269, 80.2170` |
| Admin | `admin@ridelink.lk` / the `ADMIN_PASSWORD` you configured |
| Ride | Galle Fort → Unawatuna; completed with 5.4 km / 14 min → final fare **564.00 LKR** |
| Declined card | any fictional number ending in `0000`, e.g. `4111111111110000` |
| Approved card | e.g. `4111111111111111` (only the last four digits are stored) |

All people, vehicles, locations and payments are fictional or simulated.

## Negative scenarios demonstrated

No available driver (409), invalid ride status transition (409), unauthorised access (401/403), invalid input
(400), declined simulated payment (402), duplicate account (409), suspended account login (403).

## Git workflow and CI

- `main` holds the integrated, assessed release; `Develop` is the integration branch.
- Work happens on a branch per service (`Account`, `Driver`, `Ride`, `Payment`) or feature (`feature/*`) and is
  merged into `Develop` through a reviewed pull request; `Develop` is merged into `main` for the release tag.
- [CI](.github/workflows/ci.yml) builds and tests all four services on every push and on pull requests into
  `Develop` and `main`, and uploads the test reports.

## Repository structure

```
RideLink/
├── account-service/
├── driver-vehicle-service/
├── ride-management-service/
├── fare-payment-service/
├── postman/                  # end-to-end collection + local environment
├── docs/                     # architecture and sequence diagrams (Mermaid)
├── .env.example
└── .github/workflows/ci.yml
```

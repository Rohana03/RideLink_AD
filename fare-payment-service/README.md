# RideLink — Fare & Payment Service (Member 4)

**Owner:** Member 4  
**Port:** `8084`  
**Database:** `fare_payment_db` (MongoDB, collection `payments`)  
**Package:** `lk.sliit.ridelink.fare`  

---

## 1. Overview & Responsibilities

1. **Fare estimation** for a pickup and destination, using the documented rule below.
2. **Final fare calculation** when the Ride Management Service reports a completed ride.
3. **Simulated payment recording** (`CARD`, `CASH`, `WALLET`) — no real payment provider is contacted.
4. **Payment status** tracking: `PENDING` → `PAID`, or `FAILED` → retry → `PAID`.
5. **Receipt generation and retrieval** once a ride is paid.

---

## 2. Fare Rule

```
km      = measured trip distance, or the Haversine (straight-line) distance pickup -> destination
minutes = measured trip time,     or km / 30 km/h x 60
fare    = max(minimumFare, (baseFare + perKmRate x km + perMinRate x minutes) x vehicleMultiplier)
```

| Setting | Value (LKR) |
|---|---|
| `baseFare` | 100.00 |
| `perKmRate` | 60.00 |
| `perMinRate` | 10.00 |
| `minimumFare` | 200.00 |
| Vehicle multiplier | `BIKE` 0.6 · `TUK_TUK` 0.8 · `CAR` 1.0 · `SEDAN` 1.0 · `SUV` 1.3 · `VAN` 1.5 (default `CAR`) |

km and minutes are rounded to 2 decimals before charging, and money is rounded half-up to 2 decimals, so every
line of the breakdown can be checked by hand. **Worked example:** a `VAN` ride of 10 km in 20 min →
(100 + 60×10 + 10×20) × 1.5 = **1350.00 LKR**. All values live under `ridelink.fare` in `application.yml`.

The **estimate** uses straight-line distance; the **final fare** uses the measured `actualDistanceKm` /
`actualDurationMinutes` from the completed ride when the Ride service sends them, so the two can differ.

---

## 3. API Endpoints Reference

### 3.1 User Endpoints (`Authorization: Bearer <JWT from Account Service>`)

| Method | Endpoint | Description | Role | Status Code |
|---|---|---|---|---|
| `POST` | `/api/fares/estimate` | Fare estimate with full breakdown | any | `200 OK` |
| `POST` | `/api/payments/{rideId}/pay` | Pay for own completed ride | `PASSENGER` | `200 OK` |
| `GET` | `/api/payments/me` | Own payments, newest first | `PASSENGER` | `200 OK` |
| `GET` | `/api/payments/{rideId}` | Payment status | ride's passenger / driver, or `ADMIN` | `200 OK` |
| `GET` | `/api/payments?status=` | All payments, optionally filtered | `ADMIN` | `200 OK` |
| `GET` | `/api/receipts/{rideId}` | Receipt (only once `PAID`) | ride's passenger / driver, or `ADMIN` | `200 OK` |

### 3.2 Interservice Endpoints (`X-Internal-Api-Key: <key>`)

| Method | Endpoint | Description | Consumed By |
|---|---|---|---|
| `POST` | `/api/fares/internal/estimate` | Estimate when a ride is requested (sync REST) | Ride Management |
| `POST` | `/api/payments/internal/ride-completed` | Report a completed ride (REST alternative to the RabbitMQ event) | Ride Management |

### 3.3 Error Responses

All errors use the shared body `{timestamp, status, error, message, path}`.

| Status | When |
|---|---|
| `400 Bad Request` | Validation failure (coordinates out of range, missing fields), unknown enum value, `CARD` without a card number |
| `401 Unauthorized` | Missing/invalid JWT, or missing/invalid `X-Internal-Api-Key` |
| `402 Payment Required` | Simulated card declined — the payment is saved as `FAILED` and can be retried |
| `403 Forbidden` | Wrong role (e.g. a driver paying), or someone else's ride |
| `404 Not Found` | No completed ride recorded for that `rideId` |
| `409 Conflict` | Ride already `PAID`, receipt requested before payment, or a concurrent update |

---

## 4. Payment Rules (simulation)

| Method | Result |
|---|---|
| `CASH`, `WALLET` | Always approved |
| `CARD` | Approved, **unless the card number ends in `0000`** → declined (`402`, status `FAILED`) |

- Only the ride's passenger can pay; a `PAID` ride cannot be paid again.
- A `FAILED` payment can be retried with any method; `attempts` counts every try.
- Only the **last four digits** of a card are stored. Use fictional numbers such as `4111111111111111`.
- On success the payment gets a `transactionReference` (`TXN-…`) and a `receiptNumber` (`RCP-yyyyMMdd-…`).
- Each ride has at most one payment (unique index on `rideId`), so a repeated completion event never charges twice.

---

## 5. Contract for the Ride Management Service

### 5.1 Estimate (sync REST, when a ride is requested)

`POST http://localhost:8084/api/fares/internal/estimate` with header `X-Internal-Api-Key`:
```json
{ "pickupLatitude": 6.9344, "pickupLongitude": 79.8428,
  "dropoffLatitude": 6.8905, "dropoffLongitude": 79.8565, "vehicleType": "CAR" }
```
Returns `distanceKm`, `durationMinutes`, the charge breakdown, `totalFare` and `currency`.

### 5.2 Ride completed (async RabbitMQ, or REST)

| | |
|---|---|
| Exchange | `ride.events.exchange` (topic, durable) |
| Routing key | `ride.completed` |
| Queue | `ride.completed.queue` (durable, declared by this service) |
| Format | JSON (`Jackson2JsonMessageConverter`); the `__TypeId__` header is ignored |

The same JSON can be POSTed to `/api/payments/internal/ride-completed` instead:
```json
{ "rideId": "ride-123", "passengerId": "<account userId>",
  "driverId": "<driver document id>", "driverUserId": "<driver's account userId>",
  "vehicleType": "VAN",
  "pickupLatitude": 6.9271, "pickupLongitude": 79.8612,
  "dropoffLatitude": 6.9000, "dropoffLongitude": 79.8500,
  "actualDistanceKm": 10.0, "actualDurationMinutes": 20.0,
  "completedAt": "2026-09-24T10:15:00" }
```
Required: `rideId`, `passengerId` and the four coordinates. `driverUserId` lets the driver view the receipt.
An invalid message is rejected **without requeue** (logged and dropped) so it cannot loop forever.

**Why async here:** the driver's "complete ride" call should not wait for, or fail because of, payment set-up.
The event is durable in the queue, so a completed ride is not lost if this service is briefly down.
The estimate stays synchronous because the passenger needs the number immediately.

---

## 6. Running & Testing

### Configuration

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — (must match the Account Service) |
| `INTERNAL_API_KEY` | yes | — (must match the Ride Management Service) |
| `MONGODB_URI` | no | `mongodb://localhost:27017/fare_payment_db` |
| `MESSAGING_ENABLED` | no | `false` — set `true` to consume `RideCompletedEvent` from RabbitMQ |
| `RABBITMQ_HOST` / `PORT` / `USER` / `PASSWORD` | only if messaging is enabled | `localhost` / `5672` / `guest` / `guest` |

Put local values in a git-ignored `src/main/resources/application-dev.yml` and run with the `dev` profile.
With messaging disabled the service needs no RabbitMQ at all and the REST endpoint handles completed rides.

### Running
- **IDE:** Run `FarePaymentServiceApplication.java` with active profile `dev`
- **Maven:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

### Tests
```bash
mvn clean verify
```
55 tests: the fare rule against hand-worked values (multipliers, minimum fare, rounding, zero distance),
payment rules (decline and retry, double payment, ownership, idempotent completion), the RabbitMQ listener,
JWT verification, and `@WebMvcTest` tests that run the real security chain and error handler. No MongoDB or
RabbitMQ is needed.

### Demo Flow (Swagger UI)
1. Log in on the Account Service as a passenger; **Authorize** here with the token.
2. `POST /api/fares/estimate` — show the breakdown matches the rule.
3. Report a completed ride (via Ride Management, or `POST /api/payments/internal/ride-completed` with the API key).
4. `POST /api/payments/{rideId}/pay` with card `4111111111110000` → **402 declined** (negative scenario).
5. Retry with `CASH` → `PAID`; paying again → **409**.
6. `GET /api/receipts/{rideId}` → receipt with breakdown and fare rule.

Swagger UI is accessible at: `http://localhost:8084/swagger-ui.html`  
OpenAPI specification at: `http://localhost:8084/v3/api-docs`

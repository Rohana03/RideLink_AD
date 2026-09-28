# RideLink — Ride Management Service (Member 3)

**Owner:** Member 3  
**Port:** `8083`  
**Database:** `ride_db` (MongoDB, collection `rides`)  
**Package:** `lk.sliit.ridelink.ride`  

---

## 1. Overview & Responsibilities

1. **Ride requests** with pickup and destination (label + simulated coordinates).
2. **Driver assignment** using the Driver & Vehicle Service's eligible-driver search.
3. **Ride status lifecycle** with explicit transition rules.
4. **Acceptance, start, completion and cancellation** by the right party.
5. **Ride retrieval** for the passenger, the assigned driver and admins.

It is the only service that calls others, so it is where the two interservice interactions of the brief happen.

---

## 2. Ride Lifecycle

```
REQUESTED ──assign──▶ ASSIGNED ──accept──▶ ACCEPTED ──start──▶ IN_PROGRESS ──complete──▶ COMPLETED
    │                    │                     │
    └──────cancel────────┴────────cancel───────┴──▶ CANCELLED
```

Any other change (e.g. completing a REQUESTED ride, or cancelling an IN_PROGRESS one) returns **409**.

---

## 3. Interservice Communication

| When | Calls | Style | Why |
|---|---|---|---|
| Ride requested | Fare & Payment `POST /api/fares/internal/estimate` | sync REST | The passenger sees the estimate in the response |
| Driver assigned | Driver & Vehicle `GET /api/drivers/internal/eligible`, then `PATCH /api/drivers/internal/{id}/status` → `ON_TRIP` | sync REST | Assignment cannot continue without knowing who is free |
| Ride completed / cancelled | Driver & Vehicle `PATCH …/status` → `AVAILABLE` | sync REST | Frees the driver for the next ride |
| Ride completed | Fare & Payment `POST /api/payments/internal/ride-completed` **or** RabbitMQ `RideCompletedEvent` | REST (default) / async | Payment set-up must not slow down or fail the driver's action |

All calls send `X-Internal-Api-Key` and use 2 s connect / 5 s read timeouts.

**Assignment rule (documented, simple):** ask Driver & Vehicle for `AVAILABLE`, `ACTIVE` drivers within
**5 km** of the pickup (same vehicle type if the passenger asked for one), nearest first, up to 5. Reserve the first
one by moving them to `ON_TRIP`; if Driver & Vehicle answers 409 (taken by another ride a moment ago), try the
next. If nobody can be reserved → **409 "No available driver within 5.0 km of the pickup location"**.

**Failure handling:**
- Fare & Payment down when **requesting** → **503**, nothing saved (no ride without an estimate).
- Driver & Vehicle down when **assigning** → **503**, ride stays `REQUESTED`.
- On **completion**, the trip has physically ended, so the ride is always marked `COMPLETED`. A failed driver
  release is logged; a failed fare hand-off leaves `fareRecorded: false` so it is visible.

With `MESSAGING_ENABLED=true` the completion is published to `ride.events.exchange` (routing key
`ride.completed`), consumed by Fare & Payment; `finalFare` is then read from Fare & Payment
(`GET /api/payments/{rideId}`).

---

## 4. API Endpoints (`Authorization: Bearer <JWT from Account Service>`)

| Method | Endpoint | Who | Result |
|---|---|---|---|
| `POST` | `/api/rides` | `PASSENGER` | `201` ride `REQUESTED` with `estimatedFare` |
| `PATCH` | `/api/rides/{id}/assign` | the ride's passenger, `ADMIN` | `200` `ASSIGNED` with `driverId`, `driverName` |
| `PATCH` | `/api/rides/{id}/accept` | the assigned driver, `ADMIN` | `200` `ACCEPTED` |
| `PATCH` | `/api/rides/{id}/start` | the assigned driver, `ADMIN` | `200` `IN_PROGRESS` |
| `PATCH` | `/api/rides/{id}/complete` | the assigned driver, `ADMIN` | `200` `COMPLETED` with `finalFare` |
| `PATCH` | `/api/rides/{id}/cancel` | passenger, assigned driver, `ADMIN` | `200` `CANCELLED` |
| `GET` | `/api/rides/{id}` | passenger, assigned driver, `ADMIN` | `200` |
| `GET` | `/api/rides/me` | any signed-in user | `200` own rides, newest first |
| `GET` | `/api/rides?passengerId=&driverId=&status=` | `ADMIN` | `200` |

Request a ride:
```json
{ "pickupLocation":      { "label": "Colombo Fort",  "latitude": 6.9344, "longitude": 79.8428 },
  "destinationLocation": { "label": "Bambalapitiya", "latitude": 6.8905, "longitude": 79.8565 },
  "vehicleType": "CAR" }
```
`vehicleType` is optional. The passenger and the fare are **not** accepted from the client.

Complete a ride (optional body, simulated measurements for the final fare):
```json
{ "actualDistanceKm": 5.4, "actualDurationMinutes": 14 }
```

### Error Responses

Body: `{timestamp, status, error, message, path}`.

| Status | When |
|---|---|
| `400` | Validation failure, malformed JSON, unknown enum value, negative measurements |
| `401` | Missing or invalid token |
| `403` | Wrong role, or not your ride |
| `404` | Ride not found |
| `409` | Invalid status transition, no available driver, concurrent update |
| `503` | Driver & Vehicle or Fare & Payment unavailable |

---

## 5. Running & Testing

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — (same as Account Service) |
| `INTERNAL_API_KEY` | yes | — (same as Driver & Vehicle and Fare & Payment) |
| `MONGODB_URI` | no | `mongodb://localhost:27017/ride_db` |
| `DRIVER_SERVICE_URL` / `FARE_SERVICE_URL` | no | `http://localhost:8082` / `http://localhost:8084` |
| `MESSAGING_ENABLED` | no | `false` |

Put local values in a git-ignored `src/main/resources/application-dev.yml` and run with the `dev` profile:
`mvn spring-boot:run -Dspring-boot.run.profiles=dev`

```bash
mvn clean verify
```
59 tests: the lifecycle state machine, nearest-driver assignment (taken drivers, no driver, outages), completion
and cancellation hand-offs, request contracts sent to Driver & Vehicle and Fare & Payment (mock HTTP server),
RabbitMQ publishing, JWT verification, and `@WebMvcTest` role/validation/error checks. No MongoDB needed.

Swagger UI: `http://localhost:8083/swagger-ui.html`

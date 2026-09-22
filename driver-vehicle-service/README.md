# RideLink — Driver & Vehicle Service (Member 2)

**Owner:** Member 2  
**Port:** `8082`  
**Database:** `driver_db` (MongoDB)  
**Package:** `lk.sliit.ridelink.driver`  

---

## 1. Overview & Responsibilities

The **Driver & Vehicle Service** manages the complete operational lifecycle of drivers and their associated vehicles within the RideLink microservices architecture:
1. **Driver Operational Profile (MongoDB Document):** Personal details, operational status (`ACTIVE`, `PENDING_APPROVAL`, `SUSPENDED`, `INACTIVE`), service area, rating, and completed trip counter.
2. **Vehicle Details (Embedded Document):** Vehicle specifications (`make`, `model`, `year`, `color`, `licensePlate`, `vehicleType`, `seatingCapacity`) embedded within each driver document.
3. **Availability Management:** Real-time availability toggling (`AVAILABLE`, `OFFLINE`, `ON_TRIP`, `BUSY`).
4. **Simulated Current Location:** Dynamic GPS coordinate updates (`latitude`, `longitude`, timestamp) enabling simulated vehicle movement.
5. **Eligible Available Driver Search (Haversine Formula):** Core matching engine called by Ride Management Service to retrieve nearby available drivers ranked by distance from the passenger's pickup location.

---

## 2. API Endpoints Reference

### 2.1 Driver-Facing Endpoints (`Authorization: Bearer <JWT>`)

| Method | Endpoint | Description | Role | Status Code |
|---|---|---|---|---|
| `POST` | `/api/drivers` | Register driver profile & vehicle | `DRIVER` | `201 Created` |
| `GET` | `/api/drivers/me` | Fetch authenticated driver profile & vehicle | `DRIVER` | `200 OK` |
| `PUT` | `/api/drivers/me` | Update driver profile info | `DRIVER` | `200 OK` |
| `PUT` | `/api/drivers/me/vehicle` | Update vehicle specifications | `DRIVER` | `200 OK` |
| `PATCH` | `/api/drivers/me/availability` | Toggle status (`AVAILABLE` / `OFFLINE`) | `DRIVER` | `200 OK` |
| `PUT` | `/api/drivers/me/location` | Update simulated GPS location (`latitude`, `longitude`) | `DRIVER` | `200 OK` |
| `GET` | `/api/drivers/{id}` | Get driver details by driver ID | any signed-in user | `200 OK` |

### 2.2 Interservice Endpoints (`X-Internal-Api-Key: <key>`)

| Method | Endpoint | Description | Consumed By |
|---|---|---|---|
| `GET` | `/api/drivers/internal/eligible` | Find available drivers near pickup coordinates | Ride Management Service |
| `GET` | `/api/drivers/internal/{id}` | Fetch driver details by ID | Ride Management Service |
| `PATCH` | `/api/drivers/internal/{id}/status` | Update driver status (e.g. `ON_TRIP`, `AVAILABLE`) | Ride Management Service |
| `PUT` | `/api/drivers/internal/{id}/location` | Simulate location update directly by ID | Automated tests / simulation |

### 2.3 Error Responses

All errors (including security rejections) use the same body: `{timestamp, status, error, message, path}`.

| Status | When |
|---|---|
| `400 Bad Request` | Validation failure, or a status the caller is not allowed to set |
| `401 Unauthorized` | Missing/invalid JWT, or missing/invalid `X-Internal-Api-Key` |
| `403 Forbidden` | Valid JWT without the `DRIVER` role (e.g. a passenger calling `/api/drivers/me`) |
| `404 Not Found` | Driver profile or driver ID does not exist |
| `409 Conflict` | Duplicate user/licence/plate, invalid status transition, or a concurrent update |

---

## 3. Availability Status Rules

`ON_TRIP` is controlled only by Ride Management Service; drivers only go on or off duty.

| Caller | From | To | Result |
|---|---|---|---|
| Driver (`/me/availability`) | `OFFLINE` / `AVAILABLE` | `AVAILABLE` / `OFFLINE` | ✅ (going `AVAILABLE` requires `operationalStatus = ACTIVE`) |
| Driver | `ON_TRIP` | anything | ❌ `409` — finish or cancel the ride first |
| Driver | any | `ON_TRIP` / `BUSY` | ❌ `400` |
| Ride Management (`/internal/{id}/status`) | `AVAILABLE` | `ON_TRIP` | ✅ ride assigned |
| Ride Management | `ON_TRIP` / `OFFLINE` / `BUSY` | `ON_TRIP` | ❌ `409` — driver is not available, so one driver never gets two rides |
| Ride Management | `ON_TRIP` (or already `AVAILABLE`) | `AVAILABLE` | ✅ ride completed or cancelled |
| Ride Management | any | `OFFLINE` / `BUSY` | ❌ `400` |

Each driver document carries a `@Version` field (optimistic locking): if two requests update the same driver at once, the second save fails with `409` instead of silently overwriting the first.

Licence plates are stored upper-case and protected by a unique index on `vehicle.licensePlate`.

---

## 4. Eligible Driver Matching Algorithm

The matching engine in `DriverServiceImpl.findEligibleDrivers(...)`:
1. Queries all drivers with `availabilityStatus = AVAILABLE` and `operationalStatus = ACTIVE` having non-null GPS coordinates.
2. Filters by `vehicleType` if specified in the query parameters (e.g. `CAR`, `TUK_TUK`, `VAN`).
3. Computes the great-circle distance between `(pickupLatitude, pickupLongitude)` and `(driverLatitude, driverLongitude)` using the **Haversine Formula**:
   $$\Delta\sigma = 2 \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)}\right)$$
   $$d = R \cdot \Delta\sigma \quad (R = 6371.0\text{ km})$$
4. Filters candidates where `distance <= radiusKm` (default: 5.0 km).
5. Sorts the eligible candidates in **ascending order of distance** (closest driver first).
6. Limits results according to the `limit` parameter (default: 5).

---

## 5. Security Architecture

- **JWT Authentication:** Every user request to `/api/drivers/**` must include an `Authorization: Bearer <token>` header signed with the shared `JWT_SECRET` (HS256). The token is verified statelessly by `JwtAuthenticationFilter` using `JwtUtil`.
- **Role-based authorisation:** `SecurityConfig` requires the `DRIVER` role for `POST /api/drivers` and `/api/drivers/me/**`; `GET /api/drivers/{id}` is open to any signed-in user (e.g. a passenger viewing their assigned driver).
- **Internal API Key:** Interservice requests to `/api/drivers/internal/**` are intercepted by `InternalApiKeyFilter`, requiring the `X-Internal-Api-Key` header matching `ridelink.internal.api-key`.

---

## 6. Running & Testing

### Configuration

Secrets have **no default values** in `application.yml`; the service will not start without them.

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — (must match Account Service) |
| `INTERNAL_API_KEY` | yes | — (must match Ride Management Service) |
| `MONGODB_URI` | no | `mongodb://localhost:27017/driver_db` |
| `JWT_EXPIRATION_MS` | no | `3600000` |

Either export them as environment variables, or put them in a local
`src/main/resources/application-dev.yml` (git-ignored, never commit it) and run with the `dev` profile.

### Running MongoDB with Docker
```bash
docker run -d --name ridelink-mongo-driver -p 27017:27017 -v ridelink_driver_db:/data/db mongo:7.0
```

### Running the Service (via IDE or Maven)
- **IDE:** Run `DriverVehicleServiceApplication.java` with the env vars above (or active profile `dev`)
- **Maven:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

### Tests
```bash
mvn clean verify
```
Unit tests use Mockito and `@WebMvcTest` with the `test` profile, so no MongoDB is needed.

Swagger UI is accessible at: `http://localhost:8082/swagger-ui.html`  
OpenAPI specification at: `http://localhost:8082/v3/api-docs`

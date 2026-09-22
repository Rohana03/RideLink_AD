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

| Method | Endpoint | Description | Status Code |
|---|---|---|---|
| `POST` | `/api/drivers` | Register driver profile & vehicle | `201 Created` |
| `GET` | `/api/drivers/me` | Fetch authenticated driver profile & vehicle | `200 OK` |
| `PUT` | `/api/drivers/me` | Update driver profile info | `200 OK` |
| `PUT` | `/api/drivers/me/vehicle` | Update vehicle specifications | `200 OK` |
| `PATCH` | `/api/drivers/me/availability` | Toggle status (`AVAILABLE` / `OFFLINE`) | `200 OK` |
| `PUT` | `/api/drivers/me/location` | Update simulated GPS location (`latitude`, `longitude`) | `200 OK` |
| `GET` | `/api/drivers/{id}` | Get public driver details by driver ID | `200 OK` |

### 2.2 Interservice Endpoints (`X-Internal-Api-Key: <key>`)

| Method | Endpoint | Description | Consumed By |
|---|---|---|---|
| `GET` | `/api/drivers/internal/eligible` | Find available drivers near pickup coordinates | Ride Management Service |
| `GET` | `/api/drivers/internal/{id}` | Fetch driver details by ID | Ride Management Service |
| `PATCH` | `/api/drivers/internal/{id}/status` | Update driver status (e.g. `ON_TRIP`, `AVAILABLE`) | Ride Management Service |
| `PUT` | `/api/drivers/internal/{id}/location` | Simulate location update directly by ID | Automated tests / simulation |

---

## 3. Eligible Driver Matching Algorithm

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

## 4. Security Architecture

- **JWT Authentication:** Every user request to `/api/drivers/**` must include an `Authorization: Bearer <token>` header signed with the shared `JWT_SECRET` (HS256). The token is verified statelessly by `JwtAuthenticationFilter` using `JwtUtil`.
- **Internal API Key:** Interservice requests to `/api/drivers/internal/**` are intercepted by `InternalApiKeyFilter`, requiring the `X-Internal-Api-Key` header matching `ridelink.internal.api-key`.

---

## 5. Running & Testing

### Running MongoDB with Docker
```bash
docker run -d --name mongodb-driver -p 27017:27017 mongo:latest
```

### Running the Service (via IDE or Maven)
- **IDE:** Run `DriverVehicleServiceApplication.java`
- **Maven:** `mvn spring-boot:run`

Swagger UI is accessible at: `http://localhost:8082/swagger-ui.html`  
OpenAPI specification at: `http://localhost:8082/v3/api-docs`

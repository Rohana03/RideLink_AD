# RideLink Sequence Diagram — Full Ride Lifecycle

Paste the diagram into [mermaid.live](https://mermaid.live) to render and export it for the report.
The Postman collection `postman/RideLink.postman_collection.json` runs this exact flow.

```mermaid
sequenceDiagram
    actor Passenger
    actor Driver
    participant ACC as Account
    participant DRV as Driver & Vehicle
    participant RIDE as Ride Management
    participant FARE as Fare & Payment

    Passenger->>ACC: POST /api/auth/register (PASSENGER), POST /api/auth/login
    ACC-->>Passenger: JWT {userId, roles:[PASSENGER]}
    Driver->>ACC: POST /api/auth/register (DRIVER), POST /api/auth/login
    ACC-->>Driver: JWT {userId, roles:[DRIVER]}

    Driver->>DRV: POST /api/drivers (profile + vehicle)
    Driver->>DRV: PUT /api/drivers/me/location
    Driver->>DRV: PATCH /api/drivers/me/availability (AVAILABLE)

    Passenger->>RIDE: POST /api/rides (pickup, destination)
    RIDE->>FARE: POST /api/fares/internal/estimate (sync REST)
    FARE-->>RIDE: distanceKm, totalFare
    RIDE-->>Passenger: 201 REQUESTED + estimatedFare

    Passenger->>RIDE: PATCH /api/rides/{id}/assign
    RIDE->>DRV: GET /api/drivers/internal/eligible (sync REST)
    alt no driver within 5 km
        DRV-->>RIDE: []
        RIDE-->>Passenger: 409 No available driver
    else nearest driver found
        DRV-->>RIDE: [nearest AVAILABLE drivers]
        RIDE->>DRV: PATCH /api/drivers/internal/{id}/status ON_TRIP
        DRV-->>RIDE: 200 (409 if just taken, then the next one is tried)
        RIDE-->>Passenger: 200 ASSIGNED
    end

    Driver->>RIDE: PATCH /api/rides/{id}/accept
    RIDE-->>Driver: ACCEPTED
    Driver->>RIDE: PATCH /api/rides/{id}/start
    RIDE-->>Driver: IN_PROGRESS
    Driver->>RIDE: PATCH /api/rides/{id}/complete (actual km, min)
    RIDE->>DRV: PATCH /api/drivers/internal/{id}/status AVAILABLE
    RIDE->>FARE: RideCompletedEvent (REST, or RabbitMQ when enabled)
    FARE->>FARE: final fare = max(200, (100 + 60 km + 10 min) x multiplier), payment PENDING
    FARE-->>RIDE: totalAmount (REST mode)
    RIDE-->>Driver: 200 COMPLETED + finalFare

    Passenger->>FARE: POST /api/payments/{rideId}/pay (simulated)
    alt card ending 0000
        FARE-->>Passenger: 402 declined (payment FAILED, can retry)
    else approved
        FARE-->>Passenger: 200 PAID + receiptNumber
    end
    Passenger->>FARE: GET /api/receipts/{rideId}
    FARE-->>Passenger: receipt with fare breakdown
```

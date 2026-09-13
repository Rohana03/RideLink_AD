# RideLink Architecture Diagram

Paste this into [mermaid.live](https://mermaid.live) to render and export as
a PNG/SVG for the technical report.

```mermaid
flowchart TB
    subgraph Client["Swagger UI / Postman (official client)"]
    end

    Client --> ACC[Account Service<br/>port 8081<br/>DB: account_db]
    Client --> DRV[Driver & Vehicle Service<br/>port 8082<br/>DB: driver_db]
    Client --> RIDE[Ride Management Service<br/>port 8083<br/>DB: ride_db]
    Client --> FARE[Fare & Payment Service<br/>port 8084<br/>DB: fare_payment_db]

    RIDE -- "sync REST: GET eligible drivers" --> DRV
    RIDE -- "sync REST: POST fare estimate" --> FARE
    RIDE -- "async: RideCompletedEvent" --> MQ[(RabbitMQ<br/>ride.events.exchange)]
    MQ -- "async: consumed by" --> FARE

    ACC -.->|"JWT verified locally<br/>(shared secret, no network call)"| DRV
    ACC -.->|"JWT verified locally"| RIDE
    ACC -.->|"JWT verified locally"| FARE

    ACC --- ACCDB[(account_db)]
    DRV --- DRVDB[(driver_db)]
    RIDE --- RIDEDB[(ride_db)]
    FARE --- FAREDB[(fare_payment_db)]

    style ACCDB fill:#eee
    style DRVDB fill:#eee
    style RIDEDB fill:#eee
    style FAREDB fill:#eee
```

**Key points to reference in the report:**
- Each service owns its own database; there are no cross-service joins or shared schemas.
- Authentication is stateless: the Account Service issues a JWT, and every other service verifies it locally using a shared secret rather than calling Account Service on every request.
- Two distinct interservice communication styles are used, each justified by its context: synchronous REST for calls that need an immediate answer (driver matching, fare estimate), and asynchronous messaging for the ride-completion → payment handoff, which is not on the critical path of the driver's action.

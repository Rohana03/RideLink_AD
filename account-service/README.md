# RideLink — Account Service (Member 1)

**Owner:** Member 1  
**Port:** `8081`  
**Database:** `account_db` (MongoDB, collection `accounts`)  
**Package:** `lk.sliit.ridelink.account`  

---

## 1. Overview & Responsibilities

The **Account Service** is the single source of identity for RideLink:
1. **Registration** of passenger and driver accounts (`PASSENGER`, `DRIVER`). Passwords are stored as BCrypt hashes only.
2. **Login and token issuance:** returns an HS256 JWT that every other service verifies locally with the shared `JWT_SECRET`, with no call back to this service.
3. **Role management:** an `ADMIN` can change an account's role.
4. **Profile viewing and updating:** every signed-in user manages their own name, phone number and password.
5. **Account status management:** an `ADMIN` can set `ACTIVE`, `SUSPENDED` or `DEACTIVATED`; only `ACTIVE` accounts can log in.

The account `id` is the stable identifier other services store as `userId` (e.g. `Driver.userId` in the Driver & Vehicle Service).

---

## 2. API Endpoints Reference

### 2.1 Public Endpoints

| Method | Endpoint | Description | Status Code |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a `PASSENGER` or `DRIVER` account | `201 Created` |
| `POST` | `/api/auth/login` | Log in; returns `{accessToken, tokenType, expiresIn, account}` | `200 OK` |

### 2.2 Signed-in User Endpoints (`Authorization: Bearer <JWT>`)

| Method | Endpoint | Description | Role | Status Code |
|---|---|---|---|---|
| `GET` | `/api/accounts/me` | View own account | any | `200 OK` |
| `PUT` | `/api/accounts/me` | Update own `fullName` and `phoneNumber` | any | `200 OK` |
| `PUT` | `/api/accounts/me/password` | Change own password (needs `currentPassword`) | any | `204 No Content` |

### 2.3 Administration Endpoints (`Authorization: Bearer <JWT>`)

| Method | Endpoint | Description | Role | Status Code |
|---|---|---|---|---|
| `GET` | `/api/accounts?role=&status=` | List accounts, optionally filtered | `ADMIN` | `200 OK` |
| `GET` | `/api/accounts/{id}` | View any account | `ADMIN` | `200 OK` |
| `PATCH` | `/api/accounts/{id}/status` | Set `ACTIVE` / `SUSPENDED` / `DEACTIVATED` | `ADMIN` | `200 OK` |
| `PATCH` | `/api/accounts/{id}/role` | Set `PASSENGER` / `DRIVER` / `ADMIN` | `ADMIN` | `200 OK` |

### 2.4 Interservice Endpoints (`X-Internal-Api-Key: <key>`)

| Method | Endpoint | Description | Consumed By |
|---|---|---|---|
| `GET` | `/api/accounts/internal/{id}` | Confirm a `userId` exists and check its status | Ride Management / Driver & Vehicle |

### 2.5 Error Responses

All errors (including security rejections) use the same body as the other services: `{timestamp, status, error, message, path}`.

| Status | When |
|---|---|
| `400 Bad Request` | Validation failure, unknown enum value, self-registering as `ADMIN`, wrong current password, an admin changing their own role/status |
| `401 Unauthorized` | Wrong email or password (one generic message for both), missing/invalid JWT, missing/invalid `X-Internal-Api-Key` |
| `403 Forbidden` | Account is `SUSPENDED`/`DEACTIVATED`, or the JWT lacks the `ADMIN` role for an admin endpoint |
| `404 Not Found` | Account ID does not exist |
| `409 Conflict` | Email already registered (case-insensitive), or a concurrent update |

---

## 3. Business Rules

- **Emails** are trimmed and stored lower-case, with a unique index, so `Nimal@Example.com` and `nimal@example.com` are the same account.
- **Passwords** must be 8–72 characters with at least one letter and one digit (72 is the BCrypt input limit).
- **`ADMIN` cannot self-register.** The first admin is created at start-up from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (see `AdminAccountInitializer`); later admins are promoted with `PATCH /api/accounts/{id}/role`.
- **Admins cannot change their own role or status**, so the last admin cannot lock everyone out.
- **Login order:** the password is checked before the status, so a wrong password on a suspended account still returns `401` and the status is not revealed.
- **Optimistic locking:** each account carries a `@Version`; a concurrent update gets `409` instead of silently overwriting.

---

## 4. JWT Contract (shared with every service)

| Item | Value |
|---|---|
| Algorithm | HS256, key = `JWT_SECRET` (**at least 32 bytes**; the service refuses to start otherwise) |
| `sub`, `userId` | Account ID |
| `roles` | JSON array with the account's role, e.g. `["DRIVER"]` — no `ROLE_` prefix |
| `email` | Account email |
| `iss` | `ridelink-account-service` |
| `exp` | now + `JWT_EXPIRATION_MS` (default 1 hour) |
| Header | `Authorization: Bearer <token>` |

Tokens are stateless: a role or status change takes effect at the user's next login. Suspending an account blocks new logins immediately, but a token already issued stays valid until it expires.

---

## 5. Running & Testing

### Configuration

Secrets have **no default values** in `application.yml`; the service will not start without them.

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — (must match every other service) |
| `INTERNAL_API_KEY` | yes | — (must match the calling services) |
| `MONGODB_URI` | no | `mongodb://localhost:27017/account_db` |
| `JWT_EXPIRATION_MS` | no | `3600000` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | no | unset (no bootstrap admin) |

Either export them as environment variables, or put them in a local
`src/main/resources/application-dev.yml` (git-ignored, never commit it) and run with the `dev` profile:

```yaml
ridelink:
  jwt:
    secret: <same value as the other services>
  internal:
    api-key: <same value as the other services>
  admin:
    email: admin@ridelink.lk
    password: <choose one>
```

### Running MongoDB with Docker
```bash
docker run -d --name ridelink-mongo-account -p 27017:27017 -v ridelink_account_db:/data/db mongo:7.0
```
One local MongoDB server can host both `account_db` and `driver_db`; they are still separate databases.

### Running the Service
- **IDE:** Run `AccountServiceApplication.java` with the env vars above (or active profile `dev`)
- **Maven:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

### Tests
```bash
mvn clean verify
```
59 tests: Mockito unit tests for the service rules, JWT contract tests, and `@WebMvcTest` tests that run the real
security filter chain and error handler with the `test` profile, so no MongoDB is needed.

### Demo Flow (Swagger UI)
1. `POST /api/auth/register` a passenger and a driver.
2. `POST /api/auth/login` as the driver, copy `accessToken`, click **Authorize** and paste it.
3. Use the same token on the Driver & Vehicle Service (`POST /api/drivers`) — it is accepted without calling this service.
4. Log in as the bootstrap admin, suspend the passenger, and show that their login now returns `403`.

Swagger UI is accessible at: `http://localhost:8081/swagger-ui.html`  
OpenAPI specification at: `http://localhost:8081/v3/api-docs`

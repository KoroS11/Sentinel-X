# SentinelX Backend Documentation

## Section 1 - Project Overview

SentinelX is a backend system for insider threat monitoring. It tracks user activity, evaluates behavioral risk, and creates actionable alerts so security teams can detect abnormal behavior early.

### Technology Stack

- Java 17
- Spring Boot
- Spring Security
- JWT
- Flyway
- PostgreSQL
- Maven

### High-Level Architecture

SentinelX is a REST API backend with role-based access control and JWT authentication. The codebase is organized into modular domains (auth, users, activity, risk, alerts, dashboard, common) and each module owns its controller, service, repository, DTO, and entity responsibilities.

## Section 2 - Getting Started

### Prerequisites

- Java 17
- Maven
- PostgreSQL 15 or newer
- Docker (optional but recommended for local database)

### Local Setup Steps

1. Clone the repository.

~~~bash
git clone https://github.com/KoroS11/Sentinel-X.git
cd Sentinel-X
~~~

2. Copy secrets.local.example.properties to secrets.local.properties and fill all keys.

~~~bash
cd backend
copy secrets.local.example.properties secrets.local.properties
~~~

Required keys in secrets.local.properties:

- DB_URL: JDBC URL used by Spring datasource in dev and test profiles. Example: jdbc:postgresql://localhost:5432/sentinelx
- DB_USERNAME: database username used for datasource authentication.
- DB_PASSWORD: database password used for datasource authentication.
- SERVER_PORT: backend port. Set to 8081 to match the frontend integration guide in this document.
- JWT_SECRET: HMAC signing secret for access tokens. Must be at least 32 characters.
- JWT_EXPIRATION_MS: access token lifetime in milliseconds.
- JWT_REFRESH_EXPIRATION_MS: refresh token lifetime in milliseconds.

Example complete file:

~~~properties
DB_URL=jdbc:postgresql://localhost:5432/sentinelx
DB_USERNAME=sentinelx
DB_PASSWORD=sentinelx
SERVER_PORT=8081
JWT_SECRET=sentinelx_local_dev_secret_key_minimum_32_chars
JWT_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000
~~~

3. Start PostgreSQL.

Exact local Docker command used for this project setup:

~~~bash
docker run --name sentinelx-postgres -e POSTGRES_DB=sentinelx -e POSTGRES_USER=sentinelx -e POSTGRES_PASSWORD=sentinelx -p 5432:5432 -d postgres:15
~~~

4. Run the backend with dev profile.

~~~bash
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
~~~

5. Verify startup with health endpoint.

~~~bash
curl http://localhost:8081/health
~~~

Expected response when app and DB are healthy:

~~~json
{
  "status": "UP",
  "application": "UP",
  "database": "CONNECTED",
  "timestamp": "2026-04-05T12:00:00Z"
}
~~~

If DB is unavailable, status code is 503 and response status is DEGRADED.

## Section 3 - Environment and Configuration

### Profile System

application.properties is the entrypoint configuration. It sets defaults and activates profile from SPRING_PROFILES_ACTIVE (default dev).

application-dev.properties contains local development datasource and JWT bindings. It imports secrets.local.properties using optional import.

application-test.properties contains test datasource and JPA validation settings. Tests provide isolated datasource values at runtime.

application-prod.properties contains production datasource and JPA settings; values are resolved from environment variables.

### Externalized Configuration Keys

| Key | Purpose | Profile(s) | Required |
|---|---|---|---|
| SPRING_PROFILES_ACTIVE | Selects active Spring profile (default dev) | Global | Optional |
| SERVER_PORT | HTTP server port | Global | Optional (default 8080) |
| DB_URL | JDBC datasource URL | dev, test, prod | Required |
| DB_USERNAME | JDBC datasource username | dev, test, prod | Required |
| DB_PASSWORD | JDBC datasource password | dev, test, prod | Required |
| JWT_SECRET | JWT signing secret (minimum 32 chars) | dev, test, prod | Required |
| JWT_EXPIRATION_MS | Access token expiration in milliseconds | dev, test, prod | Required |
| JWT_REFRESH_EXPIRATION_MS | Refresh token expiration in milliseconds | dev, test, prod | Required |

### secrets.local.properties

secrets.local.properties is a local developer secrets file loaded only in dev through spring.config.import=optional:file:./secrets.local.properties.

It is gitignored because it contains machine-specific credentials and secrets.

It should contain these keys: DB_URL, DB_USERNAME, DB_PASSWORD, SERVER_PORT, JWT_SECRET, JWT_EXPIRATION_MS, JWT_REFRESH_EXPIRATION_MS.

## Section 4 - Database

### Schema Overview by Migration (V1 to V9)

| Migration | Table/Change | Purpose | Key Columns | Foreign Keys |
|---|---|---|---|---|
| V1__init.sql | roles | Role catalog | id, name | none |
| V1__init.sql | users | User accounts and identity | id, username, email, password_hash, is_active, role_id, created_at, updated_at | users.role_id -> roles.id |
| V2__add_refresh_tokens.sql | refresh_tokens | Refresh token storage and revocation | id, token, user_id, expiry_date, revoked, created_at | refresh_tokens.user_id -> users.id |
| V3__add_password_reset_tokens.sql | password_reset_tokens | Password reset flow tokens | id, token, user_id, expiry_date, used, created_at | password_reset_tokens.user_id -> users.id |
| V4__add_email_verification.sql | users alter | Adds email_verified flag | email_verified | n/a |
| V4__add_email_verification.sql | email_verification_tokens | Email verification flow tokens | id, token, user_id, expiry_date, used | email_verification_tokens.user_id -> users.id |
| V5__add_activity_table.sql | activities | Audit activity stream | id, user_id, action, entity_type, entity_id, metadata, created_at | activities.user_id -> users.id |
| V6__add_risk_scores_table.sql | risk_scores | Historical risk scoring records | id, user_id, score, reason, calculated_at | risk_scores.user_id -> users.id |
| V7__add_alerts_table.sql | alerts | Alert lifecycle tracking | id, user_id, risk_score_id, severity, message, status, created_at, updated_at | alerts.user_id -> users.id; alerts.risk_score_id -> risk_scores.id |
| V8__add_user_status.sql | users alter | Adds account status | status | n/a |
| V9__add_alert_assignee.sql | alerts alter | Adds analyst assignee relation | assigned_to_user_id, updated_at | alerts.assigned_to_user_id -> users.id |

### Entity Relationship Diagram (Plain Text)

~~~text
roles (id, name)
   users (role_id FK)

users (id, username, email, password_hash, is_active, email_verified, status, created_at, updated_at)
   refresh_tokens (user_id FK)
   password_reset_tokens (user_id FK)
   email_verification_tokens (user_id FK)
   activities (user_id FK)
   risk_scores (user_id FK)
   alerts (user_id FK)
         assigned_to_user_id FK -> users.id
         risk_score_id FK -> risk_scores.id
~~~

### Migration Strategy

Flyway is the authoritative mechanism for all schema changes.

Migration naming convention is V{number}__{description}.sql.

JPA is configured with spring.jpa.hibernate.ddl-auto=validate, so Hibernate validates schema compatibility and never applies schema mutations.

Never modify an already-applied migration file. Create a new versioned migration for every schema change.

### Seeded Data

SeedDataRunner calls RoleService.ensureDefaultRoles() on startup.

Seeded roles are:

- ADMIN
- ANALYST
- EMPLOYEE

Seeding is idempotent because roles are created only if missing.

## Section 5 - API Reference

Common rules:

- Base API path uses /api for business modules.
- Protected endpoints require Authorization header: Bearer access token.
- Content-Type for request bodies is application/json.

Common error shape from GlobalExceptionHandler:

~~~json
{
  "timestamp": "2026-04-05T12:00:00Z",
  "status": 400,
  "error": "Validation failed"
}
~~~

### Auth (/api/auth/*)

#### POST /api/auth/register

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Request body:

~~~json
{
  "username": "string, required, not blank",
  "email": "string, required, valid email",
  "password": "string, required, not blank"
}
~~~

Success response (200):

~~~json
{
  "token": "jwt-access-token",
  "username": "alice",
  "refreshToken": null
}
~~~

Error responses:

- 400 validation failure
- 409 email already registered
- 500 unexpected server error

#### POST /api/auth/login

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Request body:

~~~json
{
  "email": "string, required, valid email",
  "password": "string, required, not blank"
}
~~~

Success response (200):

~~~json
{
  "token": "jwt-access-token",
  "username": "alice",
  "refreshToken": "uuid-refresh-token"
}
~~~

Error responses:

- 400 validation failure
- 401 invalid credentials
- 500 unexpected server error

#### POST /api/auth/refresh

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Request body:

~~~json
{
  "refreshToken": "string, required, not blank"
}
~~~

Success response (200):

~~~json
{
  "token": "new-jwt-access-token",
  "username": "alice",
  "refreshToken": "new-uuid-refresh-token"
}
~~~

Error responses:

- 400 validation failure
- 401 refresh token missing, revoked, expired, or unknown
- 500 unexpected server error

#### POST /api/auth/logout

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (EMPLOYEE, ANALYST, ADMIN) |

Request body: none.

Success response (200): empty body.

Error responses:

- 401 missing or invalid authentication
- 500 unexpected server error

#### POST /api/auth/forgot-password

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Request body:

~~~json
{
  "email": "string, required, valid email"
}
~~~

Success response (200): empty body.

Error responses:

- 400 validation failure
- 500 unexpected server error

Notes: unknown email is handled silently and still returns 200.

#### POST /api/auth/reset-password

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Request body:

~~~json
{
  "token": "string, required, not blank",
  "newPassword": "string, required, min length 8"
}
~~~

Success response (200): empty body.

Error responses:

- 400 validation failure
- 401 invalid, expired, or already used reset token
- 500 unexpected server error

#### GET /api/auth/verify-email

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Query params:

- token: required

Success response (200): empty body.

Error responses:

- 401 invalid, expired, or already used verification token
- 500 unexpected server error

### Users (/api/users/*)

#### GET /api/users

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Request body: none.

Success response (200): paginated UserResponse.

~~~json
{
  "content": [
    {
      "id": 1,
      "username": "alice",
      "email": "alice@example.com",
      "emailVerified": false,
      "status": "ACTIVE",
      "roles": ["ADMIN"],
      "createdAt": "2026-04-05T12:00:00"
    }
  ],
  "pageable": {},
  "last": true,
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0,
  "sort": {},
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
~~~

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

#### GET /api/users/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN or own profile |

Request body: none.

Success response (200): UserResponse.

Error responses:

- 401 unauthenticated
- 403 not own profile and not admin
- 404 user not found
- 500 unexpected server error

#### POST /api/users

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Request body:

~~~json
{
  "username": "string, required, not blank",
  "email": "string, required, valid email",
  "password": "string, required, min length 8",
  "role": "string, required, one of ADMIN ANALYST EMPLOYEE"
}
~~~

Success response (201): UserResponse.

Error responses:

- 400 validation failure
- 403 insufficient role
- 404 role not found
- 409 email already registered
- 500 unexpected server error

#### PUT /api/users/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN or own profile |

Request body:

~~~json
{
  "username": "string, optional",
  "email": "string, optional, valid email when present"
}
~~~

Success response (200): UserResponse.

Error responses:

- 400 validation failure
- 403 not own profile and not admin
- 404 user not found
- 409 email already registered
- 500 unexpected server error

#### DELETE /api/users/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Request body: none.

Success response (204): empty body.

Error responses:

- 403 insufficient role
- 404 user not found
- 409 deleting admin user is blocked
- 500 unexpected server error

#### PATCH /api/users/{id}/status

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Request body:

~~~json
{
  "status": "ACTIVE | INACTIVE | SUSPENDED"
}
~~~

Success response (200): UserResponse.

Error responses:

- 400 validation failure
- 403 insufficient role
- 404 user not found
- 500 unexpected server error

### Activity (/api/activities/*)

#### GET /api/activities/me

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Query params:

- page (optional, default 0)
- size (optional, default Spring pageable)
- sort (optional)

Success response (200): paginated ActivityResponse.

~~~json
{
  "content": [
    {
      "id": 10,
      "userId": 1,
      "action": "LOGIN_SUCCESS",
      "entityType": "AUTH",
      "entityId": "1",
      "metadata": "{\"ip\":\"127.0.0.1\"}",
      "createdAt": "2026-04-05T12:00:00"
    }
  ],
  "pageable": {},
  "last": true,
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0,
  "sort": {},
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
~~~

Error responses:

- 401 unauthenticated
- 500 unexpected server error

#### GET /api/activities?userId=&page=&size=

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Query params:

- userId (required)
- page (optional)
- size (optional)

Success response (200): paginated ActivityResponse.

Error responses:

- 400 missing userId or type mismatch
- 401 unauthenticated
- 403 insufficient role
- 404 user not found
- 500 unexpected server error

#### GET /api/activities/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (employees limited to own activity) |

Request body: none.

Success response (200): ActivityResponse.

Error responses:

- 401 unauthenticated
- 403 employee accessing another user activity
- 404 activity not found
- 500 unexpected server error

#### GET /api/activities/entity/{entityType}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Query params:

- page (optional)
- size (optional)

Success response (200): paginated ActivityResponse.

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

### Risk (/api/risk/*)

#### GET /api/risk/me

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Success response (200): RiskScoreResponse.

~~~json
{
  "id": 77,
  "userId": 1,
  "score": 64,
  "reason": "Elevated risk due to frequent and off-hours activity.",
  "calculatedAt": "2026-04-05T12:00:00"
}
~~~

Error responses:

- 401 unauthenticated
- 500 unexpected server error

#### GET /api/risk/{userId}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN, or EMPLOYEE when userId equals own id |

Success response (200): RiskScoreResponse.

Error responses:

- 400 type mismatch on userId
- 401 unauthenticated
- 403 employee accessing another user
- 404 user not found
- 500 unexpected server error

#### GET /api/risk/{userId}/history

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN, or EMPLOYEE when userId equals own id |

Success response (200): paginated RiskScoreResponse.

Error responses:

- 400 type mismatch on userId
- 401 unauthenticated
- 403 employee accessing another user
- 404 user not found
- 500 unexpected server error

#### GET /api/risk/history/me

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Success response (200): paginated RiskScoreResponse.

Error responses:

- 401 unauthenticated
- 500 unexpected server error

Compatibility endpoint that also exists:

- GET /api/risk/user/{userId} (ANALYST or ADMIN only)

### Alerts (/api/alerts/*)

#### GET /api/alerts/me

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Query params:

- status (optional: OPEN, UNDER_INVESTIGATION, ACKNOWLEDGED, RESOLVED)
- page, size, sort (optional)

Success response (200): paginated AlertResponse.

~~~json
{
  "content": [
    {
      "id": 15,
      "userId": 1,
      "riskScoreId": 77,
      "severity": "HIGH",
      "message": "Risk score 64 detected for user alice.",
      "status": "OPEN",
      "createdAt": "2026-04-05T12:00:00",
      "updatedAt": "2026-04-05T12:00:00"
    }
  ],
  "pageable": {},
  "last": true,
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0,
  "sort": {},
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
~~~

Error responses:

- 400 invalid status enum value
- 401 unauthenticated
- 500 unexpected server error

#### GET /api/alerts

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Query params:

- status (optional)
- page, size, sort (optional)

Success response (200): paginated AlertResponse.

Error responses:

- 400 invalid status enum value
- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

#### GET /api/alerts/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (employees limited to own alert) |

Success response (200): AlertResponse.

Error responses:

- 401 unauthenticated
- 403 employee accessing another user alert
- 404 alert not found
- 500 unexpected server error

#### PATCH /api/alerts/{id}/acknowledge

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (employees limited to own alert) |

Request body: none.

Success response (200): AlertResponse with status ACKNOWLEDGED.

Error responses:

- 401 unauthenticated
- 403 employee modifying another user alert
- 404 alert not found
- 409 illegal status transition
- 500 unexpected server error

#### PATCH /api/alerts/{id}/resolve

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (employees limited to own alert) |

Request body: none.

Success response (200): AlertResponse with status RESOLVED.

Error responses:

- 401 unauthenticated
- 403 employee modifying another user alert
- 404 alert not found
- 409 illegal status transition
- 500 unexpected server error

#### PATCH /api/alerts/{id}/status

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated (employees limited to own alert) |

Request body:

~~~json
{
  "status": "OPEN | UNDER_INVESTIGATION | ACKNOWLEDGED | RESOLVED"
}
~~~

Success response (200): AlertResponse.

Error responses:

- 400 validation failure or enum type mismatch
- 401 unauthenticated
- 403 employee modifying another user alert
- 404 alert not found
- 409 illegal status transition
- 500 unexpected server error

#### POST /api/alerts/{id}/assign

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Request body:

~~~json
{
  "assigneeUserId": "number, required"
}
~~~

Success response (200): AlertResponse.

Error responses:

- 400 validation failure
- 401 unauthenticated
- 403 insufficient role
- 404 alert or assignee user not found
- 500 unexpected server error

#### DELETE /api/alerts/{id}

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Request body: none.

Success response (204): empty body.

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 404 alert not found
- 500 unexpected server error

### Dashboard (/api/dashboard/*)

#### GET /api/dashboard/me

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Success response (200): DashboardSummaryResponse.

~~~json
{
  "totalActivities": 120,
  "latestRiskScore": 64,
  "openAlertsCount": 2,
  "criticalAlertsCount": 1,
  "recentActivities": [
    {
      "id": 10,
      "userId": 1,
      "action": "LOGIN_SUCCESS",
      "entityType": "AUTH",
      "entityId": "1",
      "metadata": "{\"ip\":\"127.0.0.1\"}",
      "createdAt": "2026-04-05T12:00:00"
    }
  ]
}
~~~

Error responses:

- 401 unauthenticated
- 500 unexpected server error

#### GET /api/dashboard/admin

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ADMIN |

Success response (200): AdminDashboardResponse.

~~~json
{
  "totalUsers": 25,
  "totalOpenAlerts": 6,
  "averageRiskScore": 31.5,
  "highRiskUserCount": 4
}
~~~

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

#### GET /api/dashboard/summary

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | Any authenticated |

Success response (200):

- EMPLOYEE receives DashboardSummaryResponse
- ANALYST and ADMIN receive AdminDashboardResponse

Error responses:

- 401 unauthenticated
- 500 unexpected server error

#### GET /api/dashboard/risk-trends

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Success response (200): array of RiskTrendResponse.

~~~json
[
  {
    "period": "2026-W14",
    "averageScore": 42.3,
    "highRiskCount": 5
  }
]
~~~

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

#### GET /api/dashboard/alert-stats

| Property | Value |
|---|---|
| Auth required | Yes |
| Required role | ANALYST or ADMIN |

Success response (200): AlertStatsResponse.

~~~json
{
  "totalOpen": 6,
  "totalUnderInvestigation": 3,
  "totalResolved": 9,
  "bySeverity": {
    "LOW": 4,
    "MEDIUM": 6,
    "HIGH": 5,
    "CRITICAL": 3
  }
}
~~~

Error responses:

- 401 unauthenticated
- 403 insufficient role
- 500 unexpected server error

### Health

#### GET /health

| Property | Value |
|---|---|
| Auth required | No |
| Required role | Public |

Success response:

- 200 when DB connected
- 503 when DB disconnected

Body fields: status, application, database, timestamp.

## Section 6 - Security Model

### JWT Access Token

Access tokens are issued at registration and login as field token in AuthResponse.

Claims include subject (username) and roles.

Lifetime is controlled by JWT_EXPIRATION_MS.

### Refresh Token

Refresh tokens are stored in refresh_tokens table with expiry_date and revoked flag.

Rotation flow:

1. Client submits refresh token to POST /api/auth/refresh.
2. Existing token is validated.
3. Existing token is revoked.
4. New refresh token is created and returned with a new access token.

Revocation flow:

- POST /api/auth/logout revokes all refresh tokens for the current user.

### Role Model and Access

- EMPLOYEE: own profile, own activity, own risk data, own alerts, personal dashboard.
- ANALYST: cross-user read access for activity, risk, alerts, and analytics endpoints; can assign alerts.
- ADMIN: full access including user CRUD and admin dashboard.

URL-level rules are defined in SecurityConfig and method-level rules are enforced with PreAuthorize on controller methods.

### Password Storage

Passwords are stored as BCrypt hashes through PasswordEncoder. Plain-text password persistence is never used.

### Email Verification Flow

Registration and admin user creation trigger verification token generation and dev email dispatch logging.

Token verification endpoint marks email_verified=true when token is valid.

Current behavior: login does not block unverified accounts yet; verification state is stored and available in user response payload.

### Password Reset Flow

Forgot-password creates a one-time token (30 minute TTL) if email exists and returns 200 regardless.

Reset-password validates token existence, expiry, and used flag; then updates password hash and marks token as used.

Silent failure behavior for unknown email is implemented to prevent account enumeration.

## Section 7 - Testing

Current backend suite status is 159 tests with 0 failures.

Test types in the project include:

- Unit tests
- Repository slice tests with DataJpaTest
- Integration tests with SpringBootTest and MockMvc

Run all tests:

~~~bash
./mvnw.cmd test
~~~

Run a single test class:

~~~bash
./mvnw.cmd -Dtest=AuthControllerTest test
~~~

Test profile behavior:

- Isolated datasource is used for tests.
- No real email provider is used.
- Dev email service writes reset and verification links to logs.

Coverage includes auth flow, token lifecycle, user management, activity endpoints, risk scoring, alert lifecycle, dashboard aggregation, and authorization rules by role.

## Section 8 - Frontend Integration Guide

### Base URL and Headers

- Base URL for development: http://localhost:8081
- Protected route header: Authorization: Bearer access_token
- Content-Type: application/json

### Frontend Auth Flow

1. Call POST /api/auth/register or POST /api/auth/login.
2. Store token and refreshToken from response. Preferred storage is in-memory state or secure HttpOnly cookies; do not store tokens in localStorage.
3. Attach Authorization bearer token to every protected API call.
4. On any 401 from protected API, call POST /api/auth/refresh with refreshToken.
5. If refresh also returns 401, treat session as expired and redirect to login.
6. On logout, call POST /api/auth/logout, then clear client-side auth state.

### Role-Based UI Guidance

- EMPLOYEE: show personal dashboard, own activity timeline, own alerts.
- ANALYST: show global activity, global risk views, global alert management, hide admin-only controls.
- ADMIN: show all analyst views plus user management and admin dashboard widgets.

### Expected Response Shapes

Login success response:

~~~json
{
  "token": "jwt-access-token",
  "username": "alice",
  "refreshToken": "uuid-refresh-token"
}
~~~

Register success response:

~~~json
{
  "token": "jwt-access-token",
  "username": "alice",
  "refreshToken": null
}
~~~

Activity list response (paginated):

~~~json
{
  "content": [
    {
      "id": 1,
      "userId": 1,
      "action": "LOGIN_SUCCESS",
      "entityType": "AUTH",
      "entityId": "1",
      "metadata": "{\"ip\":\"127.0.0.1\"}",
      "createdAt": "2026-04-05T12:00:00"
    }
  ],
  "pageable": {},
  "last": true,
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0,
  "sort": {},
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
~~~

Risk score response:

~~~json
{
  "id": 77,
  "userId": 1,
  "score": 64,
  "reason": "Elevated risk due to frequent and off-hours activity.",
  "calculatedAt": "2026-04-05T12:00:00"
}
~~~

Alert response:

~~~json
{
  "id": 15,
  "userId": 1,
  "riskScoreId": 77,
  "severity": "HIGH",
  "message": "Risk score 64 detected for user alice.",
  "status": "OPEN",
  "createdAt": "2026-04-05T12:00:00",
  "updatedAt": "2026-04-05T12:00:00"
}
~~~

Dashboard summary response:

~~~json
{
  "totalActivities": 120,
  "latestRiskScore": 64,
  "openAlertsCount": 2,
  "criticalAlertsCount": 1,
  "recentActivities": []
}
~~~

### Error Handling Guide

400 indicates validation or parameter type errors. Response shape:

~~~json
{
  "timestamp": "2026-04-05T12:00:00Z",
  "status": 400,
  "error": "Validation failed"
}
~~~

401 indicates unauthenticated requests or invalid token lifecycle state. Refresh token or redirect to login.

403 indicates authenticated but not authorized. Hide forbidden UI actions and show access denied state.

404 indicates resource not found. Show empty or not found view.

500 indicates server-side failure. Show generic error UI and log diagnostic context.

### Not Implemented Yet

- Real email sending provider in non-dev environments
- Rate limiting
- Redis-backed token blacklist/revocation store
- Dedicated audit logging subsystem
- Frontend UI implementation itself

## Section 9 - Branching and Git Workflow

### Branching Strategy

- main: stable, production-ready history only
- develop: integration branch for completed work
- feature/*: one branch per feature, branched from develop, merged back using no-ff
- fix/*: one branch per bug fix, branched from develop, merged back using no-ff

### Workflow Rules

- Never commit directly to main or develop.
- Always branch from develop.
- Always merge using --no-ff to preserve integration history.
- Never force push to main.
- Run full test suite before merging into develop.

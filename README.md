#  SentinelX – Real-Time Insider Threat Monitoring System

> An enterprise-grade, full-stack application for monitoring internal user activities, calculating behavioral risk scores, and generating automated security alerts — built with Java Spring Boot, PostgreSQL, and React.js.

---

## 📋 Table of Contents

- [Project Overview](#project-overview)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Core Modules](#core-modules)
- [OOP Design](#oop-design)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Risk Scoring Rules](#risk-scoring-rules)
- [Alert Severity & Thresholds](#alert-severity--thresholds)
- [Role-Based Access Control](#role-based-access-control)
- [Project Structure](#project-structure)
- [Setup & Installation](#setup--installation)
- [Environment Configuration](#environment-configuration)
- [Development Phases](#development-phases)
- [What NOT To Do](#what-not-to-do)
- [Contributing](#contributing)
- [License](#license)

---

## Project Overview

SentinelX is a modular monolith full-stack system designed to detect, monitor, and respond to **insider threats** within an organization. It continuously tracks user behavior across login attempts, file access events, and privilege usage, then applies a **rule-based scoring engine** to generate dynamic risk assessments and automated security alerts.

**Key Goals:**
- Authenticate users securely with JWT and BCrypt
- Log all internal user activities in real time
- Calculate and update cumulative behavioral risk scores
- Auto-generate alerts based on configurable thresholds
- Visualize threat data on a role-sensitive dashboard

---

## Technology Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 17+ | Core language |
| Spring Boot 3.x | REST API framework |
| Maven | Dependency management |
| Spring Data JPA / Hibernate | ORM and DB interaction |
| Spring Security + JWT | Authentication & authorization |
| BCrypt | Password hashing |

### Database
| Technology | Purpose |
|---|---|
| PostgreSQL 15+ | Primary relational database |
| ACID Transactions | Data integrity |
| Foreign Key Constraints | Referential integrity |
| Indexes | Query performance on `user_id`, `timestamp` |

### Frontend
| Technology | Purpose |
|---|---|
| React.js (v18+) | UI framework |
| Axios | HTTP client for API calls |
| Chart.js | Risk trend and alert charts |
| Bootstrap 5 / Tailwind CSS | Responsive UI styling |
| React Router | Client-side routing |

### Dev Tools
| Tool | Purpose |
|---|---|
| IntelliJ IDEA / VS Code | IDEs |
| pgAdmin 4 | PostgreSQL GUI |
| Postman | API testing |
| GitHub | Version control |

---

## System Architecture

```
┌─────────────────────────────────┐
│        React.js Frontend        │
│  (Dashboard, Alerts, Logs, UI)  │
└────────────────┬────────────────┘
                 │ HTTP / Axios (REST)
┌────────────────▼────────────────┐
│     Spring Boot Backend         │
│  ┌────────┐  ┌────────────────┐ │
│  │  auth  │  │  user          │ │
│  ├────────┤  ├────────────────┤ │
│  │activity│  │  risk          │ │
│  ├────────┤  ├────────────────┤ │
│  │  alert │  │  dashboard     │ │
│  └────────┘  └────────────────┘ │
└────────────────┬────────────────┘
                 │ JPA / Hibernate
┌────────────────▼────────────────┐
│         PostgreSQL DB           │
│  users | roles | activity_logs  │
│  risk_scores | alerts           │
└─────────────────────────────────┘
```

**Architecture Type:** 3-Tier Modular Monolith

---

## Core Modules

### 1. Authentication & Authorization (`auth`)
- Secure login with username + password
- Password hashing using **BCrypt**
- **JWT token** issuance on successful login (access + refresh tokens)
- JWT validation middleware on all protected routes
- Role-Based Access Control: `ADMIN`, `ANALYST`, `EMPLOYEE`
- Token expiry and invalidation handling

**Endpoints:**
```
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/refresh-token
```

---

### 2. User Management (`user`)
- CRUD operations for user accounts
- Assign or update roles
- Enable / disable user accounts
- View user profiles with risk score summary
- Admin-only access for sensitive operations

**Endpoints:**
```
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
DELETE /api/users/{id}
PATCH  /api/users/{id}/status
```

---

### 3. Activity Logging System (`activity`)
- Capture events:
  - `LOGIN_SUCCESS` / `LOGIN_FAILURE`
  - `FILE_ACCESS` / `UNAUTHORIZED_FILE_ACCESS`
  - `PRIVILEGE_USAGE`
  - `OFF_HOURS_ACCESS`
  - `DATA_EXPORT` *(added)*
  - `ADMIN_OVERRIDE` *(added)*
- Store: `event_type`, `timestamp`, `ip_address`, `device_info`, `status`, `user_id`
- Maintain full historical audit trail
- Paginated log retrieval for large datasets

**Endpoints:**
```
GET  /api/activities?userId=&page=&size=
GET  /api/activities/{id}
POST /api/activities   (internal use by services)
```

---

### 4. Behavioral Risk Scoring Engine (`risk`)

See [Risk Scoring Rules](#risk-scoring-rules) for full rule table.

- Applies rules to incoming activity events
- Updates cumulative risk score per user in real time
- Stores risk score history for trend analysis
- Triggers alert module if threshold is crossed
- Supports score decay over time *(recommended addition)*

**Endpoints:**
```
GET /api/risk/{userId}
GET /api/risk/{userId}/history
```

---

### 5. Alert Generation Module (`alert`)
- Auto-generated when risk score crosses a threshold
- Categorized by severity: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- Alert lifecycle statuses: `OPEN` → `UNDER_INVESTIGATION` → `RESOLVED`
- Assign analyst to investigate
- Timestamped resolution notes

**Endpoints:**
```
GET    /api/alerts
GET    /api/alerts/{id}
PATCH  /api/alerts/{id}/status
POST   /api/alerts/{id}/assign
DELETE /api/alerts/{id}
```

---

### 6. Dashboard & Visualization (`dashboard`)
- Total registered users
- Count of high-risk users (score > 70)
- Active alerts by severity
- Risk score trends over time (Chart.js line graph)
- Alert severity distribution (Chart.js pie/doughnut)
- Recent activity feed

**Endpoints:**
```
GET /api/dashboard/summary
GET /api/dashboard/risk-trends
GET /api/dashboard/alert-stats
```

---

## OOP Design

SentinelX is built with core OOP principles demonstrated throughout the codebase:

### Encapsulation
All entity fields are `private` with controlled access through getters/setters (or Lombok annotations).

```java
@Entity
public class User {
    @Id @GeneratedValue
    private Long id;
    private String username;
    private String passwordHash;
    private int riskScore;
    // getters & setters
}
```

### Inheritance
User types extend a base `User` entity, adding role-specific behavior.

```java
public class AdminUser extends User { ... }
public class AnalystUser extends User { ... }
public class EmployeeUser extends User { ... }
```

### Abstraction
`ThreatAnalyzer` is an abstract class providing shared logic while delegating specifics to subclasses.

```java
public abstract class ThreatAnalyzer {
    public abstract int calculateRisk(ActivityLog log);
    public void triggerAlert(User user) { ... }  // shared logic
}
```

### Polymorphism
Multiple risk strategy implementations fulfill the `RiskStrategy` interface.

```java
public interface RiskStrategy {
    int evaluate(ActivityLog log);
}

public class LoginFailureStrategy implements RiskStrategy { ... }
public class OffHoursStrategy implements RiskStrategy { ... }
public class UnauthorizedAccessStrategy implements RiskStrategy { ... }
```

### Interface Usage
`RiskStrategy` interface enforces a contract across all scoring algorithms.

### Composition
`ActivityLog` is composed with a `User` reference — it cannot exist independently.

```java
@Entity
public class ActivityLog {
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;  // Composition: ActivityLog HAS-A User
    ...
}
```

---

## Database Schema

### Entity Relationship Overview

```
roles (1) ──── (M) users (1) ──── (M) activity_logs
                         (1) ──── (M) alerts
                         (1) ──── (M) risk_scores
```

### SQL Schema

```sql
-- Roles
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Users
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_id INT NOT NULL REFERENCES roles(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Activity Logs
CREATE TABLE activity_logs (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    device_info VARCHAR(255),
    status VARCHAR(50),
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Risk Scores
CREATE TABLE risk_scores (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score INT NOT NULL DEFAULT 0,
    reason VARCHAR(255),
    recorded_at TIMESTAMP DEFAULT NOW()
);

-- Alerts
CREATE TABLE alerts (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','UNDER_INVESTIGATION','RESOLVED')),
    assigned_to INT REFERENCES users(id),
    description TEXT,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    resolved_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_activity_user_id ON activity_logs(user_id);
CREATE INDEX idx_activity_timestamp ON activity_logs(occurred_at);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_risk_user_id ON risk_scores(user_id);
```

---

## API Reference

All protected routes require: `Authorization: Bearer <JWT_TOKEN>`

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Login and receive JWT |
| GET | `/api/users` | Admin | List all users |
| POST | `/api/users` | Admin | Create new user |
| GET | `/api/activities` | Admin, Analyst | Get activity logs |
| GET | `/api/risk/{userId}` | Admin, Analyst | Get user risk score |
| GET | `/api/alerts` | Admin, Analyst | Get all alerts |
| PATCH | `/api/alerts/{id}/status` | Analyst | Update alert status |
| GET | `/api/dashboard/summary` | Admin, Analyst | Dashboard metrics |

---

## Risk Scoring Rules

| Event Type | Score Impact | Notes |
|---|---|---|
| `LOGIN_FAILURE` | +20 | Per failed attempt |
| `UNAUTHORIZED_FILE_ACCESS` | +30 | Per unauthorized attempt |
| `OFF_HOURS_ACCESS` | +15 | Outside 08:00–20:00 |
| `PRIVILEGE_ESCALATION` | +40 | Admin override / privilege usage |
| `DATA_EXPORT` | +25 | Large data extraction event |
| `MULTIPLE_DEVICE_LOGIN` | +20 | Same user, different devices |
| Score Decay | -5/day | Reduces if no suspicious activity *(recommended)* |

---

## Alert Severity & Thresholds

| Severity | Score Range | Response SLA |
|---|---|---|
| LOW | 20 – 39 | Monitor |
| MEDIUM | 40 – 59 | Review within 48h |
| HIGH | 60 – 79 | Investigate within 24h |
| CRITICAL | 80+ | Immediate escalation |

---

## Role-Based Access Control

| Feature | Admin | Analyst | Employee |
|---|---|---|---|
| View Dashboard | ✅ | ✅ | ❌ |
| View All Users | ✅ | ❌ | ❌ |
| Manage Users | ✅ | ❌ | ❌ |
| View Activity Logs | ✅ | ✅ | ❌ |
| View Own Activity | ✅ | ✅ | ✅ |
| View Alerts | ✅ | ✅ | ❌ |
| Update Alert Status | ✅ | ✅ | ❌ |
| View Risk Scores | ✅ | ✅ | ❌ |

---

## Project Structure

```
sentinelx/
├── backend/
│   └── src/main/java/com/sentinelx/
│       ├── auth/
│       │   ├── AuthController.java
│       │   ├── AuthService.java
│       │   ├── JwtUtil.java
│       │   └── JwtFilter.java
│       ├── user/
│       │   ├── User.java
│       │   ├── UserController.java
│       │   ├── UserService.java
│       │   └── UserRepository.java
│       ├── activity/
│       │   ├── ActivityLog.java
│       │   ├── ActivityController.java
│       │   ├── ActivityService.java
│       │   └── ActivityRepository.java
│       ├── risk/
│       │   ├── RiskStrategy.java          (interface)
│       │   ├── ThreatAnalyzer.java        (abstract)
│       │   ├── LoginFailureStrategy.java
│       │   ├── OffHoursStrategy.java
│       │   ├── UnauthorizedAccessStrategy.java
│       │   ├── RiskScore.java
│       │   ├── RiskController.java
│       │   └── RiskService.java
│       ├── alert/
│       │   ├── Alert.java
│       │   ├── AlertController.java
│       │   ├── AlertService.java
│       │   └── AlertRepository.java
│       └── dashboard/
│           ├── DashboardController.java
│           └── DashboardService.java
│
├── frontend/
│   └── src/
│       ├── components/
│       │   ├── Navbar.jsx
│       │   ├── Sidebar.jsx
│       │   └── ProtectedRoute.jsx
│       ├── pages/
│       │   ├── Login.jsx
│       │   ├── Dashboard.jsx
│       │   ├── Users.jsx
│       │   ├── ActivityLogs.jsx
│       │   ├── Alerts.jsx
│       │   └── RiskProfile.jsx
│       ├── services/
│       │   └── api.js                    (Axios instance)
│       └── context/
│           └── AuthContext.jsx
│
├── database/
│   └── schema.sql
│
└── README.md
```

---

## Setup & Installation

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 15+
- pgAdmin (optional)

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/sentinelx.git
cd sentinelx
```

### 2. Database Setup
```bash
# Create database in PostgreSQL
psql -U postgres -c "CREATE DATABASE sentinelx;"

# Run schema
psql -U postgres -d sentinelx -f database/schema.sql
```

### 3. Backend Setup
```bash
cd backend
# Edit src/main/resources/application.properties
mvn clean install
mvn spring-boot:run
```

Backend runs at: `http://localhost:8080`

### 4. Frontend Setup
```bash
cd frontend
npm install
npm start
```

Frontend runs at: `http://localhost:3000`

---

## Environment Configuration

### `backend/src/main/resources/application.properties`
```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/sentinelx
spring.datasource.username=postgres
spring.datasource.password=yourpassword

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# JWT
jwt.secret=your_jwt_secret_key_here
jwt.expiration=86400000

# Server
server.port=8080
```

### `frontend/.env`
```env
REACT_APP_API_BASE_URL=http://localhost:8080/api
```

---

## Development Phases

| Phase | Focus | Deliverable |
|---|---|---|
| **Phase 1** | Requirement Analysis | System scope, roles, rules |
| **Phase 2** | Database Design | ER diagram, SQL schema |
| **Phase 3** | Backend Development | REST APIs, auth, scoring, alerts |
| **Phase 4** | Frontend Development | Login, dashboard, alert UI |
| **Phase 5** | Integration & Testing | End-to-end testing, RBAC verification |
| **Phase 6** | Finalization | Error handling, docs, PPT |

---

## What NOT To Do

| ❌ Avoid | ✅ Do Instead |
|---|---|
| Microservices | Modular Monolith |
| Go / Node.js backend | Java Spring Boot |
| NoSQL databases | PostgreSQL with relational schema |
| DevOps / Docker complexity | Local setup, focus on core logic |
| Hardcoded credentials | Use `application.properties` / `.env` |
| Plaintext passwords | BCrypt hashing |

---

## Additions Over Original Scope

The following were identified as gaps and added to strengthen the system:

1. **Score Decay Mechanism** — Risk scores decay over time if no suspicious activity is detected, preventing permanent penalization
2. **Additional Event Types** — `DATA_EXPORT` and `ADMIN_OVERRIDE` added for more realistic threat modeling
3. **Alert Assignment** — Analysts can be assigned to specific alerts for accountability
4. **Refresh Token Support** — Prevents users from being logged out abruptly mid-session
5. **Pagination on Activity Logs** — Necessary for production-scale log tables
6. **Device/IP Metadata** — Stored in activity logs for forensic investigation
7. **Resolution Notes on Alerts** — Analysts can document findings when closing alerts
8. **`MULTIPLE_DEVICE_LOGIN` Scoring** — Flags concurrent sessions from different devices

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add: your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## License

This project is developed for academic and educational purposes.

---

*Built with Java, Spring Boot, PostgreSQL, and React.js — SentinelX | Insider Threat Monitoring System*
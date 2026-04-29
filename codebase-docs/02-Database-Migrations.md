# Database Migrations (Flyway)

This document explains the database schema evolution for Sentinel-X. The project uses **Flyway** for database migrations, which means the database schema is strictly version-controlled using `.sql` files in `src/main/resources/db/migration/`.

When the application starts up, Flyway checks the database's `flyway_schema_history` table to see which scripts have already been run, and automatically applies any new ones in numerical order.

---

# File: `V1__init.sql`

## 1. Purpose
Initializes the core security schema: `roles` and `users`.

## 3. Internal Breakdown
* **`roles` Table**:
  * `id BIGSERIAL PRIMARY KEY`: Auto-incrementing unique identifier.
  * `name VARCHAR(50) UNIQUE`: The name of the role (e.g., ADMIN, ANALYST).
* **`users` Table**:
  * `username`, `email`, `password_hash`: Core credentials.
  * `is_active`: Soft-delete/disable flag.
  * `role_id`: A Foreign Key linking back to the `roles` table.
* **Indexes**: Creates an index on `users(role_id)` to speed up queries like "Find all ADMIN users".

## 9. Beginner Explanation
This is the "Big Bang" of the database. Before this script, the database is totally empty. After this script, the system can securely store users and group them by roles.

---

# File: `V2__add_refresh_tokens.sql`

## 1. Purpose
Adds support for long-lived JWT refresh tokens.

## 3. Internal Breakdown
* **`refresh_tokens` Table**:
  * `token VARCHAR(255) UNIQUE`: The actual refresh string.
  * `user_id BIGINT`: Links the token to a specific user.
  * `expiry_date TIMESTAMP`: When the token dies.
  * `revoked BOOLEAN`: Allows the system to forcefully log a user out (e.g., if a device is stolen).
* **Indexes**: Adds indexes on `user_id` and `token` for rapid lookups during token rotation.

---

# File: `V3__add_password_reset_tokens.sql`

## 1. Purpose
Creates a table to handle the "Forgot Password" workflow.

## 3. Internal Breakdown
* **`password_reset_tokens` Table**:
  * Very similar to refresh tokens, but includes a `used BOOLEAN` flag to ensure a password reset link can only be clicked once.

---

# File: `V4__add_email_verification.sql`

## 1. Purpose
Mandates email verification for new accounts.

## 3. Internal Breakdown
* **`ALTER TABLE users ADD COLUMN email_verified`**: Retroactively adds a boolean flag to the `users` table.
* **`email_verification_tokens` Table**: Stores the unique, single-use token sent to the user's email upon signup.

---

# File: `V5__add_activity_table.sql`

## 1. Purpose
Sets up an audit trail to log what users are doing inside the system.

## 3. Internal Breakdown
* **`activities` Table**:
  * `action VARCHAR(100)`: What happened (e.g., "LOGIN", "VIEW_DASHBOARD").
  * `entity_type`, `entity_id`: Which specific object was touched.
  * `metadata TEXT`: JSON blob for extra, unstructured context.
* **Indexes**: Indexed by `user_id`, `entity_type`, and `created_at` to support rapid timeline queries and filtering.

---

# File: `V6__add_risk_scores_table.sql`

## 1. Purpose
Creates a table to track dynamic risk assessments associated with users.

## 3. Internal Breakdown
* **`risk_scores` Table**:
  * `score INTEGER`: A numeric value (e.g., 0-100) representing threat level.
  * `reason VARCHAR(255)`: Why the score was given (e.g., "Failed login attempts").
  * `calculated_at TIMESTAMP`: When the score was assessed.

---

# File: `V7__add_alerts_table.sql`

## 1. Purpose
Creates a system for generating actionable alerts for the security team (Analysts).

## 3. Internal Breakdown
* **`alerts` Table**:
  * `risk_score_id`: Optionally links an alert to the specific risk calculation that triggered it.
  * `severity VARCHAR(20)`: LOW, MEDIUM, HIGH, CRITICAL.
  * `status VARCHAR(20)`: E.g., OPEN, INVESTIGATING, RESOLVED.

---

# File: `V8__add_user_status.sql`

## 1. Purpose
Upgrades the simple `is_active` boolean from V1 into a more robust state machine.

## 3. Internal Breakdown
* **`ALTER TABLE users ADD COLUMN status`**: Replaces the binary active/inactive with states like `ACTIVE`, `SUSPENDED`, `LOCKED`.

---

# File: `V9__add_alert_assignee.sql`

## 1. Purpose
Allows security analysts to "claim" or be assigned an alert to investigate.

## 3. Internal Breakdown
* **`ALTER TABLE alerts ADD COLUMN assigned_to_user_id`**: A foreign key pointing back to the `users` table, specifically representing the analyst working on the alert.

---

# File: `V10__add_dashboard_query_indexes.sql`

## 1. Purpose
Optimizes database read performance for the Dashboard UI.

## 2. Why This File Exists
As the `activities`, `alerts`, and `risk_scores` tables grow, the dashboard aggregations (like "alerts per day" or "top risky users") would become slow.

## 3. Internal Breakdown
* Adds compound indexes:
  * `idx_activities_user_created` (user_id, created_at)
  * `idx_alerts_status_created` (status, created_at)
  * `idx_risk_scores_user_created` (user_id, calculated_at DESC)
* **Performance Note:** These indexes are specifically designed to speed up the raw JDBC queries found in `DashboardJdbcRepository`.

---

# File: `V11__add_rules_and_audit_logs.sql`

## 1. Purpose
Introduces a dynamic rule engine and a dedicated, stricter audit log.

## 3. Internal Breakdown
* **`rules` Table**:
  * Stores configurable conditions (`condition_text`) that, when met, assign a `risk_score` and `severity`. Includes `CHECK` constraints to ensure scores stay between 0 and 100.
* **`audit_logs` Table**:
  * Differs from the `activities` table. This is specifically for tracking system-level trigger events (e.g., when a rule fires). Links back to `users` using `ON DELETE SET NULL` to preserve the audit history even if the offending user is deleted.

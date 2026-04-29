# Alert and Activity Modules Documentation

These two modules handle the auditing (Activity) and the notification of security issues (Alerts) within Sentinel-X.

---

# Part 1: The Alert Module

This module manages the lifecycle of a security alert, from creation (usually triggered by a high risk score) to resolution by an analyst.

## File: `src/main/java/com/sentinelx/alert/controller/AlertController.java`

### 1. Purpose
Provides REST endpoints for querying and modifying alerts.

### 3. Internal Breakdown
* **`GET /me`**: Fetches alerts belonging to the logged-in user.
* **`PATCH /{id}/acknowledge`**: Marks an alert as acknowledged by the user.
* **`PATCH /{id}/resolve`**: Marks an alert as resolved.
* **`GET /`**: Fetches all alerts globally (Restricted to `ANALYST` and `ADMIN`).
* **`PATCH /{id}/status`**: Manually updates an alert's status.
* **`POST /{id}/assign`**: Allows an Admin/Analyst to assign an alert to a specific investigator.
* **`DELETE /{id}`**: Deletes an alert (Restricted to `ADMIN`).

### 9. Beginner Explanation
This is the security guard's walkie-talkie. It lets the guard report a problem (an alert), tell headquarters they are looking into it (acknowledge), and report when the problem is fixed (resolve).

## File: `src/main/java/com/sentinelx/alert/service/AlertService.java`

### 1. Purpose
The business logic for alert management, strictly enforcing who can transition an alert between states.

### 3. Internal Breakdown
* **`generateAlert(User, RiskScore)`**: Creates a new alert. Calculates the severity (LOW, MEDIUM, HIGH, CRITICAL) based on hardcoded score thresholds.
* **State Machine Validation**:
  * `validateStatusTransition(current, newStatus)` is a critical method.
  * An `OPEN` alert can move to `UNDER_INVESTIGATION` or `ACKNOWLEDGED`.
  * An `ACKNOWLEDGED` alert can move to `RESOLVED`.
  * You **cannot** move a `RESOLVED` alert back to `OPEN`. It throws `AlertInvalidStatusTransitionException` if attempted.
* **Access Control**:
  * `assertModifyAccess()`: Ensures that only the user who triggered the alert, or a system Admin/Analyst, can acknowledge or resolve it.

### 10. Important Concepts to Learn
* **Finite State Machines (FSM)**: Enforcing strict rules about what status can follow another status.

---

# Part 2: The Activity Module

This module is a universal auditing system. Whenever a user does something significant, an Activity is logged.

## File: `src/main/java/com/sentinelx/activity/controller/ActivityController.java`

### 1. Purpose
Provides endpoints to search the audit trail.

### 3. Internal Breakdown
* **`GET /me`**: Fetches the logged-in user's activity history.
* **`GET /entity/{entityType}`**: Fetches all activities related to a specific object (e.g., "Show me everything that happened to User Profile 123"). Restricted to Analysts/Admins.
* **`GET /`** (with `userId` param): Fetches all activities for a specific user.

### 9. Beginner Explanation
This is the security camera footage room. You can ask for tapes showing what a specific person did today, or tapes showing who touched a specific door.

## File: `src/main/java/com/sentinelx/activity/service/ActivityService.java`

### 1. Purpose
Provides a simple, fast API for other modules to record audit logs.

### 3. Internal Breakdown
* **`logActivity(user, action, entityType, entityId, metadata)`**: The core recording method.
  * `action`: e.g., "PASSWORD_CHANGED"
  * `entityType`: e.g., "USER"
  * `entityId`: e.g., "42"
  * `metadata`: Optional extra JSON details.
* **Read Methods**: Standard Spring Data JPA pass-throughs returning paginated results.

### 6. Side Effects
Writing to the `activities` table. This method is heavily utilized by other services in the application.

### 10. Important Concepts to Learn
* **System Auditing**: Keeping an immutable, append-only log of system events for security and compliance purposes.

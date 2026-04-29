# Dashboard Module Documentation

This module handles the aggregation of data from across the system (users, alerts, risk scores, activities) to populate the frontend UI with summary statistics and trend charts.

---

# File: `src/main/java/com/sentinelx/dashboard/controller/DashboardController.java`

## 1. Purpose
Provides REST endpoints that return complex, pre-aggregated data specifically shaped for the frontend UI.

## 3. Internal Breakdown
* **Endpoints**:
  * `GET /me`: Returns the personal dashboard for the logged-in user (their recent activities, their risk score, and counts of alerts assigned to them).
  * `GET /admin`: Returns the global system dashboard (total users, total open alerts, average system risk score, and count of high-risk users).
  * `GET /summary`: A smart endpoint that checks the user's role and automatically routes them to either `/me` or `/admin`.
  * `GET /risk-trends`: Returns a historical trend of average risk scores grouped by week.
  * `GET /alert-stats`: Returns a breakdown of alerts grouped by status (OPEN, RESOLVED) and severity (CRITICAL, HIGH).

## 4. Inputs
Authentication context (who is asking?).

## 5. Outputs
Aggregated DTOs: `AdminDashboardResponse`, `DashboardSummaryResponse`, `RiskTrendResponse`, `AlertStatsResponse`.

## 9. Beginner Explanation
Instead of making the frontend download 10,000 users and 5,000 alerts just to count them, the frontend asks this Controller: "Give me the summary." This file gathers the summaries and hands them over in one neat package.

---

# File: `src/main/java/com/sentinelx/dashboard/service/DashboardService.java`

## 1. Purpose
Orchestrates data gathering from the `ActivityService`, `RiskScoreService`, `AlertService`, and the raw `DashboardJdbcRepository`.

## 3. Internal Breakdown
* **`getUserDashboard(User user)`**:
  * Calls `activityService.getActivitiesForUser` limiting to the 5 most recent.
  * Calls `riskScoreService.getLatestRiskScore`.
  * Calls `alertService` to count open and critical alerts.
* **`getAdminDashboard()`**:
  * Relies heavily on optimized database count queries rather than loading Java objects into memory.
* **`getRiskTrends()`**:
  * Fetches the last 8 weeks of data by calling `findWeeklyRiskTrendForPeriod` and formats the dates (e.g., "2024-W12").
* **`getAlertStats()`**:
  * Uses Java Streams to map raw `Object[]` database rows into a clean `Map<AlertStatus, Long>`.

## 10. Important Concepts to Learn
* **Data Transformation**: Using Java Streams (`.stream().collect(Collectors.toMap(...))`) to convert messy database rows into clean Maps.

---

# File: `src/main/java/com/sentinelx/dashboard/repository/DashboardJdbcRepository.java`

## 1. Purpose
Executes highly optimized, raw SQL queries using JDBC instead of Hibernate/JPA.

## 2. Why This File Exists
**Performance.** Hibernate (JPA) is great for saving and updating single rows (like `User` or `Activity`). However, if you need to calculate the average risk score of 1 million users, Hibernate would try to load 1 million Java objects into RAM, crashing the server. Pure JDBC just asks the database for the final number, which takes milliseconds.

## 3. Internal Breakdown
* **`NamedParameterJdbcTemplate`**: A Spring utility that lets you write SQL with named variables (e.g., `:from`) instead of question marks (`?`).
* **`RetryableReadService`**: Wraps queries to automatically retry them if a transient database network error occurs.
* **Complex Queries**:
  * `getTopRiskyUsers(limit)`: Uses a **correlated subquery** to find the absolute latest risk score for every user, orders them descending, and limits the result.
  * `getAlertTrendByDay(lastNDays)`: Groups alerts by `CAST(created_at AS DATE)` to count how many alerts fired per day.
  * `getSystemSummary()`: Runs four separate `COUNT` queries back-to-back to get total users, activities, open alerts, and high-risk users.

## 6. Side Effects
Read-only. Does not modify the database.

## 9. Beginner Explanation
While the rest of the application uses Spring's "magic" to avoid writing SQL, this file rolls up its sleeves and writes pure SQL. It's the heavy lifter designed specifically to crunch massive amounts of data as fast as possible.

## 10. Important Concepts to Learn
* **JPA vs JDBC**: Knowing when to use an ORM (for saving/updating entities) vs when to use raw SQL (for complex aggregations and reporting).
* **Correlated Subqueries**: Advanced SQL technique to fetch the "latest" row per group.

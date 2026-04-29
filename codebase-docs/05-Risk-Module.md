# Risk Module Documentation

This module is the core analytical engine of Sentinel-X. It calculates a "Risk Score" (from 0 to 100) for a given user based on their recent activity patterns. High risk scores trigger alerts for security analysts.

---

# File: `src/main/java/com/sentinelx/risk/controller/RiskController.java`

## 1. Purpose
Exposes endpoints to fetch risk scores and risk history.

## 2. Why This File Exists
Allows the frontend dashboard to display a user's current threat level and historical trend line.

## 3. Internal Breakdown
* **Endpoints**:
  * `GET /me`: Fetches the currently logged-in user's latest risk score. If they don't have one, it calculates it on the fly.
  * `GET /user/{userId}` & `GET /{userId}`: Fetches the risk score for a specific user. The second one includes a strict security check ensuring the caller is either an Admin/Analyst, or the user themselves.
  * `GET /history/me` & `GET /{userId}/history`: Fetches a paginated list of all past risk scores for charting purposes.
* **`ensureAdminAnalystOrOwn()`**: An authorization guard. Prevents a normal Employee from peeking at another Employee's risk score.

## 4. Inputs
`Authentication` (from the JWT) and `userId` from the URL path.

## 5. Outputs
Returns `RiskScoreResponse` DTOs or a `Page<RiskScoreResponse>`.

## 7. Relationships
Delegates entirely to `RiskScoreService`.

## 9. Beginner Explanation
This is the credit reporting agency. If you ask for your own credit score (risk score), it gives it to you. If a bank manager (Analyst) asks for your score, it gives it to them. If your neighbor asks for your score, it tells them to go away (Access Denied).

---

# File: `src/main/java/com/sentinelx/risk/service/RiskScoreService.java`

## 1. Purpose
Orchestrates the calculation, saving, and retrieval of risk scores.

## 2. Why This File Exists
Risk calculation requires pulling data from multiple places (Activities) and notifying other systems (Alerts). This service acts as the central coordinator.

## 3. Internal Breakdown
* **`evaluateRisk(User user)`**: The most important method in the module.
  1. Fetches the user's 50 most recent actions from the `ActivityRepository`.
  2. Passes the user and their activities to the `RiskScoringStrategy` to get a numeric score (0-100).
  3. Decides on a human-readable `reason` based on the score.
  4. Saves the new `RiskScore` to the database.
  5. **Critical Feature:** If the score is >= `60` (`ALERT_TRIGGER_SCORE_THRESHOLD`), it automatically calls `alertService.generateAlert()`.
* **Retrieval Methods**: `getLatestRiskScore()`, `getRiskHistory()`.

## 4. Inputs
`User` entities.

## 5. Outputs
Mapped `RiskScoreResponse` objects.

## 6. Side Effects
Writes `risk_scores` to the database. **Triggers cross-module side effects** by calling the `AlertService` if the score is too high.

## 7. Relationships
Couples the `Risk` module to both the `Activity` module (to read history) and the `Alert` module (to push notifications).

## 10. Important Concepts to Learn
* Module Orchestration: How one service acts as the glue between three different database domains.

---

# File: `src/main/java/com/sentinelx/risk/strategy/RiskScoringStrategy.java` & `BasicRiskScoringStrategy.java`

## 1. Purpose
Calculates the actual numeric risk score using a specific algorithm.

## 2. Why This File Exists
It implements the **Strategy Design Pattern**. By defining an interface (`RiskScoringStrategy`), the system can easily swap out the math behind risk scores (e.g., from a basic math formula to an AI-driven model) without having to rewrite the `RiskScoreService`.

## 3. Internal Breakdown
* **`RiskScoringStrategy` (Interface)**: Has a single method: `calculateScore(User, List<Activity>)`.
* **`BasicRiskScoringStrategy` (Implementation)**:
  * **Initial Score**: Starts at 0.
  * **Frequency Penalty**: If the user has >= 10 activities recently, adds 40 points.
  * **Off-Hours Penalty**: For every activity that occurred between 10:00 PM and 6:00 AM, adds 4 points.
  * **Cap**: Caps the final score at `100` using `Math.min(score, 100)`.

## 4. Inputs
A user and their 50 most recent activities.

## 5. Outputs
An integer between 0 and 100.

## 9. Beginner Explanation
This is the math teacher grading a test. The `RiskScoreService` hands the teacher a stack of the user's recent actions. The teacher gives them points for acting suspiciously (like working at 3 AM). If they get 60 points or more, they fail the test, and the teacher calls the principal (generates an Alert).

## 10. Important Concepts to Learn
* **Strategy Pattern**: A fundamental software design pattern that allows algorithms to be selected or swapped at runtime.

---

# Supporting Files Overview

* **`entity/RiskScore.java`**: The database model for `risk_scores`. Stores the user, the integer score, the text reason, and the exact timestamp it was calculated.
* **`dto/RiskScoreResponse.java`**: The safe wrapper sent to the frontend so it can render gauges and charts.

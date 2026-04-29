# Common and Exception Modules Documentation

This document covers the entry point of the application, health checks, common utilities, and the global error handling system.

---

# File: `src/main/java/com/sentinelx/BackendApplication.java`

## 1. Purpose
The absolute starting point of the Sentinel-X backend application.

## 2. Why This File Exists
Every Java application needs a `public static void main(String[] args)` method to start running.

## 3. Internal Breakdown
* **`@SpringBootApplication`**: A mega-annotation that tells Spring to:
  1. Auto-configure itself based on the libraries in `pom.xml`.
  2. Scan the `com.sentinelx` package and all its sub-packages for other components (like `@Service`, `@RestController`).
* **`SpringApplication.run(...)`**: Fires up the embedded Tomcat web server and initializes the entire application.

## 9. Beginner Explanation
This is the ignition key to the car. When you run this file, the engine (Spring Boot) turns on, dashboard lights up, and the car is ready to drive.

---

# File: `src/main/java/com/sentinelx/common/controller/HealthController.java`

## 1. Purpose
Provides endpoints to check if the server is alive and functioning properly.

## 2. Why This File Exists
Modern infrastructure (like Kubernetes or AWS) needs to know if your app has crashed so it can restart it. It does this by constantly pinging health endpoints.

## 3. Internal Breakdown
* **`@PreAuthorize("permitAll()")`**: These endpoints bypass JWT security so the infrastructure doesn't need to "log in" to check health.
* **`GET /health/live`**: Simply returns `{"status": "UP"}`. Proves the web server is running.
* **`GET /health/ready`**: Checks `healthService.getDbHealthResult()`. Returns HTTP 200 OK if the database is connected, or HTTP 503 SERVICE UNAVAILABLE if the database is down.

## 9. Beginner Explanation
This is the heart monitor. `live` checks if the heart is beating at all. `ready` checks if the brain (database) is actually communicating with the body.

---

# File: `src/main/java/com/sentinelx/common/service/RetryableReadService.java`

## 1. Purpose
Automatically retries database read operations if they fail due to a temporary network blip.

## 3. Internal Breakdown
* **`@Autowired private RetryTemplate readOnlyRetryTemplate;`**: Injects a Spring Retry template.
* **`executeRead(operationName, Callable<T> operation)`**: Takes a block of code (the query) and runs it. If the database drops the connection mid-query, the `RetryTemplate` automatically catches the error, waits a fraction of a second, and tries again.

## 10. Important Concepts to Learn
* **Resilience Patterns**: Building software that expects networks to fail and recovers gracefully without throwing an error to the user.

---

# File: `src/main/java/com/sentinelx/exception/GlobalExceptionHandler.java`

## 1. Purpose
Catches errors thrown anywhere in the application and converts them into standardized, clean JSON HTTP responses.

## 2. Why This File Exists
If a user tries to access a deleted alert, the `AlertService` throws an `AlertNotFoundException`. Without this file, Spring would return a massive, ugly Java Stack Trace and an HTTP 500 Server Error. This file catches it and returns a clean `{"error": "Alert not found"}` with an HTTP 404.

## 3. Internal Breakdown
* **`@RestControllerAdvice`**: Tells Spring: "Wrap every single Controller in the app. If any of them throw an exception, route it here."
* **`@ExceptionHandler(...)`**: Maps specific Java Exceptions to specific HTTP Status Codes:
  * `DuplicateEmailException` -> **HTTP 409 Conflict**
  * `InvalidCredentialsException` -> **HTTP 401 Unauthorized**
  * `AccessDeniedException` -> **HTTP 403 Forbidden**
  * `ResourceNotFoundException` -> **HTTP 404 Not Found**
  * `MethodArgumentNotValidException` -> **HTTP 400 Bad Request** (Triggered when DTO `@Valid` checks fail).
  * `Exception.class` -> **HTTP 500 Internal Server Error** (The final catch-all for unexpected bugs).

## 5. Outputs
A consistent JSON structure:
```json
{
  "timestamp": "2024-05-20T10:15:30Z",
  "status": 404,
  "error": "Alert not found."
}
```

## 9. Beginner Explanation
This is the PR (Public Relations) department. If something goes wrong in the back office (the code throws an error), the PR department intercepts it before the customer sees it. They hide the messy details (stack traces) and issue a polite, easy-to-read public statement (the JSON error message).

## 10. Important Concepts to Learn
* **Global Error Handling**: Using `@ControllerAdvice` to keep your Controllers completely clean of `try/catch` blocks.
* **HTTP Status Codes**: Understanding when to use 400 vs 401 vs 403 vs 404.

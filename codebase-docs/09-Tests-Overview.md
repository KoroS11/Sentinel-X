# Tests Overview

This document outlines the testing strategy used in the Sentinel-X backend to ensure reliability, security, and correctness before deployment.

---

# 1. Overall Testing Strategy

The repository follows a two-tiered testing approach:
1. **Unit and Integration Tests**: Testing individual services (like `UserServiceTest`) to ensure business logic works in isolation and database queries execute correctly.
2. **End-to-End (E2E) Tests**: Testing the full lifecycle of a user journey (like `E2EAuthFlowTest`) from HTTP Request to Database, mimicking a real browser client.

## Test Environment Configuration
Almost all tests use the `@ActiveProfiles("test")` annotation and configure an **in-memory H2 database**. This means the tests do not require a live PostgreSQL database to run. The database is created, used, and entirely destroyed within seconds.

---

# File: `src/test/java/com/sentinelx/e2e/E2EAuthFlowTest.java`

## 1. Purpose
Simulates a real user trying to register, log in, view a protected page, refresh their token, and reset their password.

## 3. Internal Breakdown
* **`MockMvc`**: A Spring utility that pretends to be a web browser. It sends fake HTTP requests (`GET`, `POST`) to our actual Controllers and checks the HTTP responses.
* **`@DynamicPropertySource`**: Dynamically overrides the `application.properties` just for this test, telling Spring to use the H2 database and disabling Flyway migrations (`spring.flyway.enabled=false`).
* **`endToEndAuthFlows()`**: A massive, single test method that tells a continuous story:
  1. Register a new user.
  2. Log in with the new user to get an Access Token and Refresh Token.
  3. Use the Access Token to access a protected Dashboard endpoint (Expect `200 OK`).
  4. Use a fake Access Token on the same endpoint (Expect `401 Unauthorized`).
  5. Use the Refresh Token to get a new Access Token.
  6. Log out.
  7. Try to use the old Refresh Token again (Expect `401 Unauthorized` because logout deleted it).
  8. Request a password reset email.
  9. Submit a new password.
  10. Log in with the old password (Expect `401 Unauthorized`).
  11. Log in with the new password (Expect `200 OK`).

## 9. Beginner Explanation
This is the robot QA tester. Instead of a human opening a browser, typing in a username, clicking "Register", and checking if it worked, this script does all of that programmatically in about 2 seconds.

---

# File: `src/test/java/com/sentinelx/user/service/UserServiceTest.java`

## 1. Purpose
Tests the specific business rules inside the `UserService`, primarily focusing on edge cases that are hard to trigger from the UI.

## 3. Internal Breakdown
* **`@MockBean EmailVerificationService`**: We don't want to actually send out real emails during automated tests! Mockito lets us create a "fake" email service.
* **`createUserWithDuplicateEmailThrowsConflictException()`**: Asserts that if you try to create a user with an email that already exists, it successfully throws a `DuplicateEmailException`.
* **`deleteUserOnAdminRoleThrowsMeaningfulException()`**: Asserts the critical business rule that attempting to delete an Admin user throws a `UserOperationNotAllowedException`.
* **`verify(emailVerificationService, times(1)).sendVerification(...)`**: A Mockito assertion checking that the fake email service was called exactly one time during user registration.

## 10. Important Concepts to Learn
* **Mocking**: Using libraries like Mockito to replace external dependencies (like an SMTP email server) with predictable "dummy" objects.
* **In-Memory Databases (H2)**: Speeding up tests by keeping data entirely in RAM instead of writing it to a slow hard drive.

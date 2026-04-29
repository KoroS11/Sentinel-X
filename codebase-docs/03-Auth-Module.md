# Auth Module Documentation

This module handles everything related to user identity: registration, login, token generation (JWT), password resets, and email verification.

---

# File: `src/main/java/com/sentinelx/auth/controller/AuthController.java`

## 1. Purpose
The entry point for all authentication-related HTTP requests from the frontend.

## 2. Why This File Exists
It acts as the API gateway for login, logout, and registration. It accepts raw JSON payloads, routes them to the `AuthService`, and returns structured HTTP responses.

## 3. Internal Breakdown
* **`@RestController`**: Marks this as a class that handles REST HTTP requests.
* **`@RequestMapping("/api/auth")`**: All endpoints in this file start with this URL path.
* **Endpoints**:
  * `POST /register`: Accepts a `RegisterRequest` DTO and creates a new user.
  * `POST /login`: Accepts a `LoginRequest` DTO.
  * `POST /refresh`: Accepts a `RefreshRequest` (containing a long-lived refresh token) to generate a new short-lived JWT.
  * `POST /logout`: Resolves the current user's token and revokes it.
  * `POST /forgot-password` & `/reset-password`: Triggers the password reset flow.
  * `GET /verify-email`: Accepts a query parameter `?token=` to mark an email as verified.

## 4. Inputs
JSON Request Bodies (e.g., `{"email": "...", "password": "..."}`) which are mapped to Java DTOs (`RegisterRequest`, `LoginRequest`).

## 5. Outputs
Returns `ResponseEntity<AuthResponse>` containing the `token`, `username`, and `refreshToken`, or simply `ResponseEntity<Void>` (HTTP 200 OK) for operations like logout or password reset.

## 6. Side Effects
Does not write to the DB directly; it delegates DB writes to the service layer. 

## 7. Relationships
Depends on `AuthService`, `RefreshTokenService`, `JwtTokenProvider`, and `PasswordResetService`.

## 8. Execution Flow
Receives an HTTP request -> Spring validates the DTO (`@Valid`) -> Calls Service layer -> Returns HTTP response.

## 9. Beginner Explanation
This file is the receptionist. When you want to check into the hotel (login) or sign up for a room (register), you talk to this file. The receptionist doesn't actually clean the room or make the key, they just take your information and pass it to the back office (the Service layer).

---

# File: `src/main/java/com/sentinelx/auth/service/AuthService.java`

## 1. Purpose
Contains the core business logic for registering and logging in users.

## 2. Why This File Exists
To separate business rules (like checking if an email already exists, or hashing passwords) from the HTTP layer.

## 3. Internal Breakdown
* **`register(RegisterRequest)`**: 
  * Checks if the email is already in the DB. Throws `DuplicateEmailException` if it is.
  * Finds the default `EMPLOYEE` role.
  * Creates a new `User` entity.
  * Encodes the plaintext password using `passwordEncoder`.
  * Saves to `userRepository`.
  * Triggers an email verification email to be sent.
* **`login(LoginRequest)`**:
  * Uses Spring's `AuthenticationManager` to verify the email and password. If wrong, throws `InvalidCredentialsException`.
  * Looks up the user in the database.
  * Calls `refreshTokenService` to create a refresh token.
  * Returns an `AuthResponse`.

## 4. Inputs
DTOs passed from the `AuthController`.

## 5. Outputs
Fully populated `AuthResponse` objects.

## 6. Side Effects
Writes new users to the database. Triggers outgoing emails via `EmailVerificationService`.

## 7. Relationships
Connects the `AuthController` to the database repositories (`UserRepository`, `RoleRepository`).

## 9. Beginner Explanation
This is the back office. The receptionist (Controller) hands this file the user's details. This file actually does the hard work: checking if the user already exists, safely scrambling their password so hackers can't read it, saving them to the database, and issuing their VIP pass (JWT token).

---

# File: `src/main/java/com/sentinelx/auth/jwt/JwtTokenProvider.java`

## 1. Purpose
Generates, parses, and validates JSON Web Tokens (JWTs).

## 3. Internal Breakdown
* **`generateToken(username, roles)`**: Uses the `io.jsonwebtoken` library to build a token. Sets the subject (username), adds custom claims (roles), sets an expiration date, and signs it cryptographically using a secret key.
* **`validateToken(String token)`**: Attempts to parse the token using the secret key. If it was tampered with or expired, it catches the `JwtException` and returns `false`.
* **`extractUsername(String token)`**: Reads the payload of the token to figure out who it belongs to.

## 9. Beginner Explanation
This file is the ID Badge Printer. When a user logs in successfully, this file prints an unforgeable digital ID badge (the JWT) that the user must show every time they want to access the system.

---

# File: `src/main/java/com/sentinelx/auth/jwt/JwtAuthenticationFilter.java`

## 1. Purpose
Intercepts every incoming HTTP request to check if the user has a valid digital ID badge (JWT).

## 3. Internal Breakdown
* **`extends OncePerRequestFilter`**: Ensures this code runs exactly once per HTTP request.
* **`doFilterInternal(...)`**:
  1. Extracts the token from the `Authorization: Bearer <token>` header.
  2. Asks `JwtTokenProvider` if the token is valid.
  3. If valid, extracts the username.
  4. Loads the user's roles from the database (`CustomUserDetailsService`).
  5. Tells Spring Security (`SecurityContextHolder`) that this user is officially logged in for this specific request.
  6. Calls `filterChain.doFilter(...)` to let the request continue to the Controller.

## 8. Execution Flow
Runs *before* the request ever reaches the `AuthController` or any other controller.

## 9. Beginner Explanation
This is the security guard standing in front of the building. Before you can talk to the receptionist, this guard checks your digital ID badge. If the badge is fake or expired, they kick you out. If it's valid, they radio the building letting them know exactly who just walked in.

---

# File: `src/main/java/com/sentinelx/auth/service/PasswordResetService.java`

## 1. Purpose
Manages the "Forgot Password" workflow securely.

## 3. Internal Breakdown
* **`initiateReset(String email)`**:
  * Finds the user by email.
  * Generates a random, unique `UUID` (the token).
  * Saves it to the database with a 30-minute expiration.
  * Sends an email with a link containing that token.
* **`resetPassword(String token, String newPassword)`**:
  * Looks up the token. Throws an error if it doesn't exist, is already used, or is expired.
  * Changes the user's password to the `newPassword` (hashed).
  * Marks the token as `used` so it can't be clicked again.

## 10. Important Concepts to Learn
* Time-bound, single-use security tokens.

---

# Supporting Files Overview

* **`dto/*`**: Pure data structures (Records or Classes with getters/setters). E.g., `LoginRequest` just holds `email` and `password`. They have `@Valid` annotations to ensure the data isn't empty or malformed before it hits the Controller.
* **`entity/RefreshToken.java` & `PasswordResetToken.java`**: Database models that represent the tokens stored in the `V2` and `V3` SQL migrations.
* **`exception/*`**: Custom error classes like `InvalidCredentialsException`. These make it easier for the global error handler to return the correct HTTP status code (like 401 Unauthorized instead of 500 Server Error).
* **`security/CustomUserDetailsService.java`**: A bridge class required by Spring Security. It tells Spring how to look up a user by their email address in our specific database layout.

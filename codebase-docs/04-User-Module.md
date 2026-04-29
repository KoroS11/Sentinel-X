# User Module Documentation

This module is responsible for managing the lifecycle of users within the system, including creating admins, fetching profiles, managing roles, and updating account states.

---

# File: `src/main/java/com/sentinelx/user/controller/UserController.java`

## 1. Purpose
Provides REST APIs for managing user profiles.

## 2. Why This File Exists
To expose CRUD (Create, Read, Update, Delete) operations on users to the frontend application, ensuring that only authorized users can perform these actions.

## 3. Internal Breakdown
* **`@RestController` & `@RequestMapping("/api/users")`**: Maps this class to the `/api/users` path.
* **Endpoints**:
  * `GET /`: Returns a paginated list of all users. Restricted to `ADMIN`.
  * `GET /{id}`: Returns a specific user's details. Restricted to the user themselves, or an Admin.
  * `POST /`: Creates a new user manually (bypassing the public registration). Restricted to `ADMIN`.
  * `PUT /{id}`: Updates a user's details (like changing an email).
  * `DELETE /{id}`: Deletes a user. Restricted to `ADMIN`.
  * `PATCH /{id}/status`: Updates a user's status (e.g., suspending them). Restricted to `ADMIN`.
* **`ensureAdminOrOwnProfile()`**: A critical security method. It intercepts requests for `/api/users/{id}` and checks if the logged-in user making the request has the `ADMIN` role. If not, it verifies that the `targetUserId` matches the logged-in user's ID.

## 4. Inputs
`CreateUserRequest`, `UpdateUserRequest`, `UserStatusRequest` DTOs via JSON bodies, and `{id}` path variables.

## 5. Outputs
JSON responses mapped from `UserResponse` DTO, or pagination wrappers `Page<UserResponse>`.

## 6. Side Effects
Delegates to `UserService` to mutate the database.

## 7. Relationships
Connects HTTP traffic to the `UserService` and relies on `UserRepository` for security context checks.

## 9. Beginner Explanation
This is the HR Department. If a manager (Admin) wants a list of all employees, they ask this file. If an employee wants to update their home address, they ask this file. The HR Department always checks your ID first to make sure you aren't trying to change someone else's files.

---

# File: `src/main/java/com/sentinelx/user/service/UserService.java`

## 1. Purpose
The core business logic engine for all user operations.

## 2. Why This File Exists
Controllers shouldn't contain business rules. This file ensures data integrity (e.g., you can't delete the last Admin) and handles the actual database interactions.

## 3. Internal Breakdown
* **`createUser(request)`**:
  * Checks for duplicate emails.
  * Maps the requested string role (e.g., "ADMIN") to the `RoleType` enum.
  * Hashes the password and saves the user.
* **`updateUser(id, request)`**:
  * Allows updating the username or email. Re-checks for email duplicates if the email is changing.
* **`deleteUser(id)`**:
  * Contains a critical business rule: **It prevents deleting an Admin if they are the very last Admin in the system**. If the last Admin is deleted, no one could ever manage the system again.
* **`updateUserStatus(id, request)`**:
  * Changes a user's state (e.g., ACTIVE, SUSPENDED).

## 4. Inputs
Extracted primitives (IDs) and DTOs from the Controller.

## 5. Outputs
`UserResponse` objects mapping the database entities back to safe API payloads.

## 6. Side Effects
Database writes. May trigger emails via `EmailVerificationService` when new users are created.

## 8. Execution Flow
Called directly by `UserController` methods.

## 10. Important Concepts to Learn
* Defensive Programming: specifically the logic preventing the deletion of the final Admin.
* Transaction Management (`@Transactional`): Ensuring database consistency.

---

# File: `src/main/java/com/sentinelx/user/entity/User.java`

## 1. Purpose
The primary object-relational mapping (ORM) representing the `users` database table.

## 3. Internal Breakdown
* **`@Entity` & `@Table(name = "users")`**: Tells Hibernate/JPA that this Java class directly maps to the `users` table.
* **Fields**: `id`, `username`, `email`, `passwordHash`, `active`, `emailVerified`, `status`, `createdAt`, `updatedAt`.
* **Relationships**:
  * `@ManyToOne` with `@JoinColumn(name = "role_id")`: Maps to the `Role` entity. FetchType is `LAZY` for performance.
* **Annotations**: Uses `@Getter` and `@Setter` from Lombok to hide boilerplate getter/setter code.

## 9. Beginner Explanation
This class is the exact blueprint of a row in the database. Instead of writing raw SQL code to select or update users, Java lets us work with these objects, and Spring automatically turns them into SQL behind the scenes.

---

# File: `src/main/java/com/sentinelx/user/entity/Role.java`

## 1. Purpose
Represents the `roles` database table.

## 3. Internal Breakdown
* **`name`**: Maps to an Enum `RoleType` (EMPLOYEE, ANALYST, ADMIN). Uses `@Enumerated(EnumType.STRING)` so the database stores the text "ADMIN" rather than a confusing number like "0" or "1".

---

# Supporting Files Overview

* **`repository/UserRepository.java`**: A Spring Data interface. By just declaring methods like `existsByEmail(String email)` or `countByRole_Name(RoleType name)`, Spring automatically generates the exact SQL required at runtime.
* **`dto/*`**: Request and Response wrappers. Notice how `UserResponse` never includes the `passwordHash`—this prevents accidental data leaks to the frontend.
* **`exception/*`**: Handled globally by the ControllerAdvice to return 404s (ResourceNotFound) or 403s (OperationNotAllowed).

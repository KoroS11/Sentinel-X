# Root and Configuration Files

This document explores the foundational files of the Sentinel-X backend. These files dictate how the application builds, connects to external systems, and bootstraps its initial state.

---

# File: `backend/pom.xml`

## 1. Purpose
The Project Object Model (POM) file is the core of Maven's build system. It manages project dependencies, plugins, Java version settings, and the build lifecycle.

## 2. Why This File Exists
Without `pom.xml`, Maven wouldn't know what libraries (like Spring Boot or PostgreSQL drivers) to download, or how to compile and package the Java code into an executable application.

## 3. Internal Breakdown
* **`<parent>`**: Inherits defaults from `spring-boot-starter-parent` (version 3.5.13).
* **`<properties>`**: Sets `java.version` to 17.
* **`<dependencies>`**: 
  * `spring-boot-starter-web`: Provides REST API capabilities (Tomcat, MVC).
  * `spring-boot-starter-data-jpa`: Provides Hibernate and JPA for database interactions.
  * `spring-boot-starter-security`: Provides Spring Security for auth.
  * `flyway-core` & `flyway-database-postgresql`: Handles automated SQL database migrations.
  * `postgresql`: The JDBC driver for connecting to PostgreSQL.
  * `jjwt-api` (plus impl/jackson): Java JWT library for creating and parsing auth tokens.
  * `lombok`: Reduces boilerplate code (getters, setters) via annotations.
  * `h2`: In-memory database used exclusively for the `test` scope.
* **`<build>`**: Configures the Maven compiler and Spring Boot plugins.

## 4. Inputs
Maven reads this file when you run commands like `mvn clean install`.

## 5. Outputs
Produces the final build artifact (e.g., a `.jar` file) and downloads dependencies into your local `~/.m2` folder.

## 6. Side Effects
Downloads third-party code from the internet (Maven Central).

## 7. Relationships
Every `.java` file in the project relies on the dependencies defined here.

## 8. Execution Flow
Parsed and executed at **compile time** and **build time**, not at runtime.

## 9. Beginner Explanation
Think of `pom.xml` as a grocery list and recipe combined. When you tell Maven to "cook" your app, it reads this file, goes to the store (internet) to buy the ingredients (dependencies like Spring, Postgres drivers), and mixes them together to bake your app.

## 10. Important Concepts to Learn
* Dependency Management
* Build Lifecycles (Maven)

---

# File: `backend/src/main/resources/application.properties`

## 1. Purpose
The master configuration file for Spring Boot.

## 2. Why This File Exists
It defines core, environment-agnostic properties (like the app name) and sets up the active profile.

## 3. Internal Breakdown
* `spring.application.name=backend`
* `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`: Tells Spring which specific environment file to load next (defaults to `dev`).
* `spring.flyway.enabled=true`: Turns on database migrations.
* `server.port=${SERVER_PORT:8080}`: Starts the web server on port 8080.

## 4. Inputs
Reads environment variables (like `SPRING_PROFILES_ACTIVE`).

## 5. Outputs
Configures the Spring `ApplicationContext` in memory.

## 6. Side Effects
Determines which database runs and what port the app binds to.

## 7. Relationships
Acts as the parent for `application-dev.properties`, `application-prod.properties`, etc.

## 8. Execution Flow
Read immediately when `BackendApplication.java` starts.

## 9. Beginner Explanation
This is the main settings menu for the app. It decides things like what port the server listens on and which secondary settings file (`dev`, `prod`, `test`) to activate.

## 10. Important Concepts to Learn
* Environment Variables
* Spring Profiles

---

# File: `backend/src/main/resources/application-dev.properties` (and prod/test)

## 1. Purpose
Environment-specific overrides.

## 2. Why This File Exists
You want a real PostgreSQL database in `dev` and `prod`, but a fake in-memory `H2` database for `test`.

## 3. Internal Breakdown
**`application-dev.properties` & `application-prod.properties`**:
* Expects `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` from the environment.
* `spring.jpa.hibernate.ddl-auto=validate`: A critical safety feature. It tells Hibernate *not* to create or drop tables automatically, but only to verify that the Java Entities match the database tables (which Flyway manages).
* Defines `jwt.secret` and health check thresholds.

**`application-test.properties`**:
* Uses `jdbc:h2:mem:testdb`: Spawns a temporary database in RAM.
* `spring.jpa.hibernate.ddl-auto=create-drop`: In tests, we *do* want Hibernate to create the tables from scratch and delete them when the test finishes.
* `spring.flyway.enabled=false`: We don't run Flyway migrations in tests.

## 9. Beginner Explanation
We don't want our automated tests accidentally deleting real user data. These files tell the app: "If you are running tests, use a fake, temporary database. If you are running locally (dev), use my local Postgres. If you are in prod, use the live server."

---

# File: `src/main/java/com/sentinelx/config/SecurityConfig.java`

## 1. Purpose
Configures the Spring Security framework, locking down endpoints and setting up JWT authentication.

## 2. Why This File Exists
To ensure that only logged-in users with the correct roles can access sensitive API routes, and to disable outdated security mechanisms like sessions.

## 3. Internal Breakdown
* **`@EnableWebSecurity` & `@EnableMethodSecurity`**: Activates Spring Security and allows method-level checks (like `@PreAuthorize`).
* **`securityFilterChain(HttpSecurity http)`**: The core router logic.
  * `.csrf(csrf -> csrf.disable())`: Disables Cross-Site Request Forgery protection (safe to do since we don't use cookies, we use JWTs).
  * `.sessionManagement(..., STATELESS)`: Tells Spring not to remember users between requests. Every request is isolated.
  * `.authorizeHttpRequests(...)`: The route map.
    * `/api/auth/**` -> `.permitAll()` (Anyone can log in/register).
    * `/api/dashboard/admin` -> `.hasAuthority(RoleConstants.ADMIN)`.
    * `.anyRequest().authenticated()` -> Everything else requires a valid token.
  * `.addFilterBefore(jwtAuthenticationFilter, ...)`: Forces our custom JWT checker to run *before* Spring's default username/password checker.
* **`passwordEncoder()`**: Returns a `BCryptPasswordEncoder` bean to securely hash passwords.
* **`authenticationManager()`**: Wires up the database (`customUserDetailsService`) to check passwords.

## 4. Inputs
HTTP requests coming into the server.

## 5. Outputs
Returns a fully configured `SecurityFilterChain` bean to Spring.

## 6. Side Effects
Rejects unauthenticated requests with a `401 Unauthorized`.

## 7. Relationships
Relies heavily on `JwtAuthenticationFilter` (to parse tokens) and `CustomUserDetailsService` (to load users from DB).

## 8. Execution Flow
Created during app startup. The filter chain executes on **every single incoming HTTP request**.

## 9. Beginner Explanation
This file is the nightclub bouncer. It has a list of rules: "Anyone can go to the lobby (login screen). Only VIPs (Admins) can go to the VIP lounge (Dashboard). Everyone else needs to show their wristband (JWT Token) at the door to get inside."

## 10. Important Concepts to Learn
* Filter Chains
* Stateless Authentication vs Session Authentication
* CORS and CSRF

---

# File: `src/main/java/com/sentinelx/config/SslConfigValidator.java`

## 1. Purpose
Ensures that if the database requires an SSL connection, the necessary certificates actually exist on the server before the app attempts to connect.

## 2. Why This File Exists
To fail fast. If the app is deployed in production but is missing the database security certificate, it's better to crash immediately on startup than to throw confusing database errors later.

## 3. Internal Breakdown
* **`@ConditionalOnProperty(name = "db.ssl.enabled", havingValue = "true")`**: This class is completely ignored unless SSL is turned on in the properties.
* **`implements InitializingBean`**: Forces the `afterPropertiesSet()` method to run immediately after Spring creates this object.
* **`validateRootCertificate()`**: Checks if `rootCertPath` exists using `Files.exists()`. Throws an `IllegalStateException` if missing.

## 8. Execution Flow
Runs exactly once, immediately upon application startup (if SSL is enabled).

## 9. Beginner Explanation
Before the app fully wakes up, it checks if it has the "keys" (certificates) needed to talk to the database securely. If the keys are missing, the app refuses to start and logs an error, rather than trying to connect and failing confusingly.

---

# File: `src/main/java/com/sentinelx/config/StartupEnvValidator.java`

## 1. Purpose
Verifies that all absolutely critical environment variables are present before the application boots up.

## 2. Why This File Exists
Just like the SSL validator, this is a "fail-fast" mechanism. It prevents the app from starting if it doesn't have a database URL, username, password, or JWT Secret.

## 3. Internal Breakdown
* **`@Profile("!test")`**: This class does **not** run during automated testing, because tests use an in-memory database and dummy secrets.
* **`implements ApplicationRunner`**: Spring boot runs this `run()` method right after the application context is loaded.
* **`REQUIRED_VARS` list**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
* **Logic**: Iterates through the list, checks `environment.getProperty()`. If any are blank, throws an `IllegalStateException`.

## 8. Execution Flow
Executes once on startup.

## 9. Beginner Explanation
Imagine trying to start a car without putting oil in the engine. This file checks the "oil levels" (environment variables). If you forgot to provide the database password, the app turns itself off immediately and tells you exactly what is missing.

---

# File: `src/main/java/com/sentinelx/config/TransactionConfig.java`

## 1. Purpose
Configures database transaction management and timeouts.

## 2. Why This File Exists
To ensure that database queries don't hang forever if the database is slow, and to provide a global timeout setting.

## 3. Internal Breakdown
* **`@EnableTransactionManagement`**: Tells Spring to look for `@Transactional` annotations on our Services.
* **`defaultTimeoutSeconds`**: Loaded from `db.transaction.defaultTimeoutSeconds` (defaults to 30 seconds).
* **`transactionManager()`**: Wraps the JPA EntityManager and applies the 30-second timeout.
* **`transactionTemplate()`**: Provides a utility for manual, programmatic transaction control (useful for complex logic where annotations aren't enough).

## 9. Beginner Explanation
A "transaction" is a group of database actions that must succeed or fail together (like transferring money: subtract from Account A, add to Account B). This file sets a rule: "If any transaction takes longer than 30 seconds, cancel it immediately so the server doesn't get stuck waiting."

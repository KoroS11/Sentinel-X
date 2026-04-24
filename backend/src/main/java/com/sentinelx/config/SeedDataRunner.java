package com.sentinelx.config;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.entity.UserStatus;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import com.sentinelx.user.service.RoleService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SeedDataRunner implements CommandLineRunner {

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final AlertRepository alertRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(String... args) {
        roleService.ensureDefaultRoles();
        log.info("Default roles verified");

        // Skip comprehensive seeding in test profile to avoid FK conflicts with test cleanup
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                log.info("Test profile active — skipping comprehensive seed data");
                return;
            }
        }

        // ── BLOCK 1: User Seeding ─────────────────────────────────────────────
        Role adminRole = roleRepository.findByName(RoleType.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
        Role analystRole = roleRepository.findByName(RoleType.ANALYST)
                .orElseThrow(() -> new IllegalStateException("ANALYST role missing"));
        Role employeeRole = roleRepository.findByName(RoleType.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));

        String seedPassword = passwordEncoder.encode("SentinelX@seed1");

        ensureUser("admin_sentinel", "admin@sentinelx.local", seedPassword, adminRole);
        ensureUser("analyst_chen", "chen@sentinelx.local", seedPassword, analystRole);
        ensureUser("analyst_morgan", "morgan@sentinelx.local", seedPassword, analystRole);
        ensureUser("analyst_patel", "patel@sentinelx.local", seedPassword, analystRole);
        ensureUser("emp_ramos", "ramos@sentinelx.local", seedPassword, employeeRole);
        ensureUser("emp_torres", "torres@sentinelx.local", seedPassword, employeeRole);
        ensureUser("emp_kim", "kim@sentinelx.local", seedPassword, employeeRole);
        ensureUser("emp_okafor", "okafor@sentinelx.local", seedPassword, employeeRole);
        ensureUser("emp_novak", "novak@sentinelx.local", seedPassword, employeeRole);
        ensureUser("emp_hassan", "hassan@sentinelx.local", seedPassword, employeeRole);

        log.info("✓ Seed users ensured: {} total in DB", userRepository.count());

        // ── BLOCK 2: Idempotency Guard + User Loading ─────────────────────────
        if (activityRepository.count() > 0) {
            log.info("Comprehensive scenario seed data already present — skipping");
            return;
        }

        User adminUser = findSeedUser("admin_sentinel");
        User analystChen = findSeedUser("analyst_chen");
        User analystMorgan = findSeedUser("analyst_morgan");
        User analystPatel = findSeedUser("analyst_patel");
        User empRamos = findSeedUser("emp_ramos");
        User empTorres = findSeedUser("emp_torres");
        User empKim = findSeedUser("emp_kim");
        User empOkafor = findSeedUser("emp_okafor");
        User empNovak = findSeedUser("emp_novak");
        User empHassan = findSeedUser("emp_hassan");

        LocalDateTime now = LocalDateTime.now();

        // ── SCENARIO 1: Brute Force Login (empRamos) score=88 OPEN/HIGH ───────
        seedScenario1(empRamos, analystChen, now);

        // ── SCENARIO 2: Privilege Escalation (empTorres) score=92 UNDER_INVESTIGATION/CRITICAL
        seedScenario2(empTorres, analystMorgan, now);

        // ── SCENARIO 3: Data Exfiltration (empKim) score=83 OPEN/HIGH ─────────
        seedScenario3(empKim, now);

        // ── SCENARIO 4: Unusual Login Location (empOkafor) score=75 OPEN/HIGH ─
        seedScenario4(empOkafor, now);

        // ── SCENARIO 5: Rapid API Calls (empNovak) score=79 UNDER_INVESTIGATION/HIGH
        seedScenario5(empNovak, analystPatel, now);

        // ── SCENARIO 6: Odd Hours Access (empHassan) score=58 ACKNOWLEDGED/MEDIUM
        seedScenario6(empHassan, analystChen, now);

        // ── SCENARIO 7: Password Reset Abuse (empRamos) score=55 RESOLVED/MEDIUM
        seedScenario7(empRamos, analystChen, now);

        // ── SCENARIO 8: Analyst Normal (analystChen) score=12 NO ALERT ────────
        seedScenario8(analystChen, now);

        // ── SCENARIO 9: Analyst Normal (analystMorgan) score=20 NO ALERT ──────
        seedScenario9(analystMorgan, now);

        // ── SCENARIO 10: Recovering User (analystPatel) scores=71→45→22 RESOLVED
        seedScenario10(analystPatel, adminUser, now);

        // ── BLOCK 3: 3 Additional OPEN Alerts ────────────────────────────────
        seedAdditionalAlerts(empTorres, empNovak, empHassan, analystMorgan, now);

        // ── BLOCK 4: Rules via JdbcTemplate ──────────────────────────────────
        seedRules();

        // ── BLOCK 5: Final Log ───────────────────────────────────────────────
        int rulesCount = 0;
        try {
            Integer r = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rules", Integer.class);
            rulesCount = r != null ? r : 0;
        } catch (Exception ex) {
            log.debug("Rules table not available for count: {}", ex.getMessage());
        }
        log.info("✓ Comprehensive seed complete — users={}, activities={}, riskScores={}, alerts={}, rules={}",
                userRepository.count(), activityRepository.count(), riskScoreRepository.count(),
                alertRepository.count(), rulesCount);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCENARIO METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private void seedScenario1(User empRamos, User analystChen, LocalDateTime now) {
        String meta = "{\"ip\":\"203.0.113.42\",\"device\":\"Chrome\",\"location\":\"Unknown\"}";
        for (int i = 0; i < 12; i++) {
            seedActivity(empRamos, "LOGIN_FAILED", "AUTH",
                    String.valueOf(empRamos.getId()), meta, now.minusSeconds(600 - (i * 49L)));
        }

        seedRiskScore(empRamos, 42, "Elevated failed login count", now.minusSeconds(172800));
        RiskScore primary = seedRiskScore(empRamos, 88,
                "Brute force login — 12 failed attempts in 10 minutes", now);

        Alert alert = seedAlert(empRamos, primary, AlertSeverity.HIGH, AlertStatus.OPEN,
                "Brute force attack detected — 12 failed logins in 10 minutes",
                analystChen, now, now);

        seedAuditLog("ALERT_GENERATED", empRamos.getId(),
                "Rule R001 triggered — brute force login pattern", "R001");
        seedAuditLog("ALERT_ASSIGNED", analystChen.getId(),
                "Alert assigned to analyst_chen for investigation", null);

        log.info("✓ Seeded scenario 1: Brute Force Login");
    }

    private void seedScenario2(User empTorres, User analystMorgan, LocalDateTime now) {
        String meta = "{\"ip\":\"10.20.30.40\",\"device\":\"curl\",\"location\":\"Internal\"}";
        for (int i = 0; i < 3; i++) {
            seedActivity(empTorres, "ROLE_CHANGE_ATTEMPT", "USER",
                    String.valueOf(empTorres.getId()), meta, now.minusSeconds(3600 - (i * 100L)));
        }
        for (int i = 0; i < 5; i++) {
            seedActivity(empTorres, "ADMIN_PANEL_ACCESS", "USER",
                    String.valueOf(empTorres.getId()), meta, now.minusSeconds(3300 - (i * 80L)));
        }

        seedRiskScore(empTorres, 25, "Normal baseline activity", now.minusSeconds(432000));
        RiskScore primary = seedRiskScore(empTorres, 92,
                "Privilege escalation — EMPLOYEE accessing ADMIN endpoints", now.minusSeconds(2400));

        seedAlert(empTorres, primary, AlertSeverity.CRITICAL, AlertStatus.UNDER_INVESTIGATION,
                "Privilege escalation attempt — EMPLOYEE accessing admin endpoints",
                analystMorgan, now.minusSeconds(2400), now.minusSeconds(2400));

        seedAuditLog("ALERT_GENERATED", empTorres.getId(),
                "Rule R004 triggered — privilege escalation attempt", "R004");
        seedAuditLog("INVESTIGATION_STARTED", analystMorgan.getId(),
                "Analyst morgan opened investigation", null);

        log.info("✓ Seeded scenario 2: Privilege Escalation Attempt");
    }

    private void seedScenario3(User empKim, LocalDateTime now) {
        String meta = "{\"ip\":\"192.168.5.99\",\"device\":\"API Client\",\"location\":\"Internal\"}";
        for (int i = 0; i < 20; i++) {
            seedActivity(empKim, "FILE_ACCESS", "USER",
                    String.valueOf(empKim.getId()), meta, now.minusSeconds(14400 - (i * 720L)));
        }

        seedRiskScore(empKim, 38, "Moderate API activity", now.minusSeconds(259200));
        RiskScore primary = seedRiskScore(empKim, 83,
                "Data exfiltration pattern — 20 file accesses in 4 hours via API client",
                now.minusSeconds(600));

        seedAlert(empKim, primary, AlertSeverity.HIGH, AlertStatus.OPEN,
                "Possible data exfiltration — 20 file accesses in 4 hours via API client",
                null, now.minusSeconds(600), now.minusSeconds(600));

        seedAuditLog("ALERT_GENERATED", empKim.getId(),
                "Rule R005 triggered — bulk file access via non-browser client", "R005");

        log.info("✓ Seeded scenario 3: Data Exfiltration Pattern");
    }

    private void seedScenario4(User empOkafor, LocalDateTime now) {
        String meta = "{\"ip\":\"198.51.100.77\",\"device\":\"Firefox\",\"location\":\"Moscow\"}";
        seedActivity(empOkafor, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(empOkafor.getId()), meta, now.minusSeconds(10800));
        seedActivity(empOkafor, "FILE_ACCESS", "USER",
                String.valueOf(empOkafor.getId()), meta, now.minusSeconds(10200));

        seedRiskScore(empOkafor, 18, "Normal activity — consistent Pune location",
                now.minusSeconds(604800));
        RiskScore primary = seedRiskScore(empOkafor, 75,
                "Login and activity from unusual location: Moscow", now.minusSeconds(10200));

        seedAlert(empOkafor, primary, AlertSeverity.HIGH, AlertStatus.OPEN,
                "Login from unusual location: Moscow (IP 198.51.100.77)",
                null, now.minusSeconds(10200), now.minusSeconds(10200));

        seedAuditLog("ALERT_GENERATED", empOkafor.getId(),
                "Rule R002 triggered — login from unrecognised geographic location", "R002");

        log.info("✓ Seeded scenario 4: Unusual Login Location");
    }

    private void seedScenario5(User empNovak, User analystPatel, LocalDateTime now) {
        String meta = "{\"ip\":\"172.16.0.99\",\"device\":\"Postman\",\"location\":\"Internal\"}";
        for (int i = 0; i < 25; i++) {
            seedActivity(empNovak, "API_CALL", "AUTH",
                    String.valueOf(empNovak.getId()), meta, now.minusSeconds(90 - (i * 3L)));
        }

        RiskScore primary = seedRiskScore(empNovak, 79,
                "API rate anomaly — 25 calls in under 90 seconds", now);

        seedAlert(empNovak, primary, AlertSeverity.HIGH, AlertStatus.UNDER_INVESTIGATION,
                "API rate anomaly — 25 API calls in 90 seconds from Postman client",
                analystPatel, now, now);

        seedAuditLog("ALERT_GENERATED", empNovak.getId(),
                "Rule R003 triggered — API call rate anomaly", "R003");
        seedAuditLog("ALERT_ASSIGNED", analystPatel.getId(),
                "Assigned to analyst_patel for review", null);

        log.info("✓ Seeded scenario 5: Rapid API Calls");
    }

    private void seedScenario6(User empHassan, User analystChen, LocalDateTime now) {
        String meta = "{\"ip\":\"10.0.0.55\",\"device\":\"Chrome\",\"location\":\"Internal\"}";
        int[][] times = {{1, 45}, {2, 30}, {3, 15}, {3, 50}, {4, 20}};
        for (int[] t : times) {
            LocalDateTime ts = now.withHour(t[0]).withMinute(t[1]).withSecond(0).withNano(0);
            seedActivity(empHassan, "FILE_ACCESS", "USER",
                    String.valueOf(empHassan.getId()), meta, ts);
        }

        LocalDateTime riskTs = now.withHour(4).withMinute(25).withSecond(0).withNano(0);
        RiskScore primary = seedRiskScore(empHassan, 58,
                "Repeated file access during off-hours (1–4 AM)", riskTs);

        seedAlert(empHassan, primary, AlertSeverity.MEDIUM, AlertStatus.ACKNOWLEDGED,
                "Off-hours file access — 5 events between 1 AM and 4 AM",
                analystChen, now, now);

        seedAuditLog("ALERT_ACKNOWLEDGED", analystChen.getId(),
                "Analyst acknowledged — user confirmed working late shift", null);

        log.info("✓ Seeded scenario 6: Odd Hours Access");
    }

    private void seedScenario7(User empRamos, User analystChen, LocalDateTime now) {
        String meta = "{\"ip\":\"203.0.113.42\",\"device\":\"Chrome\",\"location\":\"Unknown\"}";
        for (int i = 0; i < 4; i++) {
            seedActivity(empRamos, "PASSWORD_RESET_REQUEST", "AUTH",
                    String.valueOf(empRamos.getId()), meta, now.minusSeconds(86400 + (i * 900L)));
        }

        RiskScore rs = seedRiskScore(empRamos, 55,
                "4 password reset requests within 1 hour from same IP", now.minusSeconds(82800));

        seedAlert(empRamos, rs, AlertSeverity.MEDIUM, AlertStatus.RESOLVED,
                "Multiple password reset requests — 4 in 1 hour from same IP",
                analystChen, now.minusSeconds(82800), now.minusSeconds(79200));

        seedAuditLog("ALERT_RESOLVED", analystChen.getId(),
                "User confirmed locked out — legitimate reset requests. Resolved.", null);

        log.info("✓ Seeded scenario 7: Repeated Password Reset Attempts");
    }

    private void seedScenario8(User analystChen, LocalDateTime now) {
        String meta = "{\"ip\":\"192.168.1.20\",\"device\":\"Chrome\",\"location\":\"Pune\"}";
        long[] offsets = {432000, 428400, 345600, 342000, 259200, 255600, 172800, 86400};
        for (long offset : offsets) {
            seedActivity(analystChen, "LOGIN_SUCCESS", "AUTH",
                    String.valueOf(analystChen.getId()), meta, now.minusSeconds(offset));
        }

        seedRiskScore(analystChen, 12,
                "Normal analyst activity — consistent login pattern", now.minusSeconds(86400));

        log.info("✓ Seeded scenario 8: Analyst Normal Activity (analystChen)");
    }

    private void seedScenario9(User analystMorgan, LocalDateTime now) {
        String meta = "{\"ip\":\"192.168.1.21\",\"device\":\"Firefox\",\"location\":\"Pune\"}";
        seedActivity(analystMorgan, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(259200));
        seedActivity(analystMorgan, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(255600));
        seedActivity(analystMorgan, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(172800));
        seedActivity(analystMorgan, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(169200));
        seedActivity(analystMorgan, "FILE_ACCESS", "USER",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(86400));
        seedActivity(analystMorgan, "FILE_ACCESS", "USER",
                String.valueOf(analystMorgan.getId()), meta, now.minusSeconds(82800));

        seedRiskScore(analystMorgan, 20,
                "Normal analyst activity", now.minusSeconds(82800));

        log.info("✓ Seeded scenario 9: Analyst Normal Activity (analystMorgan)");
    }

    private void seedScenario10(User analystPatel, User adminUser, LocalDateTime now) {
        String metaUnusual = "{\"ip\":\"45.77.100.22\",\"device\":\"Unknown\",\"location\":\"Singapore\"}";
        String metaNormal = "{\"ip\":\"192.168.1.22\",\"device\":\"Chrome\",\"location\":\"Pune\"}";

        seedActivity(analystPatel, "LOGIN_FAILED", "AUTH",
                String.valueOf(analystPatel.getId()), metaUnusual, now.minusSeconds(1814400));
        seedActivity(analystPatel, "LOGIN_FAILED", "AUTH",
                String.valueOf(analystPatel.getId()), metaUnusual, now.minusSeconds(1810800));
        seedActivity(analystPatel, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystPatel.getId()), metaNormal, now.minusSeconds(1209600));
        seedActivity(analystPatel, "FILE_ACCESS", "USER",
                String.valueOf(analystPatel.getId()), metaNormal, now.minusSeconds(604800));
        seedActivity(analystPatel, "LOGIN_SUCCESS", "AUTH",
                String.valueOf(analystPatel.getId()), metaNormal, now.minusSeconds(86400));

        RiskScore rs1 = seedRiskScore(analystPatel, 71,
                "Unusual login from Singapore", now.minusSeconds(1810000));
        seedRiskScore(analystPatel, 45,
                "Activity normalising — continued monitoring", now.minusSeconds(1209000));
        seedRiskScore(analystPatel, 22,
                "Risk resolved — confirmed business travel", now.minusSeconds(604000));

        seedAlert(analystPatel, rs1, AlertSeverity.MEDIUM, AlertStatus.RESOLVED,
                "Login from Singapore — confirmed legitimate business travel",
                adminUser, now.minusSeconds(1810000), now.minusSeconds(604000));

        seedAuditLog("ALERT_RESOLVED", adminUser.getId(),
                "Confirmed travel — user submitted travel approval. Score trending down.", null);

        log.info("✓ Seeded scenario 10: Recovering User — Trending Down (analystPatel)");
    }

    private void seedAdditionalAlerts(User empTorres, User empNovak, User empHassan,
                                      User analystMorgan, LocalDateTime now) {
        // Alert A — empTorres second incident: score=70 → latest for empTorres
        RiskScore rsA = seedRiskScore(empTorres, 70,
                "Renewed admin endpoint probing after initial block", now.minusSeconds(1800));
        seedAlert(empTorres, rsA, AlertSeverity.HIGH, AlertStatus.OPEN,
                "Renewed admin endpoint probing — 3 attempts after initial block",
                analystMorgan, now.minusSeconds(1800), now.minusSeconds(1800));
        seedAuditLog("ALERT_GENERATED", empTorres.getId(),
                "Rule R004 re-triggered after block bypass attempt", "R004");

        // Alert B — empNovak second incident: score=72 → latest for empNovak
        RiskScore rsB = seedRiskScore(empNovak, 72,
                "API calls at 3 AM from internal network", now.minusSeconds(36000));
        seedAlert(empNovak, rsB, AlertSeverity.MEDIUM, AlertStatus.OPEN,
                "API calls during off-hours (3 AM) — 8 requests in 5 minutes",
                null, now.minusSeconds(36000), now.minusSeconds(36000));

        // Alert C — empHassan second incident: score=65 → latest for empHassan (65 >= 60)
        RiskScore rsC = seedRiskScore(empHassan, 65,
                "File access volume 3x above 30-day baseline", now.minusSeconds(7200));
        seedAlert(empHassan, rsC, AlertSeverity.MEDIUM, AlertStatus.OPEN,
                "File access volume spike — 3x above 30-day baseline",
                null, now.minusSeconds(7200), now.minusSeconds(7200));

        log.info("✓ Seeded 3 additional OPEN alerts (total OPEN = 6)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BLOCK 4: Rules
    // ══════════════════════════════════════════════════════════════════════════

    private void seedRules() {
        try {
            Integer existingRules = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rules", Integer.class);
            if (existingRules != null && existingRules > 0) {
                log.info("Rules already seeded — skipping");
                return;
            }

            String sql = "INSERT INTO rules (rule_id, name, condition_text, risk_score, severity, alert_message, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, "R001", "Brute Force Login Detection",
                    "failed_logins > 5 within 10 minutes", 80, "HIGH",
                    "Brute force attack detected", true);
            jdbcTemplate.update(sql, "R002", "Unusual Login Location",
                    "login_location NOT IN known_locations", 70, "HIGH",
                    "Login from unusual geographic location", true);
            jdbcTemplate.update(sql, "R003", "API Rate Anomaly",
                    "api_calls > 15 within 2 minutes", 75, "HIGH",
                    "API call rate anomaly detected", true);
            jdbcTemplate.update(sql, "R004", "Privilege Escalation Attempt",
                    "role_change_attempt OR admin_access BY EMPLOYEE role", 90, "CRITICAL",
                    "Privilege escalation attempt detected", true);
            jdbcTemplate.update(sql, "R005", "Bulk File Access Pattern",
                    "file_access_count > 15 within 4 hours via non-browser client", 80, "HIGH",
                    "Bulk file access pattern detected", true);
            jdbcTemplate.update(sql, "R006", "Off-Hours System Access",
                    "any_access BETWEEN 01:00 AND 05:00 local time", 55, "MEDIUM",
                    "System access during off-hours", true);

            log.info("✓ Seeded 6 detection rules");
        } catch (Exception ex) {
            log.warn("Rules table not available — skipping rule seeding: {}", ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private void ensureUser(String username, String email, String encodedPassword, Role role) {
        if (!userRepository.existsByEmail(email)) {
            User u = new User();
            u.setUsername(username);
            u.setEmail(email);
            u.setPasswordHash(encodedPassword);
            u.setRole(role);
            u.setActive(true);
            u.setEmailVerified(true);
            u.setStatus(UserStatus.ACTIVE);
            userRepository.save(u);
            log.debug("Created seed user: {}", username);
        }
    }

    private User findSeedUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Seed user not found: " + username));
    }

    private void seedActivity(User user, String action, String entityType,
                              String entityId, String metadata, LocalDateTime createdAt) {
        Activity a = new Activity();
        a.setUser(user);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setMetadata(metadata);
        a.setCreatedAt(createdAt);
        activityRepository.save(a);
    }

    private RiskScore seedRiskScore(User user, int score, String reason,
                                    LocalDateTime calculatedAt) {
        RiskScore rs = new RiskScore();
        rs.setUser(user);
        rs.setScore(score);
        rs.setReason(reason);
        rs.setCalculatedAt(calculatedAt);
        return riskScoreRepository.save(rs);
    }

    private Alert seedAlert(User user, RiskScore riskScore, AlertSeverity severity,
                            AlertStatus status, String message, User assignedTo,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setRiskScore(riskScore);
        alert.setSeverity(severity);
        alert.setStatus(status);
        alert.setMessage(message);
        alert.setAssignedTo(assignedTo);
        alert.setCreatedAt(createdAt);
        alert.setUpdatedAt(updatedAt);
        return alertRepository.save(alert);
    }

    private void seedAuditLog(String action, Long userId, String details,
                              String triggeredByRule) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (action, user_id, details, triggered_by_rule) VALUES (?, ?, ?, ?)",
                    action, userId, details, triggeredByRule);
        } catch (Exception ex) {
            log.debug("Audit log table not available — skipping: {}", ex.getMessage());
        }
    }
}

-- ── Rules table ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rules (
    id              BIGSERIAL    PRIMARY KEY,
    rule_id         VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    condition_text  VARCHAR(500) NOT NULL,
    risk_score      INTEGER      NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    severity        VARCHAR(20)  NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    alert_message   VARCHAR(300) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Audit logs table ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id                  BIGSERIAL    PRIMARY KEY,
    action              VARCHAR(100) NOT NULL,
    user_id             BIGINT,
    details             VARCHAR(500),
    triggered_by_rule   VARCHAR(20),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE SET NULL
);

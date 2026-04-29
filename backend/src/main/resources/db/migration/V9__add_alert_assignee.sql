ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS assigned_to_user_id BIGINT NULL;

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE alerts
    DROP CONSTRAINT IF EXISTS fk_alerts_assigned_to_user;

ALTER TABLE alerts
    ADD CONSTRAINT fk_alerts_assigned_to_user
    FOREIGN KEY (assigned_to_user_id) REFERENCES users (id);

CREATE INDEX IF NOT EXISTS idx_alerts_assigned_to_user_id ON alerts (assigned_to_user_id);
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS assigned_to_user_id BIGINT NULL;

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_alerts_assigned_to_user'
    ) THEN
        ALTER TABLE alerts
            ADD CONSTRAINT fk_alerts_assigned_to_user
            FOREIGN KEY (assigned_to_user_id) REFERENCES users (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_alerts_assigned_to_user_id ON alerts (assigned_to_user_id);
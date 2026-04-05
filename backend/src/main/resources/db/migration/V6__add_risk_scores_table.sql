CREATE TABLE risk_scores (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    score INTEGER NOT NULL,
    reason VARCHAR(255) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_scores_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_risk_scores_user_id ON risk_scores (user_id);
CREATE INDEX idx_risk_scores_calculated_at ON risk_scores (calculated_at);

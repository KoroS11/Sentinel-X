CREATE TABLE activities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    metadata TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activities_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_activities_user_id ON activities (user_id);
CREATE INDEX idx_activities_entity_type ON activities (entity_type);
CREATE INDEX idx_activities_created_at ON activities (created_at);

-- Supports getActivityCountByUser time-range group-by
CREATE INDEX IF NOT EXISTS idx_activities_user_created
    ON activities (user_id, created_at);

-- Supports getAlertCountsByStatus grouped filtering
CREATE INDEX IF NOT EXISTS idx_alerts_status_created
    ON alerts (status, created_at);

-- Supports getAlertTrendByDay date-bucket aggregation
CREATE INDEX IF NOT EXISTS idx_alerts_created
    ON alerts (created_at);

-- Supports getTopRiskyUsers latest-per-user subquery
CREATE INDEX IF NOT EXISTS idx_risk_scores_user_created
    ON risk_scores (user_id, calculated_at DESC);

-- Supports getTopRiskyUsers final ordering by score
CREATE INDEX IF NOT EXISTS idx_risk_scores_score_desc
    ON risk_scores (score DESC);

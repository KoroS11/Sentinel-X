package com.sentinelx.risk.strategy;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BasicRiskScoringStrategy implements RiskScoringStrategy {

    private static final int INITIAL_SCORE = 0;
    private static final int HIGH_FREQUENCY_ACTIVITY_THRESHOLD = 10;
    private static final int HIGH_FREQUENCY_SCORE_BOOST = 40;
    private static final int OFF_HOURS_START_HOUR = 22;
    private static final int OFF_HOURS_END_HOUR = 6;
    private static final int OFF_HOURS_ACTIVITY_SCORE_BOOST = 4;
    private static final int MAX_SCORE = 100;

    @Override
    public int calculateScore(User user, List<Activity> recentActivities) {
        int score = INITIAL_SCORE;

        if (recentActivities.size() >= HIGH_FREQUENCY_ACTIVITY_THRESHOLD) {
            score += HIGH_FREQUENCY_SCORE_BOOST;
        }

        long offHoursActivityCount = recentActivities.stream()
            .map(Activity::getCreatedAt)
            .filter(this::isOffHours)
            .count();

        score += (int) offHoursActivityCount * OFF_HOURS_ACTIVITY_SCORE_BOOST;

        return Math.min(score, MAX_SCORE);
    }

    private boolean isOffHours(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        return hour >= OFF_HOURS_START_HOUR || hour < OFF_HOURS_END_HOUR;
    }
}

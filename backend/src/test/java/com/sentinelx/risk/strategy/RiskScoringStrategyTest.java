package com.sentinelx.risk.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.user.entity.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskScoringStrategyTest {

    private final BasicRiskScoringStrategy strategy = new BasicRiskScoringStrategy();

    @Test
    void basicRiskScoringStrategyReturnsZeroForUserWithNoActivities() {
        int score = strategy.calculateScore(new User(), List.of());

        assertEquals(0, score);
    }

    @Test
    void basicRiskScoringStrategyIncreasesScoreForHighFrequencyActions() {
        List<Activity> highFrequencyActivities = new ArrayList<>();
        LocalDateTime businessHours = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);

        for (int index = 0; index < 12; index++) {
            Activity activity = new Activity();
            activity.setCreatedAt(businessHours.plusMinutes(index));
            highFrequencyActivities.add(activity);
        }

        int score = strategy.calculateScore(new User(), highFrequencyActivities);

        assertTrue(score > 0);
    }
}

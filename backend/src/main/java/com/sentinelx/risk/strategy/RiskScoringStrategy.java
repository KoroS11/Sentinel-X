package com.sentinelx.risk.strategy;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.user.entity.User;
import java.util.List;

public interface RiskScoringStrategy {
    int calculateScore(User user, List<Activity> recentActivities);
}

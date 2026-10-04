package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

/** An account-adjusted planning stage, not an access lock, CA tier or success guarantee. */
public final class ProgressionDifficulty
{
    private ProgressionDifficulty() {}

    public static int stage(CombatAchievement task, PlayerProfile profile)
    {
        int gap = 0, totalGap = 0; boolean unreadable = false;
        for (Map.Entry<String, Integer> recommendation : Readiness.levels(task, profile).entrySet())
        {
            Integer current = profile.skills().get(recommendation.getKey());
            if (current == null) { unreadable = true; continue; }
            int shortfall = Math.max(0, recommendation.getValue() - current);
            gap = Math.max(gap, shortfall); totalGap += shortfall;
        }
        int readiness = Math.min(3, Math.max((gap + 9) / 10, (totalGap + 24) / 25));
        boolean missing = task.recommendedGear().stream()
            .anyMatch(capability -> profile.gear().capability(capability) == GearProfile.CapabilityState.MISSING);
        Integer observed = profile.killCounts().get(task.activity());
        int experience = observed == null ? 0 : observed >= 150 ? 2 : observed >= 25 ? 1 : 0;
        // Unreadable levels add one uncertainty stage without filling in a fictitious skill value.
        // Unknown gear is not declared missing. Unknown KC earns no claimed experience discount.
        return Math.max(1, Math.max(task.activityDifficulty(), task.difficulty())
            + readiness + (unreadable ? 1 : 0) + (missing ? 1 : 0) - experience);
    }

    public static int stage(BossSession session, PlayerProfile profile)
    { return session.tasks().stream().mapToInt(assessment -> stage(assessment.task(), profile)).max().orElse(1); }
}

package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

/** Choose an applicable combat style; alternatives must not become all-style requirements. */
final class Readiness
{
    static Map<String, Integer> levels(CombatAchievement task, PlayerProfile profile)
    {
        Map<String, Integer> levels = new TreeMap<>(task.recommendedSkills());
        for (Map<String, Integer> alternatives : task.recommendedAlternatives())
        {
            String best = null; int gap = Integer.MAX_VALUE;
            for (String skill : new TreeSet<>(alternatives.keySet()))
            {
                Integer actual = profile.skills().get(skill);
                int candidate = actual == null ? Integer.MAX_VALUE : Math.max(0, alternatives.get(skill) - actual);
                if (best == null || candidate < gap) { best = skill; gap = candidate; }
            }
            if (best != null) levels.merge(best, alternatives.get(best), Math::max);
        }
        return levels;
    }
}

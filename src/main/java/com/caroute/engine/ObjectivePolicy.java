package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

/** Explicit planning priorities. Policy units are never elapsed minutes or GP. */
final class ObjectivePolicy
{
    static double cost(RouteObjective objective,double physical,double baseline,double setup,double coordination,double acquisition,List<TaskAssessment> tasks,PlayerProfile profile)
    {
        switch(objective)
        {
            case EASIEST_PROGRESSION:
                int hardest=tasks.stream().mapToInt(a->ProgressionDifficulty.stage(a.task(),profile)).max().orElse(1);
                double retries=tasks.stream().mapToDouble(a->a.components().getOrDefault("retryWork",0.0)).sum();
                double stats=tasks.stream().mapToDouble(a->a.components().getOrDefault("statReadiness",0.0)).sum();
                return baseline-physical+physical*0.25+hardest*hardest*4+retries*2+stats*2;
            case MINIMUM_FRICTION:return baseline+12+setup*3+coordination*2;
            case IRONMAN_EFFICIENT:
                Set<String> gear=new TreeSet<>();tasks.forEach(a->gear.addAll(a.task().recommendedGear()));double preparation=0;
                for(String capability:gear)
                    if(profile.gear().capability(capability)==GearProfile.CapabilityState.MISSING)preparation+=24;
                    else if(profile.gear().capability(capability)==GearProfile.CapabilityState.UNKNOWN)preparation+=16;
                long rng=tasks.stream().filter(a->a.task().flags().contains(TaskFlag.RNG)).count();
                return baseline+acquisition*2+preparation+rng*10;
            default:return baseline;
        }
    }
    static String explanation(RouteObjective objective)
    {
        switch(objective)
        {
            case EASIEST_PROGRESSION:return "Prioritizes lower difficulty before point efficiency, with harder tasks later in the route";
            case MINIMUM_FRICTION:return "Prioritizes fewer trips, less setup and less group coordination";
            case IRONMAN_EFFICIENT:return "Prioritizes preparation you can confirm, fewer missing gear upgrades and less reliance on RNG";
            case MAXIMUM_POINTS_PER_HOUR:return "Maximizes points within your active-time budget; favors shorter routes when points are equal";
            case BOSS_COMPLETION:return "Boss completion scope: every remaining task for the selected activity; this does not claim tier completion";
            default:return "";
        }
    }
    static double taskOrderingCost(TaskAssessment task,PlayerProfile profile,RoutingPreferences prefs)
    {
        return cost(prefs.objective(),task.estimatedMinutes(),task.planningCost(),task.components().getOrDefault("setup",0.0),task.components().getOrDefault("groupCoordination",0.0),task.components().getOrDefault("acquisitionActive",0.0),Collections.singletonList(task),profile);
    }
}

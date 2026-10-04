package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualPreparationTest
{
    private CombatAchievement task(int id, String activity, Set<String> gear)
    {
        return CombatAchievement.builder(id, "Task " + id, activity, AchievementTier.EASY)
            .recommendedGear(gear).requirementsKnown(true).timingKnown(true).secondsPerKill(60).setupMinutes(0).build();
    }
    private PlayerProfile profile(GearProfile gear)
    { return PlayerProfile.builder().loaded(true).catalogueComplete(true).gear(gear).thresholds(Map.of(AchievementTier.EASY, 1)).build(); }

    @Test public void reportedActiveTimeCountsEvenWhenOwnershipIsUnknownWithoutInventingOtherCosts()
    {
        GearProfile gear = GearProfile.unknown().withActivePreparationEstimates(Map.of("upgrade", 12.5));
        RoutePlan route = new RouteEngine().plan(List.of(task(1, "Boss", Set.of("upgrade"))), profile(gear), RoutingPreferences.builder().build());
        assertEquals(13.5, route.estimatedMinutes(), 1e-6);
        assertEquals(Confidence.LOW, route.confidence());
        assertTrue(route.assessments().get(0).reasons().stream().anyMatch(reason -> reason.contains("waiting time unknown") && reason.contains("resource cost unknown")));
        assertFalse(AcquisitionCost.manual(12.5).hasWaitingEstimate());
        assertFalse(AcquisitionCost.manual(12.5).hasResourceEstimate());
        assertEquals(GearProfile.CapabilityState.UNKNOWN, gear.capability("upgrade"));
    }

    @Test public void confirmedOwnershipRemovesReportedAcquisitionAndEstimatesAreImmutable()
    {
        Map<String, Double> input = new HashMap<>(); input.put("upgrade", 100.0);
        GearProfile gear = GearProfile.of(Set.of(), Set.of(), Set.of("upgrade"), false).withActivePreparationEstimates(input);
        input.put("upgrade", 1.0);
        assertEquals(100.0, gear.activePreparationEstimates().get("upgrade"), 0);
        RoutePlan route = new RouteEngine().plan(List.of(task(1, "Boss", Set.of("upgrade"))), profile(gear), RoutingPreferences.builder().build());
        assertEquals(1.0, route.estimatedMinutes(), 0);
        try { gear.activePreparationEstimates().put("other", 1.0); fail("Published estimates must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void accountLocalPreparationChangesTheChoiceWithoutAnAssumedShopOrDropRate()
    {
        List<CombatAchievement> tasks = List.of(task(1, "Needs upgrade", Set.of("upgrade")),
            CombatAchievement.builder(2, "Alternative", "No upgrade", AchievementTier.EASY)
                .requirementsKnown(true).timingKnown(true).secondsPerKill(900).setupMinutes(0).build());
        GearProfile base = GearProfile.of(Set.of(), Set.of(), Set.of(), Set.of("upgrade"), false);
        RoutingPreferences prefs = RoutingPreferences.builder().build();
        RouteEngine engine = new RouteEngine();
        assertEquals("Needs upgrade", engine.plan(tasks, profile(base.withActivePreparationEstimates(Map.of("upgrade", 1.0))), prefs).stops().get(0).activity());
        assertEquals("No upgrade", engine.plan(tasks, profile(base.withActivePreparationEstimates(Map.of("upgrade", 50.0))), prefs).stops().get(0).activity());
    }
}

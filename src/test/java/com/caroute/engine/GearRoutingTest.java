package com.caroute.engine;

import com.caroute.data.GearCapabilityRepository;
import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GearRoutingTest
{
    private final GearCapabilityRepository repository = GearCapabilityRepository.loadBundled();
    private CombatAchievement task(Requirement requirement,Set<String> recommended)
    {
        return CombatAchievement.builder(0,"Fixture","Zulrah",AchievementTier.EASY).requirement(requirement)
            .recommendedGear(recommended).requirementsKnown(true).timingKnown(true).secondsPerKill(60).setupMinutes(0).build();
    }
    private PlayerProfile profile(GearProfile gear)
    {
        return PlayerProfile.builder().loaded(true).catalogueComplete(true).gear(gear)
            .skills(Map.of("Ranged",99,"Magic",99)).thresholds(Map.of(AchievementTier.EASY,1)).build();
    }
    private TaskAssessment assess(CombatAchievement task,GearProfile gear)
    {
        return new RouteEngine().plan(List.of(task),profile(gear),RoutingPreferences.builder().target(AchievementTier.EASY).build()).assessments().get(0);
    }
    @Test public void equalStatsWithBowfaCrystalReducePlanningCostWithoutInventingDps()
    {
        CombatAchievement task=task(Requirement.all(),Set.of("ranged-accuracy-kit"));
        GearProfile basic=repository.classify(Set.of(861),Set.of(),false);
        GearProfile bowfa=repository.classify(Set.of(25867,23971,23975,23979),Set.of(),false);
        TaskAssessment basicCost=assess(task,basic),bowfaCost=assess(task,bowfa);
        assertTrue(basicCost.planningCost()>bowfaCost.planningCost());
        assertEquals(basicCost.estimatedMinutes(),bowfaCost.estimatedMinutes(),0.001);
        assertEquals(Confidence.LOW,basicCost.confidence()); assertEquals(Confidence.HIGH,bowfaCost.confidence());
        assertEquals(0,bowfaCost.components().get("gearReadiness"),0.001);
        assertTrue(basicCost.reasons().stream().anyMatch(reason->reason.contains("not elapsed time")));
    }
    @Test public void softKnownMissingGearCostsMoreAndNeverBecomesAnAccessLock()
    {
        CombatAchievement task=task(Requirement.all(),Set.of("ratbane-weapon"));
        GearProfile missing=repository.classify(Set.of(),Set.of(),false,Map.of("ratbane-weapon",false));
        TaskAssessment assessment=assess(task,missing);
        assertEquals(EligibilityState.AVAILABLE_BUT_UNDERGEARED,assessment.eligibility().state());
        assertTrue(assessment.eligibility().recommendedNow()); assertTrue(assessment.components().get("gearReadiness")>0);
        assertTrue(assessment.reasons().stream().anyMatch(reason->reason.contains("acquisition time is excluded")));
    }
    @Test public void minimumGearAlternativesDistinguishUnobservedFromExplicitlyMissing()
    {
        CombatAchievement task=task(Requirement.any(Requirement.gear("bowfa-crystal"),Requirement.gear("twisted-bow")),Set.of());
        assertEquals(EligibilityState.UNKNOWN_REQUIREMENT,assess(task,GearProfile.unknown()).eligibility().state());
        GearProfile missing=repository.classify(Set.of(),Set.of(),false,Map.of("bowfa-crystal",false,"twisted-bow",false));
        assertEquals(EligibilityState.GEAR_LOCKED,assess(task,missing).eligibility().state());
        assertFalse(assess(task,missing).eligibility().recommendedNow());
        GearProfile substitute=repository.classify(Set.of(),Set.of(20997),false,Map.of("bowfa-crystal",false));
        assertEquals(EligibilityState.AVAILABLE,assess(task,substitute).eligibility().state());
    }
    @Test public void explicitMissingUsesAcquisitionProviderEvenWhenBankCoverageIsPartial()
    {
        CombatAchievement task=task(Requirement.all(),Set.of("ratbane-weapon"));
        GearProfile gear=repository.classify(Set.of(),Set.of(),false,Map.of("ratbane-weapon",false));
        RoutePlan plan=new RouteEngine((type,cap,profile)->AcquisitionCost.known(12,0,0,Confidence.HIGH)).plan(List.of(task),profile(gear),RoutingPreferences.builder().target(AchievementTier.EASY).build());
        assertEquals(13,plan.estimatedMinutes(),0.001);
        assertEquals(12,plan.assessments().get(0).components().get("acquisitionActive"),0.001);
    }

    @Test public void oneGearPreparationPenaltyServesTheWholeBossBundle()
    {
        List<CombatAchievement> tasks = new ArrayList<>();
        for (int id = 0; id < 2; id++) tasks.add(CombatAchievement.builder(id, "Task " + id, "Scurrius", AchievementTier.EASY)
            .recommendedGear(Set.of("ratbane-weapon")).requirementsKnown(true).timingKnown(true).secondsPerKill(60).setupMinutes(0).build());
        GearProfile missing = repository.classify(Set.of(), Set.of(), false, Map.of("ratbane-weapon", false));
        PlayerProfile profile = PlayerProfile.builder().loaded(true).catalogueComplete(true).gear(missing)
            .thresholds(Map.of(AchievementTier.EASY, 2)).build();
        RoutePlan plan = new RouteEngine((type, cap, gear) -> AcquisitionCost.known(0, 0, 0, Confidence.HIGH))
            .plan(tasks, profile, RoutingPreferences.builder().build());
        assertEquals(2, plan.projectedPoints());
        assertEquals(2, plan.estimatedMinutes(), 0.001);
        assertEquals(10, plan.stops().get(0).planningCost(), 0.001);
        assertTrue(plan.stops().get(0).reasons().stream().anyMatch(reason -> reason.contains("preparation is shared")));
    }

    @Test public void equalStatsChangeNextStopWhenSupportedGearIsOwned()
    {
        CombatAchievement ranged = task(Requirement.all(), Set.of("ranged-accuracy-kit"));
        CombatAchievement other = CombatAchievement.builder(1, "Other task", "Other boss", AchievementTier.EASY)
            .requirementsKnown(true).timingKnown(true).secondsPerKill(180).setupMinutes(0).build();
        RoutingPreferences preferences = RoutingPreferences.builder().build();
        RouteEngine engine = new RouteEngine();
        GearProfile basic = repository.classify(Set.of(861), Set.of(), false);
        GearProfile bowfa = repository.classify(Set.of(25867, 23971, 23975, 23979), Set.of(), false);
        assertEquals("Other boss", engine.plan(List.of(ranged, other), profile(basic), preferences).stops().get(0).activity());
        RoutePlan upgraded = engine.plan(List.of(ranged, other), profile(bowfa), preferences);
        assertEquals("Zulrah", upgraded.stops().get(0).activity());
        assertTrue(upgraded.stops().get(0).reasons().stream().anyMatch(reason -> reason.startsWith("Supported gear confirmed:")));
    }
}

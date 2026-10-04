package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteObjectiveTest
{
    private CombatAchievement task(int id,String activity,double minutes,int difficulty)
    {return CombatAchievement.builder(id,"Task "+id,activity,AchievementTier.EASY).secondsPerKill(minutes*60).setupMinutes(0).difficulty(difficulty).requirementsKnown(true).timingKnown(true).build();}
    private PlayerProfile profile(int target)
    {return PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,target,AchievementTier.GRANDMASTER,target)).skills(Map.of("Attack",99)).build();}
    private RoutingPreferences preferences(RouteObjective objective)
    {return RoutingPreferences.builder().target(AchievementTier.EASY).objective(objective).build();}
    private RoutePlan plan(List<CombatAchievement> tasks,PlayerProfile profile,RoutingPreferences preferences)
    {return new RouteEngine().plan(tasks,profile,preferences);}
    private Set<Integer> ids(RoutePlan plan)
    {Set<Integer> ids=new TreeSet<>();plan.stops().forEach(s->s.tasks().forEach(a->ids.add(a.task().id())));return ids;}

    @Test public void easiestChoosesLowerExecutionDifficultyOverTheFastestBoss()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Hard",1,8),task(1,"Easy",6,1));
        assertEquals(Set.of(0),ids(plan(tasks,profile(1),preferences(RouteObjective.FASTEST_NEXT_TIER))));
        RoutePlan easy=plan(tasks,profile(1),preferences(RouteObjective.EASIEST_PROGRESSION));
        assertEquals(Set.of(1),ids(easy));assertEquals(6,easy.estimatedMinutes(),0.001);
        assertEquals(5.5,easy.stops().get(0).planningCost(),0.001);
        assertTrue(easy.stops().get(0).reasons().stream().anyMatch(s->s.contains("lower difficulty")));
    }
    @Test public void minimumFrictionChoosesOneBundleOverTwoShortTrips()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Bundle",4,1),task(1,"Bundle",4,1),task(2,"Solo A",1,1),task(3,"Solo B",1,1));
        assertEquals(Set.of(2,3),ids(plan(tasks,profile(2),preferences(RouteObjective.FASTEST_NEXT_TIER))));
        RoutePlan friction=plan(tasks,profile(2),preferences(RouteObjective.MINIMUM_FRICTION));
        assertEquals(Set.of(0,1),ids(friction));assertEquals(8,friction.estimatedMinutes(),0.001);assertEquals(20,friction.stops().get(0).planningCost(),0.001);
    }
    @Test public void ironmanPriorityUsesPreparationEvidenceWithoutInventingGrindDuration()
    {
        CombatAchievement preparation=CombatAchievement.builder(0,"Gear task","Preparation",AchievementTier.EASY).secondsPerKill(60).setupMinutes(0).recommendedGear(Set.of("bowfa")).requirementsKnown(true).timingKnown(true).build();
        List<CombatAchievement> tasks=List.of(preparation,task(1,"Owned loadout",15,1));
        PlayerProfile account=PlayerProfile.builder().loaded(true).catalogueComplete(true).accountType(AccountType.IRONMAN).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),Set.of("bowfa"),false)).thresholds(Map.of(AchievementTier.EASY,1)).build();
        RoutePlan fastest=plan(tasks,account,preferences(RouteObjective.FASTEST_NEXT_TIER));assertEquals(Set.of(0),ids(fastest));assertEquals(1,fastest.estimatedMinutes(),0.001);
        RoutePlan iron=plan(tasks,account,preferences(RouteObjective.IRONMAN_EFFICIENT));assertEquals(Set.of(1),ids(iron));assertEquals(15,iron.estimatedMinutes(),0.001);
        assertTrue(fastest.assessments().get(0).reasons().stream().anyMatch(s->s.contains("acquisition time is excluded")));
    }
    @Test public void budgetMaximizesPointsInsteadOfStoppingAtTheTierGap()
    {
        RoutePlan plan=plan(List.of(task(0,"A",1,1),task(1,"B",1,1),task(2,"C",20,1)),profile(1),RoutingPreferences.builder().target(AchievementTier.EASY).objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(2).build());
        assertEquals(Set.of(0,1),ids(plan));assertEquals(2,plan.projectedPoints());assertEquals(2,plan.timeBudgetMinutes());assertTrue(plan.scopeComplete());
        assertTrue(plan.blockers().stream().anyMatch(s->s.contains("not an ETA")));
    }
    @Test public void budgetOptionPruningUsesPhysicalTimeRatherThanTheLowestPlanningPenalty()
    {
        CombatAchievement shortUnconfirmed=CombatAchievement.builder(1,"Short","One boss",AchievementTier.EASY).secondsPerKill(60).setupMinutes(0).recommendedGear(Set.of("bowfa")).requirementsKnown(true).timingKnown(true).build();
        RoutePlan plan=plan(List.of(task(0,"One boss",6,1),shortUnconfirmed),profile(1),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(2).build());
        assertEquals(Set.of(1),ids(plan));assertEquals(1,plan.estimatedMinutes(),0.001);
    }
    @Test public void budgetHonoursPinnedBundlesAndReportsAnImpossibleBudget()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Pinned",10,1),task(1,"Short",1,1));
        RoutingPreferences prefs=RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(2).pinnedTaskIds(Set.of(0)).build();
        RoutePlan impossible=plan(tasks,profile(1),prefs);assertTrue(impossible.stops().isEmpty());assertFalse(impossible.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,impossible.reachability());
        assertTrue(impossible.blockers().stream().anyMatch(s->s.contains("cannot fit")));
        RoutePlan fits=plan(tasks,profile(1),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(11).pinnedTaskIds(Set.of(0)).build());
        assertEquals(Set.of(0,1),ids(fits));assertTrue(fits.scopeComplete());
    }
    @Test public void bossCompletionCoversOnlyTheSelectedActivityAndRetainsExclusions()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Selected",1,1),task(1,"Selected",2,1),task(2,"Other",1,1));
        RoutingPreferences prefs=RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION).selectedActivity("Selected").excludedTaskIds(Set.of(1)).build();
        RoutePlan plan=plan(tasks,profile(1),prefs);assertEquals(Set.of(0),ids(plan));assertFalse(plan.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,plan.reachability());
        assertTrue(plan.blockers().stream().anyMatch(s->s.startsWith("Task 1:")));assertEquals("Selected",plan.selectedActivity());
        RoutePlan full=plan(tasks,profile(100),RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION).selectedActivity("Selected").build());
        assertEquals(Set.of(0,1),ids(full));assertTrue(full.scopeComplete());assertEquals(Reachability.REACHABLE_NOW,full.reachability());assertEquals(100,full.pointsRemaining());
    }
    @Test public void bossCompletionDetectsEmptySelectionOutsidePinsAndCompletedActivity()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Selected",1,1),task(1,"Other",1,1));
        RoutePlan unset=plan(tasks,profile(1),preferences(RouteObjective.BOSS_COMPLETION));assertFalse(unset.scopeComplete());
        RoutePlan conflict=plan(tasks,profile(1),RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION).selectedActivity("Selected").pinnedTaskIds(Set.of(1)).build());
        assertFalse(conflict.scopeComplete());assertTrue(conflict.blockers().stream().anyMatch(s->s.contains("another activity")));
        PlayerProfile done=PlayerProfile.builder().loaded(true).catalogueComplete(true).completedTaskIds(Set.of(0)).thresholds(Map.of(AchievementTier.EASY,10)).build();
        RoutePlan reached=plan(tasks,done,RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION).selectedActivity("Selected").build());assertEquals(Reachability.REACHED,reached.reachability());assertTrue(reached.scopeComplete());assertEquals(10,reached.pointsRemaining());
    }
    @Test public void currentActivitySurvivesBossRankingAndCopiesPreserveScope()
    {
        CombatAchievement task=CombatAchievement.builder(0,"T","Here",AchievementTier.EASY).secondsPerKill(60).setupMinutes(9).requirementsKnown(true).timingKnown(true).build();
        RoutePlan plan=plan(List.of(task),profile(1),RoutingPreferences.builder().objective(RouteObjective.MINIMUM_FRICTION).currentActivity("Here").timeBudgetMinutes(120).selectedActivity("Here").build());
        assertEquals(1,plan.bossSessions().get(0).estimatedMinutes(),0.001);assertTrue(plan.bossSessions().get(0).reasons().stream().anyMatch(s->s.startsWith("Already at")));
        RoutePlan copy=plan.withPreparationSteps(Collections.emptyList()).withBossSessions(plan.bossSessions());assertEquals(plan.objective(),copy.objective());assertEquals(120,copy.timeBudgetMinutes());assertEquals("Here",copy.selectedActivity());assertTrue(copy.scopeComplete());
    }
    @Test public void budgetSolverMatchesAnIndependentExhaustiveSubsetOracle()
    {
        Random random=new Random(6614);
        for(int sample=0;sample<40;sample++)
        {
            List<CombatAchievement> tasks=new ArrayList<>();int budget=1+random.nextInt(13);
            for(int id=0;id<7;id++)tasks.add(CombatAchievement.builder(id,"T"+id,"B"+random.nextInt(3),AchievementTier.EASY).secondsPerKill((1+random.nextInt(5))*60).setupMinutes(2).requirementsKnown(true).timingKnown(true).build());
            int bestPoints=0;double bestMinutes=0;
            for(int mask=0;mask<(1<<tasks.size());mask++)
            {
                int points=Integer.bitCount(mask);double minutes=0;Set<String> trips=new HashSet<>();
                for(int id=0;id<tasks.size();id++)if((mask&(1<<id))!=0){minutes+=tasks.get(id).secondsPerKill()/60;trips.add(tasks.get(id).activity());}
                minutes+=trips.size()*2;if(minutes>budget)continue;
                if(points>bestPoints||points==bestPoints&&minutes<bestMinutes){bestPoints=points;bestMinutes=minutes;}
            }
            RoutePlan route=plan(tasks,profile(1),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(budget).build());
            assertEquals(bestPoints,route.projectedPoints());assertEquals(bestMinutes,route.estimatedMinutes(),1e-6);assertTrue(route.estimatedMinutes()<=budget);assertTrue(route.optimal());
            List<CombatAchievement> reverse=new ArrayList<>(tasks);Collections.reverse(reverse);assertEquals(ids(route),ids(plan(reverse,profile(1),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(budget).build())));
        }
    }
    @Test public void coveringPoliciesMatchTheirIndependentCostOracle()
    {
        Random random=new Random(3117);
        for(RouteObjective mode:List.of(RouteObjective.FASTEST_NEXT_TIER,RouteObjective.EASIEST_PROGRESSION,RouteObjective.MINIMUM_FRICTION,RouteObjective.IRONMAN_EFFICIENT))for(int sample=0;sample<15;sample++)
        {
            List<CombatAchievement> tasks=new ArrayList<>();for(int id=0;id<6;id++)tasks.add(CombatAchievement.builder(id,"T"+id,"B"+random.nextInt(3),AchievementTier.EASY).secondsPerKill((1+random.nextInt(5))*60).setupMinutes(2).difficulty(1+random.nextInt(4)).requirementsKnown(true).timingKnown(true).build());
            int gap=1+random.nextInt(6),lowestStage=Integer.MAX_VALUE;double oracle=Double.POSITIVE_INFINITY;
            for(int mask=1;mask<(1<<6);mask++)
            {
                if(Integer.bitCount(mask)<gap)continue;Map<String,List<CombatAchievement>> groups=new HashMap<>();for(int id=0;id<6;id++)if((mask&(1<<id))!=0)groups.computeIfAbsent(tasks.get(id).activity(),key->new ArrayList<>()).add(tasks.get(id));
                int stage=groups.values().stream().flatMap(Collection::stream).mapToInt(CombatAchievement::difficulty).max().orElse(1);
                if(mode==RouteObjective.EASIEST_PROGRESSION&&stage>lowestStage)continue;
                double total=0;
                for(List<CombatAchievement> group:groups.values())
                {
                    double physical=2+group.stream().mapToDouble(t->t.secondsPerKill()/60).sum(),learning=group.stream().mapToDouble(t->(t.difficulty()-1)*0.5).sum();
                    if(mode==RouteObjective.EASIEST_PROGRESSION){int hardest=group.stream().mapToInt(CombatAchievement::difficulty).max().getAsInt();total+=physical*.25+learning+hardest*hardest*4;}
                    else total+=physical+learning+(mode==RouteObjective.MINIMUM_FRICTION?12+2*3:0);
                }
                if(mode==RouteObjective.EASIEST_PROGRESSION&&stage<lowestStage){lowestStage=stage;oracle=total;}
                else oracle=Math.min(oracle,total);
            }
            RoutePlan route=plan(tasks,profile(gap),preferences(mode));assertEquals(mode.name(),oracle,route.stops().stream().mapToDouble(BossSession::planningCost).sum(),1e-6);assertTrue(route.scopeComplete());assertTrue(route.optimal());
        }
    }

    @Test public void zeroPointBudgetIsUnreachableWhenTasksAreTooLongOrUnavailable()
    {
        RoutingPreferences budget=RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(1).build();
        RoutePlan tooLong=plan(List.of(task(0,"Slow",2,1)),profile(1),budget);
        assertEquals(0,tooLong.projectedPoints());assertFalse(tooLong.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,tooLong.reachability());
        assertTrue(tooLong.blockers().stream().anyMatch(s->s.contains("increase the budget")));
        CombatAchievement locked=CombatAchievement.builder(0,"Locked","Boss",AchievementTier.EASY).quests(Map.of("Quest",QuestStatus.COMPLETED)).requirementsKnown(true).timingKnown(true).build();
        PlayerProfile lockedAccount=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,1)).quests(Map.of("Quest",QuestStatus.NOT_STARTED)).build();
        RoutePlan unavailable=plan(List.of(locked),lockedAccount,budget);
        assertFalse(unavailable.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,unavailable.reachability());
        assertTrue(unavailable.blockers().stream().anyMatch(s->s.contains("No incomplete tasks are eligible")));
        GearProfile preparation=GearProfile.of(Set.of(),Set.of(),Set.of(),Set.of("bowfa"),false).withActivePreparationEstimates(Map.of("bowfa",10.0));
        PlayerProfile preparable=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,1)).gear(preparation).build();
        RoutePlan sharedZero=plan(List.of(CombatAchievement.builder(0,"Prepare","Boss",AchievementTier.EASY).recommendedGear(Set.of("bowfa")).secondsPerKill(60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build()),preparable,budget);
        assertFalse(sharedZero.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,sharedZero.reachability());
    }

    @Test public void fullyCompletedVerifiedCatalogueIsReachedForBudgetAndGrandmasterUnlessPinsConflict()
    {
        List<CombatAchievement> tasks=List.of(task(0,"A",1,1));
        PlayerProfile done=PlayerProfile.builder().loaded(true).catalogueComplete(true).completedTaskIds(Set.of(0)).currentPoints(1).thresholds(Map.of(AchievementTier.EASY,1,AchievementTier.GRANDMASTER,1)).build();
        RoutePlan budget=plan(tasks,done,RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).build());
        assertEquals(Reachability.REACHED,budget.reachability());assertTrue(budget.scopeComplete());assertTrue(budget.stops().isEmpty());
        RoutePlan gm=plan(tasks,done,RoutingPreferences.builder().target(AchievementTier.GRANDMASTER).build());assertEquals(Reachability.REACHED,gm.reachability());assertTrue(gm.scopeComplete());
        RoutePlan missingPin=plan(tasks,done,RoutingPreferences.builder().target(AchievementTier.GRANDMASTER).pinnedTaskIds(Set.of(999)).build());
        assertEquals(Reachability.UNREACHABLE_NOW,missingPin.reachability());assertFalse(missingPin.scopeComplete());assertTrue(missingPin.blockers().stream().anyMatch(s->s.startsWith("Pinned task 999")));
        RoutePlan budgetPin=plan(tasks,done,RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).pinnedTaskIds(Set.of(999)).build());assertFalse(budgetPin.scopeComplete());assertEquals(Reachability.UNREACHABLE_NOW,budgetPin.reachability());
        PlayerProfile unverified=PlayerProfile.builder().loaded(true).catalogueComplete(false).completedTaskIds(Set.of(0)).currentPoints(1).thresholds(Map.of(AchievementTier.EASY,1)).build();
        RoutePlan stale=plan(tasks,unverified,RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).build());assertEquals(Reachability.UNREACHABLE_NOW,stale.reachability());assertFalse(stale.scopeComplete());
    }
}

package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedPreparationTest
{
    private CombatAchievement task(int id,String activity,double minutes,boolean gear)
    {return CombatAchievement.builder(id,"Task "+id,activity,AchievementTier.EASY).secondsPerKill(minutes*60).setupMinutes(0).requirementsKnown(true).timingKnown(true).recommendedGear(gear?Set.of("same-gear"):Set.of()).build();}
    private PlayerProfile profile(int target,double preparation)
    {return PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,target)).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),Set.of("same-gear"),false).withActivePreparationEstimates(Map.of("same-gear",preparation))).build();}
    private Set<Integer> ids(RoutePlan plan)
    {Set<Integer> ids=new TreeSet<>();plan.stops().forEach(s->s.tasks().forEach(a->ids.add(a.task().id())));return ids;}
    @Test public void budgetPaysOneTenMinuteAcquisitionForTwoOneMinuteActivities()
    {
        RoutePlan route=new RouteEngine().plan(List.of(task(0,"A",1,true),task(1,"B",1,true)),profile(1,10),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(12).build());
        assertEquals(Set.of(0,1),ids(route));assertEquals(2,route.projectedPoints());assertEquals(12,route.estimatedMinutes(),0.001);assertTrue(route.optimal());assertTrue(route.scopeComplete());
        assertEquals(12,route.stops().stream().mapToDouble(BossSession::estimatedMinutes).sum(),0.001);
        assertEquals(1,route.stops().stream().filter(stop->stop.reasons().stream().anyMatch(reason->reason.startsWith("Gear preparation already covered"))).count());
    }
    @Test public void tierSelectionAndIronmanSelectionUseTheSameSharedOwnershipCost()
    {
        List<CombatAchievement> tierTasks=List.of(task(0,"A",1,true),task(1,"B",1,true),task(2,"Owned",20,false),task(3,"Owned",20,false));
        RoutePlan fastest=new RouteEngine().plan(tierTasks,profile(2,10),RoutingPreferences.builder().build());
        assertEquals(Set.of(0,1),ids(fastest));assertEquals(12,fastest.estimatedMinutes(),0.001);assertTrue(fastest.optimal());
        List<CombatAchievement> ironTasks=List.of(task(0,"A",1,true),task(1,"B",1,true),task(2,"Owned",57.5,false),task(3,"Owned",57.5,false));
        RoutePlan iron=new RouteEngine().plan(ironTasks,profile(2,10),RoutingPreferences.builder().objective(RouteObjective.IRONMAN_EFFICIENT).build());
        assertEquals(Set.of(0,1),ids(iron));assertEquals(12,iron.estimatedMinutes(),0.001);assertEquals(100,iron.stops().stream().mapToDouble(BossSession::planningCost).sum(),0.001);
    }
    @Test public void everyPolicyAndPinnedBudgetSharesAcquisitionAcrossStops()
    {
        List<CombatAchievement> tasks=List.of(task(0,"A",1,true),task(1,"B",1,true));
        for(RouteObjective objective:List.of(RouteObjective.FASTEST_NEXT_TIER,RouteObjective.EASIEST_PROGRESSION,RouteObjective.MINIMUM_FRICTION,RouteObjective.IRONMAN_EFFICIENT,RouteObjective.MAXIMUM_POINTS_PER_HOUR))
        {
            RoutePlan route=new RouteEngine().plan(tasks,profile(2,10),RoutingPreferences.builder().objective(objective).pinnedTaskIds(Set.of(0,1)).timeBudgetMinutes(12).build());
            assertEquals(objective.name(),Set.of(0,1),ids(route));assertEquals(objective.name(),12,route.estimatedMinutes(),0.001);assertTrue(route.scopeComplete());
        }
    }
    @Test public void optionPruningRetainsPreparationSignaturesWithFutureSharingValue()
    {
        List<CombatAchievement> tasks=List.of(task(0,"A",13,false),task(1,"A",2,true),task(2,"B",1,true));
        RoutePlan route=new RouteEngine().plan(tasks,profile(2,10),RoutingPreferences.builder().build());
        assertEquals(Set.of(1,2),ids(route));assertEquals(13,route.estimatedMinutes(),0.001);assertTrue(route.optimal());
    }
    @Test public void budgetMatchesAnIndependentSharedAcquisitionSubsetOracle()
    {
        Random random=new Random(65481);
        for(int sample=0;sample<40;sample++)
        {
            int budget=3+random.nextInt(15),preparation=1+random.nextInt(7);List<CombatAchievement> tasks=new ArrayList<>();
            for(int id=0;id<7;id++)tasks.add(task(id,"B"+random.nextInt(3),1+random.nextInt(5),random.nextBoolean()));
            int bestPoints=0;double bestTime=0;
            for(int mask=0;mask<(1<<tasks.size());mask++)
            {
                int points=Integer.bitCount(mask);double time=0;boolean needsGear=false;
                for(int id=0;id<tasks.size();id++)if((mask&(1<<id))!=0){time+=tasks.get(id).secondsPerKill()/60;needsGear|=!tasks.get(id).recommendedGear().isEmpty();}
                if(needsGear)time+=preparation;if(time>budget)continue;
                if(points>bestPoints||points==bestPoints&&time<bestTime){bestPoints=points;bestTime=time;}
            }
            RoutePlan route=new RouteEngine().plan(tasks,profile(1,preparation),RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(budget).build());
            assertEquals(bestPoints,route.projectedPoints());assertEquals(bestTime,route.estimatedMinutes(),1e-6);assertTrue(route.optimal());
        }
    }

    @Test public void budgetOracleTracksDifferentAndOverlappingPreparationSets()
    {
        Random random=new Random(16895);Map<String,Double> estimates=Map.of("gear-a",2.0,"gear-b",3.0,"gear-c",4.0);
        for(int sample=0;sample<30;sample++)
        {
            List<CombatAchievement> tasks=new ArrayList<>();int budget=3+random.nextInt(17);
            for(int id=0;id<7;id++)
            {
                Set<String> gear=new TreeSet<>();for(String capability:new TreeSet<>(estimates.keySet()))if(random.nextBoolean())gear.add(capability);
                tasks.add(CombatAchievement.builder(id,"T"+id,"B"+random.nextInt(3),AchievementTier.EASY).secondsPerKill((1+random.nextInt(5))*60).setupMinutes(0).recommendedGear(gear).requirementsKnown(true).timingKnown(true).build());
            }
            PlayerProfile profile=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,1)).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),estimates.keySet(),false).withActivePreparationEstimates(estimates)).build();
            int bestPoints=0;double bestTime=0;
            for(int mask=0;mask<(1<<tasks.size());mask++)
            {
                double time=0;Set<String> gear=new TreeSet<>();int points=Integer.bitCount(mask);
                for(int id=0;id<tasks.size();id++)if((mask&(1<<id))!=0){time+=tasks.get(id).secondsPerKill()/60;gear.addAll(tasks.get(id).recommendedGear());}
                for(String capability:gear)time+=estimates.get(capability);if(time>budget)continue;
                if(points>bestPoints||points==bestPoints&&time<bestTime){bestPoints=points;bestTime=time;}
            }
            RoutePlan route=new RouteEngine().plan(tasks,profile,RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(budget).build());
            assertEquals(bestPoints,route.projectedPoints());assertEquals(bestTime,route.estimatedMinutes(),1e-6);assertTrue(route.optimal());
        }
    }

    @Test public void boundedSignatureSearchNeverClaimsGlobalOptimality()
    {
        List<CombatAchievement> tasks=new ArrayList<>();Map<String,Double> estimates=new TreeMap<>();
        for(int id=0;id<14;id++)
        {
            String gear="gear-"+id;estimates.put(gear,1.0);
            tasks.add(CombatAchievement.builder(id,"T"+id,"Boss "+id,AchievementTier.EASY).secondsPerKill(60).setupMinutes(0).recommendedGear(Set.of(gear)).requirementsKnown(true).timingKnown(true).build());
        }
        PlayerProfile profile=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,1)).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),estimates.keySet(),false).withActivePreparationEstimates(estimates)).build();
        RoutePlan route=new RouteEngine().plan(tasks,profile,RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(60).build());
        assertFalse(route.optimal());assertTrue(route.scopeComplete());assertEquals(14,route.projectedPoints());assertEquals(28,route.estimatedMinutes(),0.001);
        assertTrue(route.blockers().stream().anyMatch(reason->reason.contains("bounded limit")));
    }

    @Test public void tierPoliciesMatchAnIndependentSharedSetCostOracle()
    {
        Random random=new Random(84215);Map<String,Double> estimates=Map.of("gear-a",2.0,"gear-b",5.0);
        for(RouteObjective objective:List.of(RouteObjective.FASTEST_NEXT_TIER,RouteObjective.EASIEST_PROGRESSION,RouteObjective.MINIMUM_FRICTION,RouteObjective.IRONMAN_EFFICIENT))for(int sample=0;sample<12;sample++)
        {
            List<CombatAchievement> tasks=new ArrayList<>();int gap=1+random.nextInt(7);
            for(int id=0;id<7;id++)
            {
                Set<String> gear=new TreeSet<>();for(String capability:new TreeSet<>(estimates.keySet()))if(random.nextBoolean())gear.add(capability);
                tasks.add(CombatAchievement.builder(id,"T"+id,"B"+random.nextInt(3),AchievementTier.EASY).secondsPerKill((1+random.nextInt(5))*60).setupMinutes(0).recommendedGear(gear).requirementsKnown(true).timingKnown(true).build());
            }
            double minimum=Double.POSITIVE_INFINITY;int lowestStage=Integer.MAX_VALUE;
            for(int mask=1;mask<(1<<tasks.size());mask++)
            {
                if(Integer.bitCount(mask)<gap)continue;double encounter=0,uncertainty=0;Map<String,Set<String>> gearByActivity=new HashMap<>();Set<String> acquired=new TreeSet<>();
                for(int id=0;id<tasks.size();id++)if((mask&(1<<id))!=0)
                {
                    CombatAchievement task=tasks.get(id);encounter+=task.secondsPerKill()/60;if(!task.recommendedGear().isEmpty())uncertainty+=2;
                    gearByActivity.computeIfAbsent(task.activity(),key->new HashSet<>()).addAll(task.recommendedGear());acquired.addAll(task.recommendedGear());
                }
                int stage=acquired.isEmpty()?1:2;
                if(objective==RouteObjective.EASIEST_PROGRESSION&&stage>lowestStage)continue;
                double preparation=0;for(String capability:acquired)preparation+=estimates.get(capability);
                int requirements=gearByActivity.values().stream().mapToInt(Set::size).sum(),trips=gearByActivity.size();
                double cost=encounter+preparation+requirements*8+uncertainty;
                if(objective==RouteObjective.EASIEST_PROGRESSION)cost=(encounter+preparation)*.25+requirements*8+uncertainty
                    +gearByActivity.values().stream().mapToInt(gear->gear.isEmpty()?4:16).sum();
                if(objective==RouteObjective.MINIMUM_FRICTION)cost+=trips*12;
                if(objective==RouteObjective.IRONMAN_EFFICIENT)cost+=preparation*2+requirements*24;
                if(objective==RouteObjective.EASIEST_PROGRESSION&&stage<lowestStage){lowestStage=stage;minimum=cost;}
                else minimum=Math.min(minimum,cost);
            }
            PlayerProfile profile=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,gap)).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),estimates.keySet(),false).withActivePreparationEstimates(estimates)).build();
            RoutePlan route=new RouteEngine().plan(tasks,profile,RoutingPreferences.builder().objective(objective).build());
            assertEquals(objective.name(),minimum,route.stops().stream().mapToDouble(BossSession::planningCost).sum(),1e-6);assertTrue(route.optimal());assertTrue(route.scopeComplete());
        }
    }
}

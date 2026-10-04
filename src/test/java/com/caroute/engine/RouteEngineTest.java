package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteEngineTest
{
    private CombatAchievement task(int id,String activity,double minutes)
    { return CombatAchievement.builder(id,"Task "+id,activity,AchievementTier.EASY).secondsPerKill(minutes*60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build(); }
    private PlayerProfile profile(int threshold)
    { return PlayerProfile.builder().accountKey("fixture").loaded(true).version(1).catalogueComplete(true).skills(Map.of("Attack",99,"Slayer",99)).thresholds(Map.of(AchievementTier.EASY,threshold,AchievementTier.GRANDMASTER,threshold)).build(); }
    private RoutePlan route(List<CombatAchievement> tasks, PlayerProfile profile, RoutingPreferences prefs)
    { return new RouteEngine().plan(tasks,profile,prefs); }
    private RoutingPreferences normal(){return RoutingPreferences.builder().target(AchievementTier.EASY).build();}
    private Set<Integer> ids(RoutePlan route){Set<Integer> ids=new TreeSet<>();route.stops().forEach(s->s.tasks().forEach(a->ids.add(a.task().id())));return ids;}

    @Test public void nestedKcUsesOneSharedProgression()
    {
        List<CombatAchievement> tasks=new ArrayList<>();int[] thresholds={25,75,150};
        for(int i=0;i<3;i++)tasks.add(CombatAchievement.builder(i,"KC "+thresholds[i],"Boss",AchievementTier.EASY).kcThreshold(thresholds[i]).secondsPerKill(60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build());
        PlayerProfile p=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,3)).killCounts(Map.of("Boss",0)).build();
        RoutePlan plan=route(tasks,p,normal());assertEquals(150,plan.estimatedMinutes(),0.001);assertEquals(Set.of(0,1,2),ids(plan));assertEquals(3,plan.projectedPoints());
        p=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,3)).killCounts(Map.of("Boss",50)).build();
        assertEquals(100,route(tasks,p,normal()).estimatedMinutes(),0.001);
    }
    @Test public void bundlesBeatSwitchingWhenSetupDominates()
    {
        List<CombatAchievement> tasks=List.of(CombatAchievement.builder(0,"A","Bundle",AchievementTier.EASY).secondsPerKill(120).setupMinutes(5).requirementsKnown(true).timingKnown(true).build(),
            CombatAchievement.builder(1,"B","Bundle",AchievementTier.EASY).secondsPerKill(120).setupMinutes(5).requirementsKnown(true).timingKnown(true).build(),
            CombatAchievement.builder(2,"C","Solo",AchievementTier.EASY).secondsPerKill(60).setupMinutes(5).requirementsKnown(true).timingKnown(true).build());
        RoutePlan plan=route(tasks,profile(2),normal());assertEquals(1,plan.stops().size());assertEquals("Bundle",plan.stops().get(0).activity());assertEquals(9,plan.estimatedMinutes(),0.001);
    }
    @Test public void pinnedBossReusesSetupInsteadOfChoosingAnotherTrip()
    {
        List<CombatAchievement> tasks=new ArrayList<>();double[] times={1,10,6};String[] bosses={"A","A","B"};
        for(int i=0;i<3;i++)tasks.add(CombatAchievement.builder(i,"T"+i,bosses[i],AchievementTier.EASY).secondsPerKill(times[i]*60).setupMinutes(6).requirementsKnown(true).timingKnown(true).build());
        RoutePlan plan=route(tasks,profile(2),RoutingPreferences.builder().target(AchievementTier.EASY).pinnedTaskIds(Set.of(0)).build());
        assertEquals(Set.of(0,1),ids(plan));assertEquals(17,plan.estimatedMinutes(),0.001);
    }
    @Test public void questLockedTasksDoNotEnterNowRoute()
    {
        CombatAchievement locked=CombatAchievement.builder(0,"Locked","Boss",AchievementTier.EASY).quests(Map.of("Quest",QuestStatus.COMPLETED)).requirementsKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).quests(Map.of("Quest",QuestStatus.NOT_STARTED)).build();
        RoutePlan plan=route(List.of(locked),p,normal());assertTrue(plan.stops().isEmpty());assertEquals(EligibilityState.QUEST_LOCKED,plan.assessments().get(0).eligibility().state());
    }
    @Test public void unreadableRequirementIsUnknownRatherThanFalseLock()
    {
        CombatAchievement task=CombatAchievement.builder(0,"Unknown","Boss",AchievementTier.EASY).requiredSkills(Map.of("Slayer",85)).requirementsKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).build();
        RoutePlan plan=route(List.of(task),p,normal());assertEquals(EligibilityState.UNKNOWN_REQUIREMENT,plan.assessments().get(0).eligibility().state());assertEquals(Reachability.CONDITIONAL,plan.reachability());
    }
    @Test public void knownHardSkillGapLocksContent()
    {
        CombatAchievement task=CombatAchievement.builder(0,"Slayer","Boss",AchievementTier.EASY).requiredSkills(Map.of("Slayer",85)).requirementsKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).skills(Map.of("Slayer",1)).build();
        assertEquals(EligibilityState.SKILL_LOCKED,route(List.of(task),p,normal()).assessments().get(0).eligibility().state());
    }
    @Test public void mildSoftGapCostsMoreWithoutAnAccessLock()
    {
        CombatAchievement task=CombatAchievement.builder(0,"Recommended","Boss",AchievementTier.EASY).recommendedSkills(Map.of("Attack",80)).requirementsKnown(true).timingKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).skills(Map.of("Attack",75)).build();
        TaskAssessment a=route(List.of(task),p,normal()).assessments().get(0);assertEquals(EligibilityState.AVAILABLE,a.eligibility().state());assertTrue(a.eligibility().recommendedNow());assertTrue(a.components().get("statReadiness")>0);
    }
    @Test public void severelyUnreadyAccountGetsPartialRouteNotEndgame()
    {
        CombatAchievement hard=CombatAchievement.builder(1,"Endgame","Raid",AchievementTier.EASY).recommendedSkills(Map.of("Attack",90)).requirementsKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,2)).skills(Map.of("Attack",1)).build();
        RoutePlan plan=route(List.of(task(0,"Easy",2),hard),p,normal());assertEquals(Set.of(0),ids(plan));assertEquals(Reachability.UNREACHABLE_NOW,plan.reachability());assertEquals(EligibilityState.AVAILABLE,plan.assessments().get(1).eligibility().state());
    }
    @Test public void avoidWildernessRemovesCandidate()
    {
        CombatAchievement wild=CombatAchievement.builder(0,"Wild","Wilderness",AchievementTier.EASY).flags(Set.of(TaskFlag.WILDERNESS)).requirementsKnown(true).build();
        assertTrue(route(List.of(wild),profile(1),RoutingPreferences.builder().target(AchievementTier.EASY).preference(TaskFlag.WILDERNESS,Preference.AVOID).build()).stops().isEmpty());
    }
    @Test public void preferNotSpeedSinksButDoesNotRemoveTask()
    {
        CombatAchievement speed=CombatAchievement.builder(0,"Speed","Fast",AchievementTier.EASY).secondsPerKill(60).setupMinutes(0).flags(Set.of(TaskFlag.SPEED)).requirementsKnown(true).timingKnown(true).build();
        List<CombatAchievement> tasks=List.of(speed,task(1,"Normal",3));assertEquals(Set.of(0),ids(route(tasks,profile(1),normal())));
        RoutePlan plan=route(tasks,profile(1),RoutingPreferences.builder().target(AchievementTier.EASY).preference(TaskFlag.SPEED,Preference.PREFER_NOT).build());assertEquals(Set.of(1),ids(plan));assertTrue(plan.assessments().get(0).eligibility().recommendedNow());
    }
    @Test public void completedTasksDisappearImmediately()
    {
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,2)).completedTaskIds(Set.of(0)).currentPoints(1).build();
        assertEquals(Set.of(1),ids(route(List.of(task(0,"A",1),task(1,"B",1)),p,normal())));
    }
    @Test public void grandmasterRetainsBlockedAndExcludedRequirements()
    {
        RoutePlan plan=route(List.of(task(0,"A",1),task(1,"B",1)),profile(2),RoutingPreferences.builder().target(AchievementTier.GRANDMASTER).excludedTaskIds(Set.of(1)).build());
        assertEquals(Reachability.UNREACHABLE_NOW,plan.reachability());assertEquals(2,plan.assessments().size());assertFalse(plan.blockers().isEmpty());
    }
    @Test public void grandmasterCannotClaimCompletenessForStaleCatalogue()
    {
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.GRANDMASTER,1)).completedTaskIds(Set.of(0)).currentPoints(1).catalogueComplete(false).build();
        assertNotEquals(Reachability.REACHED,route(List.of(task(0,"A",1)),p,RoutingPreferences.builder().target(AchievementTier.GRANDMASTER).build()).reachability());
    }
    @Test public void acquisitionCostsCanDifferentiateMainAndIronman()
    {
        CombatAchievement gear=CombatAchievement.builder(0,"Gear","A",AchievementTier.EASY).secondsPerKill(60).setupMinutes(0).recommendedGear(Set.of("bowfa")).requirementsKnown(true).timingKnown(true).build();
        RouteEngine engine=new RouteEngine((type,cap,owned)->AcquisitionCost.known(type==AccountType.MAIN?1:120,0,0,Confidence.HIGH));
        for(AccountType type:List.of(AccountType.MAIN,AccountType.IRONMAN)) {
            PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).accountType(type).gear(GearProfile.of(Set.of(),Set.of(),Set.of(),true)).build();
            RoutePlan plan=engine.plan(List.of(gear,task(1,"B",30)),p,normal());assertEquals(type==AccountType.MAIN?Set.of(0):Set.of(1),ids(plan));
        }
    }
    @Test public void arbitraryInputOrderDoesNotChangeTies()
    {
        List<CombatAchievement> a=List.of(task(0,"A",2),task(1,"B",2));List<CombatAchievement> b=new ArrayList<>(a);Collections.reverse(b);
        assertEquals(ids(route(a,profile(1),normal())),ids(route(b,profile(1),normal())));
    }
    @Test public void hardRequirementAlternativesAreEvaluatedAsAny()
    {
        CombatAchievement t=CombatAchievement.builder(0,"Alternative","Boss",AchievementTier.EASY).requirement(Requirement.any(Requirement.skill("Magic",80),Requirement.skill("Ranged",80))).requirementsKnown(true).build();
        PlayerProfile p=PlayerProfile.builder().loaded(true).thresholds(Map.of(AchievementTier.EASY,1)).skills(Map.of("Magic",1,"Ranged",90)).build();
        assertEquals(EligibilityState.AVAILABLE,route(List.of(t),p,normal()).assessments().get(0).eligibility().state());
    }
    @Test public void executionAddCountsNeverMultiplyBossCompletions()
    {
        CombatAchievement t=CombatAchievement.builder(0,"Adds","Scurrius",AchievementTier.EASY).description("Kill 6 Giant Rats in 3 seconds").completionCount(1).secondsPerKill(60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build();
        assertEquals(1,route(List.of(t),profile(1),normal()).estimatedMinutes(),0.001);
    }
    @Test public void clusteredSelectionMatchesExhaustiveOracleForSmallIndependentInstances()
    {
        Random random=new Random(8317);
        for(int sample=0;sample<40;sample++) {
            List<CombatAchievement> tasks=new ArrayList<>();
            for(int i=0;i<7;i++)tasks.add(CombatAchievement.builder(i,"T"+i,"Boss "+random.nextInt(3),AchievementTier.EASY).secondsPerKill((1+random.nextInt(8))*60).setupMinutes(4).requirementsKnown(true).timingKnown(true).build());
            int gap=1+random.nextInt(7);double minimum=Double.POSITIVE_INFINITY;
            for(int mask=0;mask<(1<<7);mask++) {
                if(Integer.bitCount(mask)<gap)continue;double minutes=0;Set<String> bosses=new HashSet<>();
                for(int i=0;i<7;i++)if((mask&(1<<i))!=0){minutes+=tasks.get(i).secondsPerKill()/60;bosses.add(tasks.get(i).activity());}
                minimum=Math.min(minimum,minutes+4*bosses.size());
            }
            RoutePlan plan=route(tasks,profile(gap),normal());assertTrue(plan.optimal());assertEquals(minimum,plan.estimatedMinutes(),1e-6);
        }
    }
    @Test public void identicalStaminaConditionsUseTheirLargestRepeatCount()
    {
        List<CombatAchievement> tasks=new ArrayList<>();for(int i=0;i<3;i++)tasks.add(CombatAchievement.builder(i,"Stamina"+i,"Kraken",AchievementTier.EASY).completionCount(new int[]{25,50,100}[i]).overlapGroup("same-private-room").secondsPerKill(60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build());
        assertEquals(100,route(tasks,profile(3),normal()).estimatedMinutes(),0.001);
    }
}

package com.caroute.engine;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EasyToHardProgressionTest
{
    @Test public void aPointRichHardBossCannotReplaceEnoughEasierActivities()
    {
        List<CombatAchievement> tasks=new ArrayList<>();
        for(int id=0;id<8;id++)tasks.add(task(id,"Easy "+id,AchievementTier.EASY,12,2,2));
        for(int id=8;id<12;id++)tasks.add(task(id,"Hard bundle",AchievementTier.HARD,.5,5,5));
        RoutePlan plan=route(tasks,profile(8,99),Set.of());
        assertEquals(8,plan.projectedPoints());assertTrue(plan.scopeComplete());assertTrue(plan.optimal());
        assertEquals(8,plan.stops().size());assertTrue(plan.stops().stream().allMatch(stop->stop.activity().startsWith("Easy ")));
        assertTrue(plan.stops().get(0).reasons().stream().anyMatch(reason->reason.contains("lower difficulty before point efficiency")));
    }

    @Test public void bossBaselineAndTaskChallengeBothMatterIndependentlyOfTheCaRewardTier()
    {
        CombatAchievement dangerousEntry=task(0,"Dangerous boss",AchievementTier.EASY,1,1,7);
        CombatAchievement harderChallengeAtSimpleBoss=task(1,"Simple boss",AchievementTier.HARD,10,5,1);
        RoutePlan plan=route(List.of(dangerousEntry,harderChallengeAtSimpleBoss),profile(1,99),Set.of());
        assertEquals(Set.of(1),ids(plan));assertEquals(3,plan.projectedPoints());
        assertEquals(7,ProgressionDifficulty.stage(dangerousEntry,profile(1,99)));
        assertEquals(5,ProgressionDifficulty.stage(harderChallengeAtSimpleBoss,profile(1,99)));
    }

    @Test public void selectedStopsAndTasksProgressFromEasierToHarderRatherThanFastestFirst()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Hard",AchievementTier.EASY,1,5,5),
            task(1,"Middle",AchievementTier.EASY,10,3,3),task(2,"Easy",AchievementTier.EASY,20,1,1));
        RoutePlan plan=route(tasks,profile(3,99),Set.of());
        assertEquals(List.of("Easy","Middle","Hard"),List.of(plan.stops().get(0).activity(),plan.stops().get(1).activity(),plan.stops().get(2).activity()));
        List<CombatAchievement> oneBoss=List.of(task(0,"One boss",AchievementTier.EASY,1,5,2),task(1,"One boss",AchievementTier.EASY,20,2,2));
        RoutePlan bundle=route(oneBoss,profile(2,99),Set.of());
        assertEquals(List.of(1,0),List.of(bundle.stops().get(0).tasks().get(0).task().id(),bundle.stops().get(0).tasks().get(1).task().id()));
    }

    @Test public void recordedStatReadinessCanChangeTheNextActivityWithoutInventingAnAccessLock()
    {
        CombatAchievement demanding=CombatAchievement.builder(0,"Demanding task","Demanding",AchievementTier.EASY)
            .difficulty(2).activityDifficulty(2).recommendedSkills(Map.of("Attack",80)).secondsPerKill(60).setupMinutes(0).requirementsKnown(true).timingKnown(true).build();
        CombatAchievement comfortable=task(1,"Comfortable",AchievementTier.EASY,20,2,2);
        RoutePlan novice=route(List.of(demanding,comfortable),profile(1,70),Set.of());
        assertEquals(Set.of(1),ids(novice));assertEquals(EligibilityState.AVAILABLE,novice.assessments().get(0).eligibility().state());
        assertTrue(novice.assessments().get(0).eligibility().recommendedNow());
        assertEquals(Set.of(0),ids(route(List.of(demanding,comfortable),profile(1,99),Set.of())));
    }

    @Test public void knownMissingGearRaisesReadinessStageWhileUnseenGearRemainsUnknown()
    {
        CombatAchievement task=CombatAchievement.builder(0,"Gear task","Boss",AchievementTier.EASY)
            .difficulty(2).activityDifficulty(2).recommendedGear(Set.of("kit")).requirementsKnown(true).timingKnown(true).build();
        PlayerProfile unseen=profile(1,99),missing=PlayerProfile.builder().loaded(true).skills(Map.of("Attack",99))
            .gear(GearProfile.of(Set.of(),Set.of(),Set.of(),Set.of("kit"),false)).build();
        assertEquals(GearProfile.CapabilityState.UNKNOWN,unseen.gear().capability("kit"));
        assertEquals(2,ProgressionDifficulty.stage(task,unseen));assertEquals(3,ProgressionDifficulty.stage(task,missing));
        RoutePlan unknown=route(List.of(task),unseen,Set.of());assertEquals(Confidence.LOW,unknown.assessments().get(0).confidence());
        assertEquals(GearProfile.CapabilityState.UNKNOWN,unseen.gear().capability("kit"));
    }

    @Test public void unreadableSkillsStayUnknownAndDoNotBecomeLevelOneOrNinetyNine()
    {
        CombatAchievement task=CombatAchievement.builder(0,"Unknown readiness","Boss",AchievementTier.EASY).difficulty(2).activityDifficulty(2)
            .recommendedSkills(Map.of("Magic",80)).requirementsKnown(true).timingKnown(true).build();
        PlayerProfile unreadable=PlayerProfile.builder().loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY,1)).build();
        RoutePlan plan=route(List.of(task),unreadable,Set.of());
        assertEquals(3,ProgressionDifficulty.stage(task,unreadable));assertFalse(unreadable.skills().containsKey("Magic"));
        assertEquals(0,plan.assessments().get(0).components().get("statReadiness"),0.001);
        assertTrue(plan.assessments().get(0).reasons().stream().anyMatch(reason->reason.contains("Magic level is unreadable")));
        assertTrue(plan.assessments().get(0).eligibility().recommendedNow());assertEquals(Confidence.LOW,plan.confidence());
        assertEquals(5,ProgressionDifficulty.stage(task,PlayerProfile.builder().skills(Map.of("Magic",1)).build()));
    }

    @Test public void anExplicitPinCanRaiseTheDifficultyCeilingAndStaysMandatory()
    {
        List<CombatAchievement> tasks=List.of(task(0,"Hard pinned",AchievementTier.EASY,1,7,7),
            task(1,"Easy A",AchievementTier.EASY,5,2,2),task(2,"Easy B",AchievementTier.EASY,5,2,2));
        assertFalse(ids(route(tasks,profile(2,99),Set.of())).contains(0));
        RoutePlan pinned=route(tasks,profile(2,99),Set.of(0));assertTrue(ids(pinned).contains(0));assertEquals(2,pinned.projectedPoints());assertTrue(pinned.scopeComplete());
        assertEquals("Hard pinned",pinned.stops().get(pinned.stops().size()-1).activity());
    }

    @Test public void knownActivityExperienceLowersTheEstimateButUnknownHistoryIsNotDeclaredZero()
    {
        CombatAchievement task=task(0,"Boss",AchievementTier.HARD,1,5,4);
        PlayerProfile unknown=profile(1,99),known=PlayerProfile.builder().loaded(true).catalogueComplete(true)
            .thresholds(Map.of(AchievementTier.EASY,1)).killCounts(Map.of("Boss",150)).build();
        assertEquals(5,ProgressionDifficulty.stage(task,unknown));assertEquals(3,ProgressionDifficulty.stage(task,known));
        assertFalse(unknown.killCounts().containsKey("Boss"));assertEquals(Integer.valueOf(150),known.killCounts().get("Boss"));
    }

    @Test public void bundledCatalogueStartsNovicesWithEasierWorkAndStillAllowsAdvancedActivitiesLater()
    {
        List<CombatAchievement> tasks=AchievementRepository.loadBundled().tasks();
        PlayerProfile novice=catalogueProfile(tasks,70,0,false),advanced=catalogueProfile(tasks,99,0,true);
        RoutingPreferences progression=RoutingPreferences.builder().target(AchievementTier.MEDIUM).objective(RouteObjective.EASIEST_PROGRESSION).build();
        RoutePlan first=new RouteEngine().plan(tasks,novice,progression);
        assertFalse(first.stops().isEmpty());assertTrue(first.scopeComplete());assertFalse(first.stops().get(0).activity().equals("K'ril Tsutsaroth"));
        assertAscending(first,novice);assertUniquePoints(first);
        int noviceKril=first.assessments().stream().filter(a->a.task().activity().equals("K'ril Tsutsaroth"))
            .mapToInt(a->ProgressionDifficulty.stage(a.task(),novice)).max().orElseThrow();
        RoutePlan full=new RouteEngine().plan(tasks,advanced,RoutingPreferences.builder().target(AchievementTier.GRANDMASTER).objective(RouteObjective.EASIEST_PROGRESSION).build());
        assertTrue(full.stops().stream().anyMatch(stop->stop.activity().equals("K'ril Tsutsaroth")));
        BossSession kril=full.stops().stream().filter(stop->stop.activity().equals("K'ril Tsutsaroth")).findFirst().orElseThrow();
        assertTrue(ProgressionDifficulty.stage(kril,advanced)<noviceKril);assertAscending(full,advanced);assertUniquePoints(full);
    }

    @Test public void partialProgressWithEligibleKrilStillUsesEnoughEasierRemainingPoints() throws Exception
    {
        List<CombatAchievement> tasks=AchievementRepository.loadBundled().tasks();Set<Integer> completed=new TreeSet<>();
        tasks.stream().filter(task->task.tier()==AchievementTier.EASY).limit(40).forEach(task->completed.add(task.id()));
        tasks.stream().filter(task->task.tier()==AchievementTier.MEDIUM).limit(39).forEach(task->completed.add(task.id()));
        int completedPoints=tasks.stream().filter(task->completed.contains(task.id())).mapToInt(CombatAchievement::points).sum();
        assertEquals("The synthetic account's progress must match its canonical completion ledger",118,completedPoints);
        PlayerProfile account=catalogueProfile(tasks,85,completedPoints,false,completed);
        RoutingPreferences preferences=RoutingPreferences.builder().target(AchievementTier.MEDIUM).objective(RouteObjective.EASIEST_PROGRESSION).build();
        RoutePlan progression=new RouteEngine().plan(tasks,account,preferences);
        assertEquals(51,progression.pointsRemaining());assertTrue(progression.scopeComplete());
        List<TaskAssessment> eligibleKril=new ArrayList<>();
        for(TaskAssessment assessment:progression.assessments())
            if(assessment.task().activity().equals("K'ril Tsutsaroth")&&assessment.eligibility().recommendedNow())eligibleKril.add(assessment);
        assertFalse("This regression must exercise eligible K'ril tasks, not just stat-gated exclusions",eligibleKril.isEmpty());
        int krilStage=eligibleKril.stream().mapToInt(assessment->ProgressionDifficulty.stage(assessment.task(),account)).min().orElseThrow();
        int easierPoints=progression.assessments().stream().filter(assessment->assessment.eligibility().recommendedNow())
            .filter(assessment->ProgressionDifficulty.stage(assessment.task(),account)<krilStage).mapToInt(assessment->assessment.task().points()).sum();
        assertTrue("Enough remaining easier points must be available to cover the goal",easierPoints>=progression.pointsRemaining());
        assertFalse(progression.stops().isEmpty());
        assertTrue(progression.stops().stream().allMatch(stop->ProgressionDifficulty.stage(stop,account)<krilStage));
        assertFalse(progression.stops().stream().anyMatch(stop->stop.activity().equals("K'ril Tsutsaroth")));
        assertTrue(Collections.disjoint(completed,ids(progression)));assertUniquePoints(progression);assertAscending(progression,account);
        assertTrue(progression.projectedPoints()>=progression.pointsRemaining());

        List<String> report=new ArrayList<>();report.add("Synthetic canonical partial-progress scenario; not the user's actual account.");
        report.add("Combat skills: 85; other known skills: 99; curated gear and listed quests confirmed solely for this fixture.");
        report.add("Completed ledger: "+completed.size()+" tasks / "+completedPoints+" points; Medium target: 169 / gap: 51");
        report.add("Completed IDs: "+completed);report.add("Eligible K'ril tasks: "+eligibleKril.size()+"; lowest K'ril stage: "+krilStage+"; lower-stage remaining points: "+easierPoints);
        for(RouteObjective mode:List.of(RouteObjective.IRONMAN_EFFICIENT,RouteObjective.FASTEST_NEXT_TIER,RouteObjective.EASIEST_PROGRESSION))
        {
            RoutePlan plan=new RouteEngine().plan(tasks,account,RoutingPreferences.builder().target(AchievementTier.MEDIUM).objective(mode).build());
            report.add("");report.add("Mode: "+mode.displayName()+"; next: "+(plan.stops().isEmpty()?"none":plan.stops().get(0).activity())
                +"; points: "+plan.projectedPoints()+"; covers gap: "+plan.scopeComplete());
            for(BossSession stop:plan.stops())
            {
                List<Integer> ids=new ArrayList<>();stop.tasks().forEach(assessment->ids.add(assessment.task().id()));
                report.add("  Stage "+ProgressionDifficulty.stage(stop,account)+": "+stop.activity()+" / +"+stop.points()+" points / IDs "+ids);
            }
        }
        Path destination=Path.of("build","scenarios","easy-to-hard-partial-progress.txt");Files.createDirectories(destination.getParent());Files.write(destination,report);
    }

    private static CombatAchievement task(int id,String activity,AchievementTier tier,double minutes,int challenge,int baseline)
    { return CombatAchievement.builder(id,"Task "+id,activity,tier).difficulty(challenge).activityDifficulty(baseline).secondsPerKill(minutes*60)
        .setupMinutes(0).requirementsKnown(true).timingKnown(true).build(); }
    private static PlayerProfile profile(int threshold,int attack)
    { return PlayerProfile.builder().loaded(true).catalogueComplete(true).skills(Map.of("Attack",attack)).thresholds(Map.of(AchievementTier.EASY,threshold)).build(); }
    private static RoutePlan route(List<CombatAchievement> tasks,PlayerProfile profile,Set<Integer> pins)
    { return new RouteEngine().plan(tasks,profile,RoutingPreferences.builder().target(AchievementTier.EASY).objective(RouteObjective.EASIEST_PROGRESSION).pinnedTaskIds(pins).build()); }
    private static Set<Integer> ids(RoutePlan route)
    { Set<Integer> result=new TreeSet<>();route.stops().forEach(stop->stop.tasks().forEach(task->result.add(task.task().id())));return result; }
    private static void assertAscending(RoutePlan route,PlayerProfile profile)
    {
        int previous=0;for(BossSession stop:route.stops()){int stage=ProgressionDifficulty.stage(stop,profile);assertTrue("Stops must progress in difficulty",stage>=previous);previous=stage;}
    }
    private static void assertUniquePoints(RoutePlan route)
    {
        Set<Integer> unique=new HashSet<>();int points=0;
        for(BossSession stop:route.stops())for(TaskAssessment task:stop.tasks()){assertTrue("Task reward cannot appear twice",unique.add(task.task().id()));points+=task.task().points();}
        assertEquals(points,route.projectedPoints());
    }
    private static PlayerProfile catalogueProfile(List<CombatAchievement> tasks,int combat,int currentPoints,boolean experienced)
    { return catalogueProfile(tasks,combat,currentPoints,experienced,Collections.emptySet()); }
    private static PlayerProfile catalogueProfile(List<CombatAchievement> tasks,int combat,int currentPoints,boolean experienced,Set<Integer> completed)
    {
        Map<String,Integer> skills=new TreeMap<>();Map<String,QuestStatus> quests=new TreeMap<>();Set<String> gear=new TreeSet<>();Map<String,Integer> counts=new TreeMap<>();
        for(CombatAchievement task:tasks)
        {
            task.requiredSkills().keySet().forEach(skill->skills.put(skill,99));task.recommendedSkills().keySet().forEach(skill->skills.put(skill,99));
            task.recommendedAlternatives().forEach(group->group.keySet().forEach(skill->skills.put(skill,99)));
            task.quests().keySet().forEach(quest->quests.put(quest,QuestStatus.COMPLETED));gear.addAll(task.recommendedGear());gear.addAll(task.minimumGear());counts.put(task.activity(),0);
        }
        for(String skill:List.of("Attack","Strength","Defence","Ranged","Magic","Hitpoints","Prayer"))skills.put(skill,combat);
        if(experienced)counts.put("K'ril Tsutsaroth",150);
        return PlayerProfile.builder().loaded(true).catalogueComplete(true).currentPoints(currentPoints).completedTaskIds(completed).skills(skills).quests(quests).killCounts(counts)
            .gear(GearProfile.of(Set.of(),Set.of(),gear,false)).thresholds(Map.of(AchievementTier.MEDIUM,169,AchievementTier.GRANDMASTER,2697)).build();
    }
}

package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreparationAdvisorTest
{
    private final PreparationAdvisor adviser = new PreparationAdvisor();
    private static PlayerProfile.Builder player()
    { return PlayerProfile.builder().loaded(true).skills(Map.of("Magic", 50, "Ranged", 85)); }
    private static CombatAchievement.Builder task(int id)
    { return CombatAchievement.builder(id, "Task " + id, "Boss", AchievementTier.EASY).requirementsKnown(true); }
    private static TaskAssessment assessment(CombatAchievement task)
    { return new TaskAssessment(task, new Eligibility(EligibilityState.AVAILABLE, List.of(), Confidence.HIGH, true, false), 0, 0, Map.of(), List.of(), Confidence.HIGH); }
    private static List<TaskAssessment> assessments(CombatAchievement... tasks)
    { List<TaskAssessment> values = new ArrayList<>(); for (CombatAchievement task : tasks) values.add(assessment(task)); return values; }

    @Test public void sharedHardPrerequisitesGroupUniqueRewardsAndLargestTarget()
    {
        CombatAchievement first = task(0).requiredSkills(Map.of("Magic", 60)).build();
        CombatAchievement second = task(1).requiredSkills(Map.of("Magic", 70)).build();
        List<PreparationStep> result = adviser.advise(assessments(first, second), player().build());
        assertEquals(1, result.size()); PreparationStep step = result.get(0);
        assertEquals(PreparationStep.Purpose.HARD_UNLOCK, step.purpose()); assertEquals(70, step.targetLevel());
        assertEquals(Integer.valueOf(50), step.currentLevel()); assertEquals(Set.of(0, 1), step.taskIds()); assertEquals(2, step.potentialPoints());
        try { result.clear(); fail("Immutable advice"); } catch (UnsupportedOperationException expected) { }
        try { step.taskNames().clear(); fail("Immutable task grouping"); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void allExpandsSkillsQuestAndGearWithoutInventedTimes()
    {
        CombatAchievement value = task(0).requirement(Requirement.all(Requirement.skill("Magic", 70),
            Requirement.quest("Example Quest", QuestStatus.COMPLETED), Requirement.gear("weapon"))).build();
        PlayerProfile profile = player().quests(Map.of("Example Quest", QuestStatus.IN_PROGRESS))
            .gear(GearProfile.of(Set.of(), Set.of(), Set.of(), Set.of("weapon"), false)).build();
        List<PreparationStep> result = adviser.advise(assessments(value), profile);
        assertEquals(3, result.size()); assertTrue(result.stream().allMatch(step -> step.purpose() == PreparationStep.Purpose.HARD_UNLOCK));
        assertTrue(result.stream().anyMatch(step -> step.kind() == PreparationStep.Kind.QUEST && step.currentQuestStatus() == QuestStatus.IN_PROGRESS));
    }

    @Test public void satisfiedAnyAlternativeAvoidsFalseTrainingAndGearWork()
    {
        CombatAchievement value = task(0).requirement(Requirement.any(Requirement.skill("Magic", 90),
            Requirement.all(Requirement.skill("Ranged", 80), Requirement.gear("weapon")))).build();
        PlayerProfile profile = player().gear(GearProfile.of(Set.of(), Set.of(), Set.of("weapon"), false)).build();
        assertTrue(adviser.advise(assessments(value), profile).isEmpty());
    }

    @Test public void unknownAlternativeIsVerificationRatherThanHardLockFromAnotherBranch()
    {
        CombatAchievement value = task(0).requirement(Requirement.any(Requirement.skill("Magic", 90), Requirement.gear("weapon"))).build();
        List<PreparationStep> result = adviser.advise(assessments(value), player().build());
        assertEquals(1, result.size()); assertEquals(PreparationStep.Kind.GEAR, result.get(0).kind());
        assertEquals(PreparationStep.Purpose.VERIFY_REQUIREMENT, result.get(0).purpose());
        assertTrue(result.get(0).unknown()); assertFalse(result.get(0).notes().isEmpty());
    }

    @Test public void allUnmetAnyChoosesDeterministicAlternativeWithExplicitContext()
    {
        Requirement left = Requirement.skill("Magic", 80), right = Requirement.skill("Ranged", 95);
        CombatAchievement first = task(0).requirement(Requirement.any(left, right)).build();
        CombatAchievement reordered = task(0).requirement(Requirement.any(right, left)).build();
        PreparationStep result = adviser.advise(assessments(first), player().build()).get(0);
        assertEquals(PreparationStep.Purpose.HARD_UNLOCK, result.purpose());
        assertTrue(result.notes().get(0).contains("One supported requirement alternative"));
        assertEquals(result.description(), adviser.advise(assessments(reordered), player().build()).get(0).description());
    }

    @Test public void softReadinessStaysSeparateAndPrefersSatisfiedCombatStyle()
    {
        CombatAchievement value = task(0).requiredSkills(Map.of("Magic", 60)).recommendedSkills(Map.of("Magic", 75))
            .recommendedAlternatives(List.of(Map.of("Magic", 90, "Ranged", 80))).recommendedGear(Set.of("armour")).build();
        List<PreparationStep> result = adviser.advise(assessments(value), player().gear(GearProfile.of(Set.of(), Set.of(), Set.of(), Set.of("armour"), false)).build());
        assertEquals(3, result.size()); assertEquals(PreparationStep.Purpose.HARD_UNLOCK, result.get(0).purpose());
        assertTrue(result.stream().anyMatch(step -> step.purpose() == PreparationStep.Purpose.SOFT_READINESS && step.targetLevel() == 75));
        assertFalse(result.stream().anyMatch(step -> step.key().equals("Ranged")));
        assertFalse(result.stream().anyMatch(step -> step.targetLevel() == 90));
    }

    @Test public void missingProfileFactsAndIncompleteMetadataNeverBecomeMissingUnlocks()
    {
        CombatAchievement value = task(0).requirementsKnown(false).requiredSkills(Map.of("Attack", 60))
            .quests(Map.of("Example Quest", QuestStatus.COMPLETED)).minimumGear(Set.of("weapon"))
            .recommendedSkills(Map.of("Defence", 70)).build();
        List<PreparationStep> result = adviser.advise(assessments(value), player().build());
        assertEquals(5, result.size()); assertTrue(result.stream().allMatch(PreparationStep::unknown));
        assertFalse(result.stream().anyMatch(step -> step.purpose() == PreparationStep.Purpose.HARD_UNLOCK));
        assertTrue(result.stream().anyMatch(step -> step.kind() == PreparationStep.Kind.ACCESS));
    }

    @Test public void hardUnlockAlreadyCoveringSoftTargetAvoidsDuplicatePreparation()
    {
        CombatAchievement value = task(0).requiredSkills(Map.of("Magic", 80)).recommendedSkills(Map.of("Magic", 70))
            .minimumGear(Set.of("weapon")).recommendedGear(Set.of("weapon")).build();
        List<PreparationStep> result = adviser.advise(assessments(value), player().build());
        assertEquals(2, result.size()); assertTrue(result.stream().allMatch(PreparationStep::hardRequirement));
        assertFalse(result.stream().anyMatch(step -> step.purpose() == PreparationStep.Purpose.SOFT_READINESS));
    }

    @Test public void completedExcludedAndDeferredTasksDoNotInflatePreparationRewards()
    {
        CombatAchievement value = task(0).requiredSkills(Map.of("Magic", 70)).build();
        assertTrue(adviser.advise(assessments(value), player().completedTaskIds(Set.of(0)).build()).isEmpty());
        for (EligibilityState state : List.of(EligibilityState.COMPLETED, EligibilityState.CONTENT_EXCLUDED, EligibilityState.DEFERRED))
        {
            TaskAssessment ignored = new TaskAssessment(value, new Eligibility(state, List.of(), Confidence.HIGH, false, false), 0, 0, Map.of(), List.of(), Confidence.HIGH);
            assertTrue(adviser.advise(List.of(ignored), player().build()).isEmpty());
        }
    }
}

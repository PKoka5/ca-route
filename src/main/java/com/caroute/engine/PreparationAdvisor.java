package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

/** Pure prerequisite/readiness advice. It makes no training, acquisition-time or instant-unlock promises. */
public final class PreparationAdvisor
{
    private enum Fact { SATISFIED, UNKNOWN, UNMET }
    private static final class Draft
    {
        final PreparationStep.Kind kind;
        final PreparationStep.Purpose purpose;
        final String key;
        int targetLevel;
        final Integer currentLevel;
        QuestStatus targetQuestStatus;
        final QuestStatus currentQuestStatus;
        final Set<String> notes = new TreeSet<>();
        final Map<Integer, String> taskNames = new TreeMap<>();
        final Map<Integer, Integer> taskPoints = new TreeMap<>();
        final Set<String> activities = new TreeSet<>();
        Draft(PreparationStep.Kind kind, PreparationStep.Purpose purpose, String key, int targetLevel,
            Integer currentLevel, QuestStatus targetQuestStatus, QuestStatus currentQuestStatus)
        { this.kind = kind; this.purpose = purpose; this.key = key; this.targetLevel = targetLevel;
            this.currentLevel = currentLevel; this.targetQuestStatus = targetQuestStatus; this.currentQuestStatus = currentQuestStatus; }
        String groupKey() { return purpose.name() + "\u0000" + kind.name() + "\u0000" + key; }
        PreparationStep build()
        { return new PreparationStep(kind, purpose, key, targetLevel, currentLevel, targetQuestStatus, currentQuestStatus,
            taskNames, taskPoints, activities, new ArrayList<>(notes)); }
    }
    private static final class Evaluation
    {
        final Fact fact;
        final List<Draft> steps;
        final String identity;
        Evaluation(Fact fact, List<Draft> steps, String identity) { this.fact = fact; this.steps = steps; this.identity = identity; }
    }

    public List<PreparationStep> advise(List<TaskAssessment> assessments, PlayerProfile profile)
    {
        Objects.requireNonNull(assessments); Objects.requireNonNull(profile);
        Map<Integer, TaskAssessment> unique = new TreeMap<>();
        for (TaskAssessment assessment : assessments)
            if (unique.put(assessment.task().id(), assessment) != null) throw new IllegalArgumentException("Duplicate preparation task " + assessment.task().id());
        Map<String, Draft> grouped = new TreeMap<>();
        for (TaskAssessment assessment : unique.values())
        {
            CombatAchievement task = assessment.task(); EligibilityState state = assessment.eligibility().state();
            if (profile.completedTaskIds().contains(task.id()) || state == EligibilityState.COMPLETED
                || state == EligibilityState.CONTENT_EXCLUDED || state == EligibilityState.DEFERRED) continue;
            List<Draft> steps = new ArrayList<>(evaluate(task.requirement(), profile).steps);
            for (Map.Entry<String, Integer> level : Readiness.levels(task, profile).entrySet())
            {
                Integer current = profile.skills().get(level.getKey());
                if (current != null && current >= level.getValue()) continue;
                if (steps.stream().anyMatch(step -> step.kind == PreparationStep.Kind.SKILL && hard(step.purpose)
                    && step.key.equals(level.getKey()) && step.targetLevel >= level.getValue())) continue;
                Draft step = new Draft(PreparationStep.Kind.SKILL, current == null ? PreparationStep.Purpose.VERIFY_READINESS
                    : PreparationStep.Purpose.SOFT_READINESS, level.getKey(), level.getValue(), current, null, null);
                if (task.recommendedAlternatives().stream().anyMatch(group -> group.containsKey(level.getKey())))
                    step.notes.add("Readiness uses one supported combat style chosen from your known levels; other styles can also work.");
                steps.add(step);
            }
            for (String capability : new TreeSet<>(task.recommendedGear()))
            {
                GearProfile.CapabilityState ownership = profile.gear().capability(capability);
                if (ownership == GearProfile.CapabilityState.PRESENT) continue;
                if (steps.stream().anyMatch(step -> step.kind == PreparationStep.Kind.GEAR && hard(step.purpose) && step.key.equals(capability))) continue;
                steps.add(new Draft(PreparationStep.Kind.GEAR, ownership == GearProfile.CapabilityState.MISSING
                    ? PreparationStep.Purpose.SOFT_READINESS : PreparationStep.Purpose.VERIFY_READINESS, capability, 0, null, null, null));
            }
            if (!task.requirementsKnown())
            {
                Draft step = new Draft(PreparationStep.Kind.ACCESS, PreparationStep.Purpose.VERIFY_REQUIREMENT, task.activity(), 0, null, null, null);
                step.notes.add("Unknown access metadata is a verification step, not a known game lock.");
                if (task.recommendedSkills().isEmpty() && task.recommendedAlternatives().isEmpty() && task.recommendedGear().isEmpty())
                    step.notes.add("Recommended combat readiness is not curated for these tasks; review their strategy requirements.");
                steps.add(step);
            }
            for (Draft incoming : steps)
            {
                Draft row = grouped.computeIfAbsent(incoming.groupKey(), ignored -> incoming);
                row.targetLevel = Math.max(row.targetLevel, incoming.targetLevel);
                if (incoming.targetQuestStatus != null && (row.targetQuestStatus == null || incoming.targetQuestStatus.ordinal() > row.targetQuestStatus.ordinal()))
                    row.targetQuestStatus = incoming.targetQuestStatus;
                row.notes.addAll(incoming.notes); row.taskNames.put(task.id(), task.name()); row.taskPoints.put(task.id(), task.points()); row.activities.add(task.activity());
            }
        }
        List<PreparationStep> result = new ArrayList<>(); grouped.values().forEach(value -> result.add(value.build()));
        result.sort(Comparator.comparing(PreparationStep::purpose).thenComparing(PreparationStep::kind)
            .thenComparing(Comparator.comparingInt(PreparationStep::potentialPoints).reversed())
            .thenComparing(PreparationStep::key).thenComparingInt(PreparationStep::targetLevel));
        return Collections.unmodifiableList(result);
    }

    private Evaluation evaluate(Requirement requirement, PlayerProfile profile)
    {
        String identity = describe(requirement);
        switch (requirement.kind())
        {
            case SKILL:
                Integer skill = profile.skills().get(requirement.key());
                if (skill != null && skill >= requirement.level()) return satisfied(identity);
                return leaf(skill == null, new Draft(PreparationStep.Kind.SKILL, skill == null ? PreparationStep.Purpose.VERIFY_REQUIREMENT
                    : PreparationStep.Purpose.HARD_UNLOCK, requirement.key(), requirement.level(), skill, null, null), identity);
            case QUEST:
                QuestStatus quest = profile.quests().get(requirement.key());
                if (quest != null && quest.ordinal() >= requirement.questStatus().ordinal()) return satisfied(identity);
                return leaf(quest == null, new Draft(PreparationStep.Kind.QUEST, quest == null ? PreparationStep.Purpose.VERIFY_REQUIREMENT
                    : PreparationStep.Purpose.HARD_UNLOCK, requirement.key(), 0, null, requirement.questStatus(), quest), identity);
            case GEAR:
                GearProfile.CapabilityState gear = profile.gear().capability(requirement.key());
                if (gear == GearProfile.CapabilityState.PRESENT) return satisfied(identity);
                return leaf(gear == GearProfile.CapabilityState.UNKNOWN, new Draft(PreparationStep.Kind.GEAR, gear == GearProfile.CapabilityState.UNKNOWN
                    ? PreparationStep.Purpose.VERIFY_REQUIREMENT : PreparationStep.Purpose.HARD_UNLOCK, requirement.key(), 0, null, null, null), identity);
            default:
                List<Evaluation> children = new ArrayList<>();
                for (Requirement child : requirement.children()) children.add(evaluate(child, profile));
                if (requirement.kind() == Requirement.Kind.ANY)
                {
                    if (children.stream().anyMatch(child -> child.fact == Fact.SATISFIED)) return satisfied(identity);
                    // Unknown alternatives may already suffice; never turn a different unmet branch into a false lock.
                    Fact bestFact = children.stream().anyMatch(child -> child.fact == Fact.UNKNOWN) ? Fact.UNKNOWN : Fact.UNMET;
                    Evaluation selected = children.stream().filter(child -> child.fact == bestFact)
                        .min(Comparator.comparingInt((Evaluation child) -> distinctSteps(child.steps)).thenComparing(child -> child.identity)).orElseThrow(IllegalStateException::new);
                    for (Draft step : selected.steps) step.notes.add("One supported requirement alternative: " + selected.identity
                        + ". Other options: " + identity + ". Selection is not a time estimate.");
                    return new Evaluation(bestFact, selected.steps, identity);
                }
                List<Draft> steps = new ArrayList<>(); children.forEach(child -> steps.addAll(child.steps));
                Fact fact = children.stream().anyMatch(child -> child.fact == Fact.UNMET) ? Fact.UNMET
                    : children.stream().anyMatch(child -> child.fact == Fact.UNKNOWN) ? Fact.UNKNOWN : Fact.SATISFIED;
                return new Evaluation(fact, steps, identity);
        }
    }
    private static Evaluation satisfied(String identity) { return new Evaluation(Fact.SATISFIED, Collections.emptyList(), identity); }
    private static boolean hard(PreparationStep.Purpose purpose)
    { return purpose == PreparationStep.Purpose.HARD_UNLOCK || purpose == PreparationStep.Purpose.VERIFY_REQUIREMENT; }
    private static Evaluation leaf(boolean unknown, Draft step, String identity)
    { return new Evaluation(unknown ? Fact.UNKNOWN : Fact.UNMET, Collections.singletonList(step), identity); }
    private static int distinctSteps(List<Draft> steps)
    { Set<String> keys = new HashSet<>(); steps.forEach(step -> keys.add(step.groupKey())); return keys.size(); }
    private static String describe(Requirement requirement)
    {
        switch (requirement.kind())
        {
            case SKILL: return requirement.key() + " " + requirement.level();
            case QUEST: return requirement.key() + " " + requirement.questStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            case GEAR: return "gear " + requirement.key();
            default:
                List<String> children = new ArrayList<>(); requirement.children().forEach(child -> children.add(describe(child))); Collections.sort(children);
                return "(" + String.join(requirement.kind() == Requirement.Kind.ANY ? " OR " : " AND ", children) + ")";
        }
    }
}

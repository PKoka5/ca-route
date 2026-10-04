package com.caroute.domain;

import java.util.*;

/** A prerequisite or readiness action, with related task rewards rather than guaranteed unlocks. */
public final class PreparationStep
{
    public enum Kind { SKILL, QUEST, GEAR, ACCESS, READINESS }
    public enum Purpose { HARD_UNLOCK, VERIFY_REQUIREMENT, SOFT_READINESS, VERIFY_READINESS }
    private final Kind kind;
    private final Purpose purpose;
    private final String key;
    private final int targetLevel;
    private final Integer currentLevel;
    private final QuestStatus targetQuestStatus, currentQuestStatus;
    private final Map<Integer, String> taskNames;
    private final Map<Integer, Integer> taskPoints;
    private final Set<Integer> taskIds;
    private final Set<String> activities;
    private final List<String> notes;

    public PreparationStep(Kind kind, Purpose purpose, String key, int targetLevel, Integer currentLevel,
        QuestStatus targetQuestStatus, QuestStatus currentQuestStatus, Map<Integer, String> taskNames,
        Map<Integer, Integer> taskPoints, Set<String> activities, List<String> notes)
    {
        this.kind = Objects.requireNonNull(kind); this.purpose = Objects.requireNonNull(purpose);
        this.key = Objects.requireNonNull(key); this.targetLevel = targetLevel; this.currentLevel = currentLevel;
        this.targetQuestStatus = targetQuestStatus; this.currentQuestStatus = currentQuestStatus;
        if (kind == Kind.SKILL && (targetLevel < 1 || targetLevel > 99)) throw new IllegalArgumentException("Invalid preparation level");
        if (currentLevel != null && (currentLevel < 1 || currentLevel > 99)) throw new IllegalArgumentException("Invalid current level");
        if (kind == Kind.QUEST && targetQuestStatus == null) throw new IllegalArgumentException("Quest preparation needs a target status");
        if (!taskNames.keySet().equals(taskPoints.keySet())) throw new IllegalArgumentException("Preparation task identity/reward mismatch");
        taskPoints.values().forEach(value -> { if (value < 1) throw new IllegalArgumentException("Invalid related task points"); });
        this.taskNames = Immutable.map(new TreeMap<>(taskNames)); this.taskPoints = Immutable.map(new TreeMap<>(taskPoints));
        this.taskIds = Immutable.set(new TreeSet<>(taskNames.keySet())); this.activities = Immutable.set(new TreeSet<>(activities));
        this.notes = Immutable.list(new ArrayList<>(new TreeSet<>(notes)));
    }
    public Kind kind() { return kind; }
    public Purpose purpose() { return purpose; }
    public String key() { return key; }
    public int targetLevel() { return targetLevel; }
    public Integer currentLevel() { return currentLevel; }
    public QuestStatus targetQuestStatus() { return targetQuestStatus; }
    public QuestStatus currentQuestStatus() { return currentQuestStatus; }
    public Map<Integer, String> taskNames() { return taskNames; }
    public Set<Integer> taskIds() { return taskIds; }
    public Set<String> activities() { return activities; }
    public List<String> notes() { return notes; }
    public int potentialPoints() { return taskPoints.values().stream().mapToInt(Integer::intValue).sum(); }
    public boolean hardRequirement() { return purpose == Purpose.HARD_UNLOCK || purpose == Purpose.VERIFY_REQUIREMENT; }
    public boolean unknown() { return purpose == Purpose.VERIFY_REQUIREMENT || purpose == Purpose.VERIFY_READINESS; }

    public String description()
    {
        String suffix = purpose == Purpose.SOFT_READINESS || purpose == Purpose.VERIFY_READINESS ? " (readiness advice)" : "";
        switch (kind)
        {
            case SKILL:
                return (unknown() ? "Verify " : purpose == Purpose.HARD_UNLOCK ? "Train " : "Improve ") + key
                    + (unknown() ? " level against " : " toward ") + targetLevel
                    + (currentLevel == null ? "; current level unknown" : "; current " + currentLevel) + suffix;
            case QUEST:
                String action = targetQuestStatus == QuestStatus.COMPLETED ? "Complete " : "Start ";
                return unknown() ? "Verify " + key + " quest progress; needs " + targetQuestStatus.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    : action + key + "; current " + currentQuestStatus.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            case GEAR:
                return (unknown() ? "Confirm gear capability: " : purpose == Purpose.HARD_UNLOCK ? "Obtain required gear capability: "
                    : "Consider recommended gear capability: ") + key.replace('-', ' ').replace('_', ' ') + suffix;
            case ACCESS: return "Check access and task conditions for " + key + "; imported requirements are incomplete";
            default: return "Check combat readiness for " + key + "; recommended requirements are unknown";
        }
    }
}

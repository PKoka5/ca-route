package com.caroute.domain;

import java.util.*;

/** Typed access requirements. Missing profile facts are evaluated as unknown. */
public final class Requirement
{
    public enum Kind { ALL, ANY, SKILL, QUEST, GEAR }
    private final Kind kind;
    private final String key;
    private final int level;
    private final QuestStatus questStatus;
    private final List<Requirement> children;
    private Requirement(Kind kind, String key, int level, QuestStatus status, List<Requirement> children)
    { this.kind = kind; this.key = key; this.level = level; this.questStatus = status; this.children = Immutable.list(children); }
    public static Requirement skill(String skill, int level) { if (level < 1 || level > 99) throw new IllegalArgumentException("Invalid skill level"); return new Requirement(Kind.SKILL, Objects.requireNonNull(skill), level, null, Collections.emptyList()); }
    public static Requirement quest(String quest, QuestStatus status) { return new Requirement(Kind.QUEST, Objects.requireNonNull(quest), 0, Objects.requireNonNull(status), Collections.emptyList()); }
    public static Requirement gear(String capability) { return new Requirement(Kind.GEAR, Objects.requireNonNull(capability), 0, null, Collections.emptyList()); }
    public static Requirement all(Requirement... children) { return group(Kind.ALL, children); }
    public static Requirement any(Requirement... children) { if (children.length == 0) throw new IllegalArgumentException("ANY requires alternatives"); return group(Kind.ANY, children); }
    private static Requirement group(Kind kind, Requirement[] children) { for (Requirement child : children) Objects.requireNonNull(child); return new Requirement(kind, null, 0, null, Arrays.asList(children)); }
    public Kind kind() { return kind; }
    public String key() { return key; }
    public int level() { return level; }
    public QuestStatus questStatus() { return questStatus; }
    public List<Requirement> children() { return children; }
}

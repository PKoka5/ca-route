package com.caroute.navigation;

import com.caroute.domain.CombatAchievement;
import com.caroute.ui.GuideView;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable render snapshot. Selection is transient and never changes game completion. */
public final class ActiveGuide
{
    // Catalogue encounter labels can differ from actual NPC names. These exact
    // aliases identify visible encounter members, never attack states or timing.
    // Barrows, Moons and the DT2 names: Quest Helper commit
    // 75b623a6bc14237831fddc10b44765c0910a4eb0, HisFaithfulServants.java:429-434,
    // PerilousMoon.java:624-626 and DesertTreasureII.java:821-824.
    // Guardians: https://oldschool.runescape.com/polls/2020/1603
    // Titans: https://oldschool.runescape.wiki/w/Royal_Titans
    private static final Map<String, Set<String>> NPC_ALIASES = Map.of(
        "grotesque guardians", Set.of("dawn", "dusk"),
        "royal titans", Set.of("eldric the ice king", "branda the fire queen"),
        "moons of peril", Set.of("blue moon", "blood moon", "eclipse moon"),
        "barrows", Set.of("ahrim the blighted", "dharok the wretched", "guthan the infested",
            "karil the tainted", "torag the corrupted", "verac the defiled"),
        "leviathan", Set.of("the leviathan"),
        "whisperer", Set.of("the whisperer"));
    private final CombatAchievement task;
    private final String activity, status, caution;
    private final ActivityDestinations.Destination destination;
    private final Integer killCount;

    ActiveGuide(CombatAchievement task, String activity, ActivityDestinations.Destination destination,
        String status, Integer killCount, String caution)
    {
        this.task = task; this.activity = Objects.requireNonNull(activity); this.destination = destination;
        this.status = Objects.requireNonNull(status); this.killCount = killCount; this.caution = caution;
    }
    public CombatAchievement task() { return task; }
    public String activity() { return activity; }
    public String title() { return task == null ? activity : task.name(); }
    public String condition() { return task == null ? "Choose a task to show its exact condition." : task.description(); }
    public ActivityDestinations.Destination destination() { return destination; }
    public String status() { return status; }
    public String caution() { return caution; }
    public Integer killCount() { return killCount; }
    public boolean matchesNpc(String name)
    {
        if (name == null) return false;
        String encounter = normalize(activity), npc = normalize(name);
        return encounter.equals(npc) || NPC_ALIASES.getOrDefault(encounter, Set.of()).contains(npc);
    }
    private static String normalize(String value)
    { return value.replace('\u2019', '\'').trim().toLowerCase(Locale.ROOT); }
    ActiveGuide withFacts(String status, Integer count, String caution)
    { return new ActiveGuide(task, activity, destination, status, count, caution); }
    public GuideView view()
    {
        String place = destination == null ? "Destination not yet verified" : destination.label();
        return new GuideView(title(), activity, task == null ? null : task.id(), condition(), place,
            status, destination != null, destination == null ? "" : destination.instructions());
    }
}

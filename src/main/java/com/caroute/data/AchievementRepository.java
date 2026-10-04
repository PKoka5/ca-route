package com.caroute.data;

import com.caroute.domain.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict canonical loading; estimates and incomplete requirements preserve their provenance. */
public final class AchievementRepository
{
    private final List<CombatAchievement> tasks;
    private final List<String> diagnostics;
    private final String version;
    private final boolean complete;
    private AchievementRepository(List<CombatAchievement> tasks, List<String> diagnostics, String version, boolean complete)
    { this.tasks = Collections.unmodifiableList(tasks); this.diagnostics = Collections.unmodifiableList(diagnostics); this.version = version; this.complete = complete; }
    public static AchievementRepository loadBundled()
    {
        InputStream in = AchievementRepository.class.getResourceAsStream("/com/caroute/achievements.json");
        if (in == null) throw new IllegalStateException("Bundled CA metadata is missing");
        return load(in);
    }
    public static AchievementRepository load(InputStream in)
    {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported metadata schema");
            List<CombatAchievement> tasks = new ArrayList<>(); List<String> diagnostics = strings(root.getAsJsonArray("diagnostics")); Set<Integer> ids = new HashSet<>();
            GearCapabilityRepository gearRules = GearCapabilityRepository.loadBundled();
            diagnostics.add("Gear overlay " + gearRules.version() + ": initial curated subset; soft preparation recommendations do not guarantee task speed or full bank coverage");
            for (JsonElement element : root.getAsJsonArray("tasks"))
            {
                JsonObject row = element.getAsJsonObject(); int id = row.get("id").getAsInt();
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate task " + id);
                CombatAchievement.Builder b = CombatAchievement.builder(id, row.get("name").getAsString(), row.get("activity").getAsString(), AchievementTier.valueOf(row.get("tier").getAsString()))
                    .description(row.get("description").getAsString()).type(row.get("type").getAsString()).wikiUrl(row.get("wikiUrl").getAsString())
                    .requiredSkills(levels(row.getAsJsonObject("requiredSkills"))).recommendedSkills(levels(row.getAsJsonObject("recommendedSkills")))
                    .minimumGear(new TreeSet<>(strings(row.getAsJsonArray("minimumGear")))).recommendedGear(new TreeSet<>(strings(row.getAsJsonArray("recommendedGear"))))
                    .kcThreshold(row.get("kcThreshold").getAsInt()).completionCount(row.get("completionCount").getAsInt())
                    .secondsPerKill(row.get("secondsPerKill").getAsDouble()).baseAttempts(row.get("baseAttempts").getAsDouble())
                    .difficulty(row.get("difficulty").getAsInt())
                    .activityDifficulty(row.has("activityDifficulty")?row.get("activityDifficulty").getAsInt():row.get("difficulty").getAsInt())
                    .setupMinutes(row.get("setupMinutes").getAsDouble())
                    .requirementsKnown(row.get("requirementsKnown").getAsBoolean()).timingKnown(row.get("timingKnown").getAsBoolean())
                    .overlapGroup(row.get("overlapGroup").getAsString()).provenance(row.get("provenance").getAsString());
                Map<String, QuestStatus> quests = new TreeMap<>(); row.getAsJsonObject("quests").entrySet().forEach(e -> quests.put(e.getKey(), QuestStatus.valueOf(e.getValue().getAsString()))); b.quests(quests);
                Set<TaskFlag> flags = EnumSet.noneOf(TaskFlag.class); for (String flag : strings(row.getAsJsonArray("flags"))) flags.add(TaskFlag.valueOf(flag)); b.flags(flags);
                b.requirement(requirement(row.getAsJsonObject("requirement")));
                // Preserve alternative-style recommendations instead of requiring every style.
                List<Map<String, Integer>> alternatives = new ArrayList<>();
                for (JsonElement alternative : row.getAsJsonArray("recommendedAlternatives"))
                {
                    Map<String, Integer> group = new TreeMap<>(); JsonObject r = alternative.getAsJsonObject();
                    for (String skill : strings(r.getAsJsonArray("skills"))) group.put(skill, r.get("level").getAsInt());
                    alternatives.add(group);
                }
                b.recommendedAlternatives(alternatives);
                if (row.has("playerPreparation")) b.playerPreparation(strings(row.getAsJsonArray("playerPreparation")));
                if (row.has("quickGuide")) b.quickGuide(strings(row.getAsJsonArray("quickGuide")));
                Set<String> recommendedGear = new TreeSet<>(strings(row.getAsJsonArray("recommendedGear")));
                recommendedGear.addAll(gearRules.recommendedFor(id, row.get("name").getAsString(), row.get("activity").getAsString()));
                b.recommendedGear(recommendedGear);
                for (String diagnostic : strings(row.getAsJsonArray("diagnostics"))) diagnostics.add("Task " + id + ": " + diagnostic);
                tasks.add(b.build());
            }
            if (tasks.size() != root.get("taskCount").getAsInt()) throw new IllegalArgumentException("Catalogue count mismatch");
            tasks.sort(Comparator.comparingInt(CombatAchievement::id));
            for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id() != i) throw new IllegalArgumentException("Canonical completion-index coverage is incomplete");
            return new AchievementRepository(tasks, diagnostics, root.get("datasetVersion").getAsString(), root.get("catalogueComplete").getAsBoolean());
        }
        catch (IOException | RuntimeException ex) { throw new IllegalArgumentException("Cannot load validated CA metadata: " + ex.getMessage(), ex); }
    }
    private static Map<String, Integer> levels(JsonObject object)
    {
        Map<String, Integer> levels = new TreeMap<>(); object.entrySet().forEach(e -> {
            int value = e.getValue().getAsInt(); if (value < 1 || value > 99) throw new IllegalArgumentException("Invalid skill requirement"); levels.put(e.getKey(), value);
        }); return levels;
    }
    private static List<String> strings(JsonArray array) { List<String> values = new ArrayList<>(); for (JsonElement e : array) values.add(e.getAsString()); return values; }
    private static Requirement requirement(JsonObject row)
    {
        Requirement.Kind kind = Requirement.Kind.valueOf(row.get("kind").getAsString());
        switch (kind)
        {
            case SKILL: return Requirement.skill(row.get("key").getAsString(), row.get("level").getAsInt());
            case QUEST: return Requirement.quest(row.get("key").getAsString(), QuestStatus.valueOf(row.get("status").getAsString()));
            case GEAR: return Requirement.gear(row.get("key").getAsString());
            default:
                List<Requirement> children = new ArrayList<>(); for (JsonElement child : row.getAsJsonArray("children")) children.add(requirement(child.getAsJsonObject()));
                return kind == Requirement.Kind.ANY ? Requirement.any(children.toArray(new Requirement[0])) : Requirement.all(children.toArray(new Requirement[0]));
        }
    }
    public List<CombatAchievement> tasks() { return tasks; }
    public List<String> diagnostics() { return diagnostics; }
    public String version() { return version; }
    public boolean catalogueComplete() { return complete; }
}

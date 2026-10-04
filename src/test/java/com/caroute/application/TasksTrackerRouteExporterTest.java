package com.caroute.application;

import com.caroute.domain.*;
import com.caroute.engine.PreparationAdvisor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compatibility DTOs mirror the pinned public JSON fields; this does not execute the third-party GUI importer. */
public class TasksTrackerRouteExporterTest
{
    private final Gson gson = new Gson();
    private final TasksTrackerRouteExporter exporter = new TasksTrackerRouteExporter(gson);
    private static TaskAssessment assessment(int id, String activity)
    {
        CombatAchievement task = CombatAchievement.builder(id, "Task " + id, activity, AchievementTier.EASY)
            .description("Exact static condition").requirementsKnown(true).build();
        return new TaskAssessment(task, new Eligibility(EligibilityState.AVAILABLE, List.of(), Confidence.MEDIUM, true, false), 2, 2, Map.of(), List.of(), Confidence.MEDIUM);
    }
    private static RoutePlan fixture()
    {
        TaskAssessment second = assessment(2, "First boss"), first = assessment(0, "First boss"), third = assessment(1, "Next boss");
        return new RoutePlan(AchievementTier.EASY, 0, 3, 3, 3, 6,
            List.of(new BossSession("First boss", List.of(second, first), 2, 4, 4, List.of(), Confidence.LOW, true),
                new BossSession("Next boss", List.of(third), 1, 2, 2, List.of(), Confidence.MEDIUM, false)),
            List.of(first, third, second), List.of(), Reachability.CONDITIONAL, Confidence.LOW, true, 1);
    }
    private static Map<Integer, Integer> mappings() { return Map.of(0, 91000, 1, 91001, 2, 91002); }

    @Test public void deterministicExportPreservesSessionOrderAndUsesStructIds()
    {
        TasksTrackerRouteExporter.ExportResult result = exporter.export(fixture(), mappings());
        assertTrue(result.success()); assertEquals(3, result.taskCount()); assertEquals(1, result.preparationCount());
        assertEquals(result.json(), exporter.export(fixture(), new TreeMap<>(mappings())).json());
        CompatibleRoute imported = gson.fromJson(result.json(), CompatibleRoute.class);
        assertEquals("COMBAT", imported.taskType); assertNotNull(UUID.fromString(imported.id)); assertFalse(imported.name.isEmpty());
        assertEquals(List.of(91002, 91000, 91001), imported.flattenedTasks());
        assertEquals("First boss", imported.sections.get(0).name); assertEquals("Next boss", imported.sections.get(1).name);
        assertTrue(imported.sections.get(0).items.get(0).customItem.label.contains("Check access"));
        assertFalse(result.json().contains("accountKey")); assertFalse(result.json().contains("profileVersion"));
    }

    @Test public void pinnedItemsSchemaRoundTripsWithExactlyOneTaskOrCustomField()
    {
        String json = exporter.export(fixture(), mappings()).json();
        CompatibleRoute imported = gson.fromJson(json, CompatibleRoute.class);
        Set<Integer> ids = new HashSet<>(); Set<String> customIds = new HashSet<>(); Set<String> sections = new HashSet<>();
        for (CompatibleSection section : imported.sections)
        {
            assertTrue(sections.add(section.id)); assertNotNull(UUID.fromString(section.id)); assertNotNull(section.items); assertFalse(section.name.isEmpty());
            for (CompatibleItem item : section.items)
            {
                assertTrue((item.taskId != null) != (item.customItem != null));
                if (item.taskId != null) assertTrue(ids.add(item.taskId));
                else { assertTrue(customIds.add(item.customItem.id)); assertNotNull(UUID.fromString(item.customItem.id)); assertFalse(item.customItem.label.isEmpty()); }
            }
        }
        JsonElement original = new JsonParser().parse(json), roundTrip = new JsonParser().parse(gson.toJson(imported));
        assertEquals(original, roundTrip); assertFalse(json.contains("taskIds"));
    }

    @Test public void scopedModesDescribeBossOrBudgetInsteadOfImplyingTierCoverage()
    {
        RoutePlan budget = fixture().withScope(RouteObjective.MAXIMUM_POINTS_PER_HOUR, "", 45, false);
        CompatibleRoute budgetJson = gson.fromJson(exporter.export(budget, mappings()).json(), CompatibleRoute.class);
        assertTrue(budgetJson.name.contains("45 minute session"));
        RoutePlan boss = fixture().withScope(RouteObjective.BOSS_COMPLETION, "First boss", 60, false);
        CompatibleRoute bossJson = gson.fromJson(exporter.export(boss, mappings()).json(), CompatibleRoute.class);
        assertEquals("CA Route - First boss", bossJson.name);
    }

    @Test public void missingAndAliasedMappingsNeverExportGuessedIds()
    {
        TasksTrackerRouteExporter.ExportResult missing = exporter.export(fixture(), Map.of(0, 91000, 1, 91001));
        assertFalse(missing.success()); assertTrue(missing.json().isEmpty()); assertTrue(missing.error().contains("CA 2"));
        TasksTrackerRouteExporter.ExportResult aliased = exporter.export(fixture(), Map.of(0, 91000, 1, 91001, 2, 91000));
        assertFalse(aliased.success()); assertTrue(aliased.error().contains("same Tasks Tracker struct"));
    }

    @Test public void unknownProfileAndDuplicateRouteTasksAreRefused()
    {
        RoutePlan unknown = new RoutePlan(AchievementTier.EASY, 0, 1, 1, 0, 0, List.of(), List.of(), List.of(), Reachability.UNKNOWN_PROFILE, Confidence.LOW, false, 1);
        assertFalse(exporter.export(unknown, mappings()).success());
        TaskAssessment task = assessment(0, "Boss");
        RoutePlan duplicate = new RoutePlan(AchievementTier.EASY, 0, 2, 2, 2, 4,
            List.of(new BossSession("Boss", List.of(task, task), 2, 4, 4, List.of(), Confidence.HIGH, false)),
            List.of(task), List.of(), Reachability.REACHABLE_NOW, Confidence.HIGH, true, 1);
        assertFalse(exporter.export(duplicate, mappings()).success());
    }

    @Test public void routeScopedPreparationExportsCustomItemsAndRejectsBroaderTrainingTargets()
    {
        TaskAssessment chosen = new TaskAssessment(CombatAchievement.builder(0, "Task 0", "First boss", AchievementTier.EASY)
            .requiredSkills(Map.of("Magic", 70)).requirementsKnown(true).build(),
            new Eligibility(EligibilityState.SKILL_LOCKED, List.of(), Confidence.HIGH, false, false), 0, 0, Map.of(), List.of(), Confidence.HIGH);
        PlayerProfile profile = PlayerProfile.builder().loaded(true).skills(Map.of("Magic", 50)).build();
        List<PreparationStep> advice = new PreparationAdvisor().advise(List.of(chosen), profile);
        TasksTrackerRouteExporter.ExportResult result = exporter.export(fixture(), mappings(), advice);
        assertTrue(result.success()); assertEquals(2, result.preparationCount());
        CompatibleRoute imported = gson.fromJson(result.json(), CompatibleRoute.class);
        assertEquals("Preparation", imported.sections.get(0).name); assertEquals(3, imported.sections.size());
        assertTrue(imported.sections.get(0).items.get(0).customItem.description.contains("Magic"));
        TaskAssessment outside = new TaskAssessment(CombatAchievement.builder(99, "Outside", "Other", AchievementTier.EASY)
            .requiredSkills(Map.of("Magic", 99)).requirementsKnown(true).build(), chosen.eligibility(), 0, 0, Map.of(), List.of(), Confidence.HIGH);
        List<PreparationStep> broad = new PreparationAdvisor().advise(List.of(chosen, outside), profile);
        assertFalse(exporter.export(fixture(), mappings(), broad).success());
        assertTrue(exporter.export(fixture().withPreparationSteps(broad), mappings()).success());
    }

    private static final class CompatibleRoute
    {
        String id, name, taskType, author, description; List<CompatibleSection> sections;
        List<Integer> flattenedTasks()
        { List<Integer> values = new ArrayList<>(); for (CompatibleSection section : sections) for (CompatibleItem item : section.items) if (item.taskId != null) values.add(item.taskId); return values; }
    }
    private static final class CompatibleSection { String id, name, description; List<CompatibleItem> items; }
    private static final class CompatibleItem { Integer taskId; CompatibleCustom customItem; String note; }
    private static final class CompatibleCustom { String id, label, description; }
}

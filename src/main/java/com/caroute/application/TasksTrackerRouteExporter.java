package com.caroute.application;

import com.caroute.domain.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local COMBAT JSON generation against the pinned Tasks Tracker schema; no file, UI or network effects. */
public final class TasksTrackerRouteExporter
{
    public static final String TRACKER_REVISION = "dbdfdaddbdc09592e54f90056a5397df63f4e88a";
    private final Gson gson;
    public TasksTrackerRouteExporter(Gson gson) { this.gson = Objects.requireNonNull(gson); }

    public static final class ExportResult
    {
        private final boolean success;
        private final String json, error;
        private final int taskCount, preparationCount;
        private ExportResult(boolean success, String json, String error, int taskCount, int preparationCount)
        { this.success = success; this.json = json; this.error = error; this.taskCount = taskCount; this.preparationCount = preparationCount; }
        public boolean success() { return success; }
        public String json() { return json; }
        public String error() { return error; }
        public int taskCount() { return taskCount; }
        public int preparationCount() { return preparationCount; }
    }

    /** Export the route with static per-stop condition reminders, without broad catalogue preparation goals. */
    public ExportResult export(RoutePlan plan, Map<Integer, Integer> verifiedStructIds)
    { return export(plan, verifiedStructIds, Collections.emptyList()); }

    /** Optional advice must be computed from selected route tasks and the same profile, not the whole catalogue. */
    public ExportResult export(RoutePlan plan, Map<Integer, Integer> verifiedStructIds, List<PreparationStep> routeScopedPreparation)
    {
        Objects.requireNonNull(plan); Objects.requireNonNull(verifiedStructIds); Objects.requireNonNull(routeScopedPreparation);
        if (plan.reachability() == Reachability.UNKNOWN_PROFILE) return refused("Account and task identities must be verified before exporting.");
        Set<Integer> taskIds = new LinkedHashSet<>(), structIds = new HashSet<>();
        List<TaskAssessment> ordered = new ArrayList<>();
        for (BossSession stop : plan.stops()) for (TaskAssessment assessment : stop.tasks())
        {
            int taskId = assessment.task().id(); Integer structId = verifiedStructIds.get(taskId);
            if (structId == null || structId < 0) return refused("No verified Tasks Tracker struct mapping for CA " + taskId + "; synchronize or update the catalogue.");
            if (!taskIds.add(taskId)) return refused("Route contains duplicate CA " + taskId + ".");
            if (!structIds.add(structId)) return refused("Multiple route tasks map to the same Tasks Tracker struct " + structId + ".");
            ordered.add(assessment);
        }
        if (ordered.isEmpty()) return refused("There are no planned tasks to export.");
        for (PreparationStep step : routeScopedPreparation)
            if (!taskIds.containsAll(step.taskIds())) return refused("Preparation advice includes tasks outside the selected route; recompute route-scoped preparation.");

        JsonObject route = new JsonObject();
        String scope = plan.objective() == RouteObjective.BOSS_COMPLETION ? plan.selectedActivity()
            : plan.objective() == RouteObjective.MAXIMUM_POINTS_PER_HOUR ? plan.timeBudgetMinutes() + " minute session"
            : plan.target().displayName() + " - " + plan.objective().displayName();
        route.addProperty("name", "CA Route - " + scope);
        route.addProperty("taskType", "COMBAT"); route.addProperty("author", "CA Route");
        route.addProperty("description", "Generated locally by CA Route. Scope: " + plan.objective().displayName()
            + ". Reachability: " + plan.reachability().name()
            + ". Approximate encounter-route ETA: " + String.format(Locale.ROOT, "%.1f", plan.estimatedMinutes())
            + " minutes. Includes manual active preparation estimates where supplied; other training, calendar waits and resource costs remain unestimated. Check static task conditions before starting. Tracker schema revision: " + TRACKER_REVISION + ".");
        JsonArray sections = new JsonArray(); int preparationCount = 0;
        if (!routeScopedPreparation.isEmpty())
        {
            JsonObject preparation = section("Preparation", "Prerequisites and readiness advice for this route; related rewards are not guaranteed instant unlocks.", "prepare");
            JsonArray items = new JsonArray(); Set<String> customIds = new HashSet<>();
            for (PreparationStep step : routeScopedPreparation)
            {
                String detail = step.description() + " Related tasks: " + String.join(", ", step.taskNames().values()) + "."
                    + (step.notes().isEmpty() ? "" : " " + String.join(" ", step.notes()));
                JsonObject item = custom(step.purpose().name().replace('_', ' '), detail,
                    "prepare:" + step.kind() + ":" + step.purpose() + ":" + step.key() + ":" + step.targetLevel() + ":" + step.targetQuestStatus());
                String customId = item.getAsJsonObject("customItem").get("id").getAsString();
                if (!customIds.add(customId)) return refused("Preparation contains duplicate steps.");
                items.add(item); preparationCount++;
            }
            preparation.add("items", items); sections.add(preparation);
        }
        int stopIndex = 0;
        for (BossSession stop : plan.stops())
        {
            JsonObject section = section(stop.activity(), stop.points() + " planned points; physical ETA "
                + String.format(Locale.ROOT, "%.1f", stop.estimatedMinutes()) + " minutes (approximate).", "stop:" + stopIndex + ":" + stop.activity());
            JsonArray items = new JsonArray();
            if (stop.conditional())
            {
                items.add(custom("Check access and task conditions", "Verify prerequisites, gear and the exact task conditions for "
                    + stop.activity() + " before starting. This is a static progression reminder.", "check:" + stopIndex + ":" + stop.activity()));
                preparationCount++;
            }
            for (TaskAssessment assessment : stop.tasks())
            {
                CombatAchievement task = assessment.task(); JsonObject item = new JsonObject();
                item.addProperty("taskId", verifiedStructIds.get(task.id()));
                item.addProperty("note", task.name() + " - " + task.points() + " points. " + task.description()
                    + (task.wikiUrl().isEmpty() ? "" : " Strategy: " + task.wikiUrl()));
                items.add(item);
            }
            section.add("items", items); sections.add(section); stopIndex++;
        }
        route.add("sections", sections);
        route.addProperty("id", stableId("route:" + gson.toJson(route)));
        return new ExportResult(true, gson.toJson(route), "", ordered.size(), preparationCount);
    }
    private static ExportResult refused(String reason) { return new ExportResult(false, "", reason, 0, 0); }
    private static JsonObject section(String name, String description, String identity)
    {
        JsonObject section = new JsonObject(); section.addProperty("id", stableId(identity));
        section.addProperty("name", name); section.addProperty("description", description); return section;
    }
    private static JsonObject custom(String label, String description, String identity)
    {
        JsonObject custom = new JsonObject(); custom.addProperty("id", stableId(identity));
        custom.addProperty("label", label); custom.addProperty("description", description);
        JsonObject item = new JsonObject(); item.add("customItem", custom); return item;
    }
    private static String stableId(String identity)
    { return UUID.nameUUIDFromBytes(("CA Route:" + identity).getBytes(StandardCharsets.UTF_8)).toString(); }
}

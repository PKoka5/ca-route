package com.caroute.verification;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.AchievementTier;
import com.caroute.domain.CombatAchievement;
import com.caroute.domain.EligibilityState;
import com.caroute.domain.GearProfile;
import com.caroute.domain.PlayerProfile;
import com.caroute.domain.QuestStatus;
import com.caroute.domain.RoutePlan;
import com.caroute.domain.RouteObjective;
import com.caroute.domain.RoutingPreferences;
import com.caroute.engine.RouteEngine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.*;

/** Full bundled-catalogue checks, separate from the synthetic arithmetic fixtures. */
public class CatalogueScenariosTest
{
    private static final String[] SKILLS = {
        "Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Hitpoints",
        "Runecraft", "Crafting", "Mining", "Smithing", "Fishing", "Cooking", "Firemaking",
        "Woodcutting", "Agility", "Herblore", "Thieving", "Fletching", "Slayer", "Farming",
        "Construction", "Hunter", "Sailing"
    };

    @Test
    public void bundledCatalogueIsStableUnderInputPermutationAndDoesNotAwardDuplicatePoints() throws Exception
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        List<CombatAchievement> tasks = repository.tasks();
        PlayerProfile profile = profile(tasks, 99, true, Collections.emptySet());
        RoutingPreferences preferences = RoutingPreferences.builder().target(AchievementTier.EASY).build();
        RouteEngine engine = new RouteEngine();
        long start = System.nanoTime();
        RoutePlan first = engine.plan(tasks, profile, preferences);
        double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;
        List<CombatAchievement> reversed = new ArrayList<>(tasks);
        Collections.reverse(reversed);
        RoutePlan second = engine.plan(reversed, profile, preferences);

        assertFalse("A prepared account should receive at least one stop", first.stops().isEmpty());
        assertEquals(render(first), render(second));
        Set<Integer> selectedIds = new HashSet<>();
        int uniquePoints = 0;
        double minutes = 0;
        for (var stop : first.stops())
        {
            assertTrue(Double.isFinite(stop.estimatedMinutes()));
            assertTrue(stop.estimatedMinutes() >= 0);
            minutes += stop.estimatedMinutes();
            for (var assessment : stop.tasks())
            {
                assertTrue("A reward ID must appear exactly once in a plan", selectedIds.add(assessment.task().id()));
                uniquePoints += assessment.task().points();
                assertNotEquals(EligibilityState.COMPLETED, assessment.eligibility().state());
                assertTrue("A stop cannot contain verified inaccessible tasks", assessment.eligibility().recommendedNow());
            }
        }
        assertEquals(uniquePoints, first.projectedPoints());
        assertEquals(minutes, first.estimatedMinutes(), 1e-6);
        assertTrue("Representative reroute should remain interactive", elapsedMillis < 5000);
        writeScenario("prepared-zero-progress", first, elapsedMillis);
        assertGolden("prepared-zero-progress", first);
    }

    @Test
    public void freshAccountDoesNotReceiveSeverelyUnderlevelledBossTasks() throws Exception
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        PlayerProfile profile = profile(tasks, 1, false, Collections.emptySet());
        RoutePlan route = new RouteEngine().plan(tasks, profile,
            RoutingPreferences.builder().target(AchievementTier.EASY).build());
        assertFalse("Missing recommended stats must not admit a fresh account to endgame raids",
            route.stops().stream().anyMatch(s -> s.activity().contains("Xeric") || s.activity().contains("Theatre") || s.activity().contains("Tombs")));
        for (var stop : route.stops())
        {
            for (var assessment : stop.tasks())
            {
                assertTrue(assessment.eligibility().recommendedNow());
                for (var requirement : assessment.task().recommendedSkills().entrySet())
                {
                    int actual = profile.skills().getOrDefault(requirement.getKey(), 1);
                    assertTrue("Fresh profile unexpectedly sent to " + assessment.task().name(),
                        requirement.getValue() - actual <= 15);
                }
            }
        }
        writeScenario("fresh-account", route, 0);
        assertGolden("fresh-account", route);
    }

    @Test
    public void authoritativeCompletionsDisappearFromFullCatalogueRoutes()
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        RouteEngine engine = new RouteEngine();
        RoutingPreferences preferences = RoutingPreferences.builder().target(AchievementTier.EASY).build();
        RoutePlan initial = engine.plan(tasks, profile(tasks, 99, true, Collections.emptySet()), preferences);
        Set<Integer> completed = new HashSet<>();
        for (var assessment : initial.stops().get(0).tasks()) completed.add(assessment.task().id());
        RoutePlan updated = engine.plan(tasks, profile(tasks, 99, true, completed), preferences);
        for (var stop : updated.stops())
            for (var assessment : stop.tasks())
                assertFalse("Completed task reappeared", completed.contains(assessment.task().id()));
        assertTrue(updated.currentPoints() > initial.currentPoints());
        assertTrue(updated.pointsRemaining() < initial.pointsRemaining());
    }

    @Test public void everyObjectiveProducesAConsistentRouteAcrossTheWholeCatalogue()
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        PlayerProfile account = profile(tasks, 99, true, Collections.emptySet());
        for (RouteObjective objective : RouteObjective.values())
        {
            RoutingPreferences prefs = RoutingPreferences.builder().target(AchievementTier.EASY).objective(objective)
                .selectedActivity("Scurrius").timeBudgetMinutes(60).build();
            RoutePlan plan = new RouteEngine().plan(tasks, account, prefs);
            assertEquals(objective, plan.objective());
            Set<Integer> selected = new HashSet<>(); int points = 0; double active = 0;
            for (var stop : plan.stops())
            {
                active += stop.estimatedMinutes();
                if (objective == RouteObjective.BOSS_COMPLETION) assertEquals("Scurrius", stop.activity());
                for (var assessment : stop.tasks())
                {
                    assertTrue("Duplicate reward in " + objective, selected.add(assessment.task().id()));
                    assertTrue(assessment.eligibility().recommendedNow()); points += assessment.task().points();
                }
            }
            assertEquals(points, plan.projectedPoints()); assertEquals(active, plan.estimatedMinutes(), 1e-6);
            assertFalse("Prepared catalogue profile should have candidates in " + objective, selected.isEmpty());
            if (objective == RouteObjective.MAXIMUM_POINTS_PER_HOUR) assertTrue(plan.estimatedMinutes() <= 60 + 1e-6);
            if (objective == RouteObjective.BOSS_COMPLETION)
            {
                Set<Integer> expected = new HashSet<>(); tasks.stream().filter(t -> t.activity().equals("Scurrius")).forEach(t -> expected.add(t.id()));
                assertEquals(expected, selected); assertTrue(plan.scopeComplete());
            }
            else if (objective != RouteObjective.MAXIMUM_POINTS_PER_HOUR) assertTrue(plan.projectedPoints() >= plan.pointsRemaining());
        }
    }

    private static PlayerProfile profile(List<CombatAchievement> tasks, int level, boolean allQuests, Set<Integer> completed)
    {
        Map<String, Integer> skills = new HashMap<>();
        for (String skill : SKILLS) skills.put(skill, level);
        if (level == 1) skills.put("Hitpoints", 10);
        Map<String, QuestStatus> quests = new HashMap<>();
        Map<String, Integer> kc = new HashMap<>();
        Map<AchievementTier, Integer> thresholds = new EnumMap<>(AchievementTier.class);
        int earned = 0;
        for (CombatAchievement task : tasks)
        {
            for (String quest : task.quests().keySet())
                quests.put(quest, allQuests ? QuestStatus.COMPLETED : QuestStatus.NOT_STARTED);
            kc.put(task.activity(), 0);
            if (completed.contains(task.id())) earned += task.points();
        }
        int running = 0;
        for (AchievementTier tier : AchievementTier.values())
        {
            for (CombatAchievement task : tasks) if (task.tier() == tier) running += task.points();
            thresholds.put(tier, running);
        }
        return PlayerProfile.builder().accountKey("synthetic-catalogue-scenario").version(completed.size())
            .loaded(true).completedTaskIds(completed).currentPoints(earned).thresholds(thresholds)
            .skills(skills).quests(quests).killCounts(kc).gear(GearProfile.unknown())
            .catalogueComplete(true).build();
    }

    private static String render(RoutePlan plan)
    {
        StringBuilder result = new StringBuilder();
        result.append("Goal: ").append(plan.target()).append('\n')
            .append("Current points: ").append(plan.currentPoints()).append('\n')
            .append("Gap: ").append(plan.pointsRemaining()).append('\n')
            .append("Projected points: ").append(plan.projectedPoints()).append('\n')
            .append("ETA minutes: ").append(String.format(Locale.ROOT, "%.3f", plan.estimatedMinutes())).append('\n')
            .append("Reachability: ").append(plan.reachability()).append('\n')
            .append("Confidence: ").append(plan.confidence()).append('\n');
        for (var stop : plan.stops())
        {
            result.append(stop.activity()).append(" | ").append(stop.points()).append(" pts | ")
                .append(String.format(Locale.ROOT, "%.3f", stop.estimatedMinutes())).append(" min\n");
            for (var assessment : stop.tasks())
                result.append("  ").append(assessment.task().id()).append(": ")
                    .append(assessment.task().name()).append(" | ")
                    .append(assessment.eligibility().state()).append('\n');
            for (String reason : stop.reasons()) result.append("  Why: ").append(reason).append('\n');
        }
        for (String blocker : plan.blockers().subList(0, Math.min(6,plan.blockers().size()))) result.append("Blocker: ").append(blocker).append('\n');
        return result.toString();
    }

    private static void assertGolden(String name, RoutePlan plan) throws Exception
    {
        try (InputStream in = CatalogueScenariosTest.class.getResourceAsStream("/scenarios/" + name + ".txt"))
        {
            assertNotNull("Reviewed route fixture missing", in);
            assertEquals("Intentional model/data changes require review of the golden route", new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n"), render(plan));
        }
    }

    private static void writeScenario(String name, RoutePlan plan, double elapsedMillis) throws Exception
    {
        Path directory = Paths.get("build", "scenarios");
        Files.createDirectories(directory);
        Files.write(directory.resolve(name + ".txt"), render(plan).getBytes(StandardCharsets.UTF_8));
        System.out.printf(Locale.ROOT, "%s: %d stops, %d pts, %.1f min, computed in %.1f ms%n",
            name, plan.stops().size(), plan.projectedPoints(), plan.estimatedMinutes(), elapsedMillis);
    }
}

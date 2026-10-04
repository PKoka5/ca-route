package com.caroute.navigation;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.AchievementTier;
import com.caroute.domain.CombatAchievement;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityDestinationsTest
{
    @Test public void requestedBossesPointToPublicApproachesInsteadOfTheirInstancedRooms()
    {
        assertPoint("Amoxliatl", 1693, 3231);
        assertPoint("Scurrius", 3236, 3458);
        assertPoint("Bryophyta", 3236, 3458);
        assertPoint("Obor", 3096, 3469);
        assertPoint("Kraken", 2277, 3611);
        assertPoint("Hespori", 1231, 3729);
        assertPoint("Tempoross", 3145, 2843);
        assertPoint("Scorpia", 3231, 3936);
        assertPoint("Barrows", 3565, 3315);
        assertPoint("Vorkath", 2638, 3698);
        for (String activity : List.of("K'ril Tsutsaroth", "General Graardor", "Commander Zilyana", "Kree'arra", "Nex"))
            assertPoint(activity, 2917, 3747);
        assertFalse(ActivityDestinations.forActivity("Scorpia").get().instructions().isEmpty());
        assertTrue("Wilderness staging must disclose PvP risk", ActivityDestinations.forActivity("Scorpia").get().instructions().contains("PvP"));
        assertTrue("A surface staging point must explain the final manual approach", ActivityDestinations.forActivity("Vorkath").get().instructions().contains("Board manually"));
    }

    @Test public void everyCanonicalRaidModeSharesItsPublicEntrance()
    {
        assertAliases(1232, 3573, "Chambers of Xeric", "Chambers of Xeric: Challenge Mode");
        assertAliases(3676, 3219, "Theatre of Blood", "Theatre of Blood: Entry Mode", "Theatre of Blood: Hard Mode");
        assertAliases(3357, 2711, "Tombs of Amascut", "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Expert Mode");
        assertAliases(3227, 6116, "Crystalline Hunllef", "Corrupted Hunllef");
        assertAliases(2520, 3740, "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
    }

    @Test public void registryOnlyContainsRealCatalogueActivitiesWithBoundedAttributedPoints()
    {
        Set<String> canonical = AchievementRepository.loadBundled().tasks().stream().map(task -> task.activity()).collect(Collectors.toSet());
        assertTrue("Aliases must exactly match the shipped CA catalogue", canonical.containsAll(ActivityDestinations.entries().keySet()));
        assertFalse(ActivityDestinations.entries().isEmpty());
        ActivityDestinations.entries().forEach((activity, destination) -> {
            WorldPoint point = destination.point();
            assertTrue(activity, point.getX() > 0 && point.getX() < 16384 && point.getY() > 0 && point.getY() < 16384);
            assertTrue(activity, point.getPlane() >= 0 && point.getPlane() <= 3);
            assertFalse(activity, destination.label().trim().isEmpty());
            assertFalse(activity, destination.instructions().trim().isEmpty());
            URI source = URI.create(destination.sourceUrl());
            assertEquals("https", source.getScheme()); assertEquals("github.com", source.getHost());
            assertTrue(activity, source.getPath().startsWith("/runelite/runelite/blob/runelite-parent-1.13.1/")
                || source.getPath().startsWith("/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/"));
            assertTrue("A destination records its specific source location", source.getFragment().matches("L[0-9]+"));
        });
        assertThrows(UnsupportedOperationException.class, () -> ActivityDestinations.entries().clear());
    }

    @Test public void unknownActivitiesAndTaskTitlesNeverReceiveGuessedFallbacks()
    {
        for (String unknown : new String[] {null, "", "Unknown boss", "kraken", "Kril", "Amoxliatl Champion", "Giants", "Other: Sit Back and Relax"})
            assertFalse(String.valueOf(unknown), ActivityDestinations.forActivity(unknown).isPresent());
    }

    @Test public void denOfGiantsTaskReceivesItsSpecificVerifiedEntrance()
    {
        CombatAchievement task = AchievementRepository.loadBundled().tasks().stream()
            .filter(candidate -> candidate.id() == 398).findFirst().orElseThrow();
        assertEquals("Into the Den of Giants", task.name());
        ActivityDestinations.Destination destination = ActivityDestinations.forTask(task).orElseThrow();
        assertEquals(new WorldPoint(1419, 3588, 0), destination.point());
        assertEquals("Giants' Den surface entrance", destination.label());
        assertTrue(destination.instructions().contains("Shayzien"));
        assertTrue(destination.instructions().contains("Hill Giant, Moss Giant and Fire Giant"));
        assertTrue(destination.sourceUrl().endsWith("/DungeonLocation.java#L102"));
        assertFalse("The broad activity has no location-specific fallback", ActivityDestinations.forActivity("Giants").isPresent());
    }

    @Test public void taskSpecificDestinationRequiresTheWholeCanonicalIdentity()
    {
        assertFalse(ActivityDestinations.forTask(null).isPresent());
        assertFalse(ActivityDestinations.forTask(CombatAchievement.builder(399, "Into the Den of Giants", "Giants", AchievementTier.EASY).build()).isPresent());
        assertFalse(ActivityDestinations.forTask(CombatAchievement.builder(398, "Different giants task", "Giants", AchievementTier.EASY).build()).isPresent());
        assertFalse(ActivityDestinations.forTask(CombatAchievement.builder(398, "Into the Den of Giants", "Unknown activity", AchievementTier.EASY).build()).isPresent());
        assertFalse(ActivityDestinations.forTask(CombatAchievement.builder(400, "Sit Back and Relax", "Other: Sit Back and Relax", AchievementTier.MEDIUM).build()).isPresent());
        assertFalse(ActivityDestinations.forTask(CombatAchievement.builder(399, "Another thrall task", "Other: Sit Back and Relax", AchievementTier.MEDIUM).build()).isPresent());
        CombatAchievement bossTask = CombatAchievement.builder(1, "Kraken task", "Kraken", AchievementTier.EASY).build();
        assertSame("Existing activity destinations are preserved", ActivityDestinations.forActivity("Kraken").orElseThrow(),
            ActivityDestinations.forTask(bossTask).orElseThrow());
    }

    @Test public void everyBundledTaskHasAnAttributedDestinationAndExportsItsCoverage() throws Exception
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        List<String> report = new ArrayList<>();
        report.add("task_id,task,activity,x,y,plane,destination,source,instructions");
        for (CombatAchievement task : tasks)
        {
            String identity = task.id() + " " + task.name() + " (" + task.activity() + ")";
            assertTrue("Missing travel destination: " + identity, ActivityDestinations.forTask(task).isPresent());
            ActivityDestinations.Destination destination = ActivityDestinations.forTask(task).orElseThrow();
            WorldPoint point = destination.point();
            assertTrue(identity, point.getX() > 0 && point.getX() < 16384 && point.getY() > 0 && point.getY() < 16384
                && point.getPlane() >= 0 && point.getPlane() <= 3);
            assertFalse(identity, destination.instructions().isBlank());
            assertFalse(identity, destination.sourceUrl().isBlank());
            report.add(task.id() + "," + csv(task.name()) + "," + csv(task.activity()) + ","
                + point.getX() + "," + point.getY() + "," + point.getPlane() + ","
                + csv(destination.label()) + "," + csv(destination.sourceUrl()) + "," + csv(destination.instructions()));
        }
        Path folder = Path.of("build", "navigation"); Files.createDirectories(folder);
        Files.write(folder.resolve("task-destination-coverage.csv"), report, StandardCharsets.UTF_8);
    }

    @Test public void ordinaryMonstersAndLocationSpecificTasksGetApplicablePublicApproaches()
    {
        assertPoint("Wyrm", 1308, 3807);
        assertTrue(ActivityDestinations.forActivity("Wyrm").orElseThrow().instructions().contains("Wyrm area"));
        assertPoint("Lizardman Shaman", 1311, 3686);
        assertTrue(ActivityDestinations.forActivity("Lizardman Shaman").orElseThrow().instructions().contains("Molch"));
        assertPoint("Basilisk Knight", 2464, 4012);
        assertPoint("Skeletal Wyvern", 3007, 3150);
        assertPoint("Deranged Archaeologist", 3676, 3755);
        assertFalse(ActivityDestinations.forActivity("Deranged Archaeologist").orElseThrow().instructions().contains("PvP risk"));
        for (String activity : List.of("Chaos Elemental", "Chaos Fanatic", "Crazy Archaeologist"))
            assertTrue(activity, ActivityDestinations.forActivity(activity).orElseThrow().instructions().contains("PvP risk"));
        CombatAchievement thralls = AchievementRepository.loadBundled().tasks().stream().filter(task -> task.id() == 399).findFirst().orElseThrow();
        assertEquals("Sit Back and Relax", thralls.name());
        ActivityDestinations.Destination suggested = ActivityDestinations.forTask(thralls).orElseThrow();
        assertEquals(new WorldPoint(3236, 3458, 0), suggested.point());
        assertTrue(suggested.instructions().contains("no required location"));
        assertTrue(suggested.label().contains("suggested"));
    }

    @Test public void questBossTasksLeadToPublicReplayApproachesRatherThanOriginalFightInstances()
    {
        assertPoint("Galvek", 2458, 2851);
        assertTrue(ActivityDestinations.forActivity("Galvek").orElseThrow().instructions().contains("Pool of Dreams"));
        assertPoint("Glough", 2465, 3495);
        assertTrue(ActivityDestinations.forActivity("Glough").orElseThrow().instructions().contains("Burning Brazier"));
        assertPoint("Fragment of Seren", 3264, 6065);
        assertTrue(ActivityDestinations.forActivity("Fragment of Seren").orElseThrow().instructions().contains("Memoriam Device"));
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private static void assertPoint(String activity, int x, int y)
    {
        assertTrue("Missing destination for " + activity, ActivityDestinations.forActivity(activity).isPresent());
        assertEquals(new WorldPoint(x, y, 0), ActivityDestinations.forActivity(activity).get().point());
    }
    private static void assertAliases(int x, int y, String... activities)
    {
        ActivityDestinations.Destination shared = ActivityDestinations.forActivity(activities[0]).orElseThrow();
        for (String activity : activities) { assertPoint(activity, x, y); assertSame(shared, ActivityDestinations.forActivity(activity).get()); }
    }
}

package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import com.caroute.ui.GuideView;
import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.NPC;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises real map points and the public path messages with authoritative account facts. */
public class GuideControllerTest
{
    private static CombatAchievement task(int id, String activity)
    { return CombatAchievement.builder(id, "Selected challenge", activity, AchievementTier.EASY)
        .description("Complete the exact selected condition.").requirementsKnown(true).kcThreshold(25).build(); }
    private static PlayerProfile account(String key, Set<Integer> complete, Integer kc)
    {
        PlayerProfile.Builder account = PlayerProfile.builder().accountKey(key).loaded(true).catalogueComplete(true)
            .completedTaskIds(complete);
        if (kc != null) account.killCounts(Map.of("K'ril Tsutsaroth", kc));
        return account.build();
    }

    @Test public void taskSelectionPublishesTheExactConditionAndVerifiedEntranceWithoutChangingCompletion()
    {
        Fixture f = new Fixture(); PlayerProfile account = account("111", Set.of(), null);
        assertTrue(f.guide.startTask(task(1, "K'ril Tsutsaroth"), account));
        assertEquals(1, f.messages.size());
        WorldPoint entrance = ActivityDestinations.forActivity("K'ril Tsutsaroth").orElseThrow().point();
        assertEquals(entrance, f.messages.get(0).getData().get("target"));
        assertEquals(1, f.points.values.size()); assertEquals(entrance, f.points.values.get(0).getWorldPoint());
        assertTrue(f.points.values.get(0).isSnapToEdge()); assertTrue(f.points.values.get(0).isJumpOnClick());
        assertEquals("Complete the exact selected condition.", f.guide.view().description());
        assertEquals(Integer.valueOf(1), f.guide.view().taskId()); assertTrue(f.guide.view().canFocus());
        assertTrue(f.guide.view().status().contains("sent")); assertNull(f.guide.current().killCount());
        assertTrue(account.completedTaskIds().isEmpty());
        assertTrue(f.guide.current().matchesNpc("K’ril Tsutsaroth"));
        assertFalse(f.guide.current().matchesNpc("Tstanon Karlak"));
        f.guide.focusMap(); assertEquals(entrance, f.focused);
    }

    @Test public void unsupportedActivitiesKeepATaskReminderButNeverInventATarget()
    {
        Fixture f = new Fixture();
        assertTrue(f.guide.startTask(task(1, "Uncurated activity"), account("111", Set.of(), null)));
        assertTrue(f.messages.isEmpty()); assertTrue(f.points.values.isEmpty());
        assertTrue(f.guide.view().active()); assertFalse(f.guide.view().canFocus());
        assertTrue(f.guide.view().status().contains("no verified"));
        f.guide.focusMap(); assertNull(f.focused);
    }

    @Test public void switchingFromTheCanonicalGiantsTaskToWyrmReplacesThePathAndOnlyOurMarker()
    {
        Fixture f = new Fixture(); PlayerProfile account = account("111", Set.of(), null);
        CombatAchievement giants = canonicalTask(398), wyrm = canonicalTask(287);
        assertEquals("Into the Den of Giants", giants.name()); assertEquals("Wyrm", wyrm.activity());
        WorldPoint giantsEntrance = ActivityDestinations.forTask(giants).orElseThrow().point();
        WorldPoint wyrmEntrance = ActivityDestinations.forTask(wyrm).orElseThrow().point();
        assertNotEquals("Different encounters need their own travel destination", giantsEntrance, wyrmEntrance);
        WorldMapPoint other = new WorldMapPoint(new WorldPoint(3200, 3200, 0), null);
        other.setName("Another plugin"); f.points.add(other);

        assertTrue(f.guide.startTask(giants, account));
        assertTrue(f.guide.startTask(wyrm, account));
        assertEquals(List.of("path", "path"), names(f));
        assertEquals(giantsEntrance, f.messages.get(0).getData().get("target"));
        assertEquals(wyrmEntrance, f.messages.get(1).getData().get("target"));
        assertEquals(Integer.valueOf(wyrm.id()), f.guide.view().taskId());
        assertEquals(wyrm.description(), f.guide.view().description());
        assertEquals(2, f.points.values.size()); assertSame(other, f.points.values.get(0));
        assertEquals(wyrmEntrance, f.points.values.get(1).getWorldPoint());
        assertTrue(f.bridge.ownsTarget()); assertTrue(f.guide.view().status().contains("sent"));
        f.guide.focusMap(); assertEquals(wyrmEntrance, f.focused);

        f.position = giantsEntrance; f.guide.tick();
        assertTrue("Arriving at the previous task must not stop the new path", f.bridge.ownsTarget());
        assertEquals(List.of("path", "path"), names(f));
        f.position = wyrmEntrance; f.guide.tick();
        assertEquals(List.of("path", "path", "clear"), names(f));
        assertTrue(f.guide.view().active()); assertEquals(Integer.valueOf(wyrm.id()), f.guide.view().taskId());
        assertTrue("Travel arrival must not manufacture CA completion", account.completedTaskIds().isEmpty());
    }

    @Test public void aTaskAfterGiantsArrivalStartsFreshAndNeverClearsAReplacedExternalPath()
    {
        Fixture f = new Fixture(); CombatAchievement giants = canonicalTask(398), wyrm = canonicalTask(287);
        PlayerProfile account = account("111", Set.of(), null);
        assertTrue(f.guide.startTask(giants, account));
        f.position = ActivityDestinations.forTask(giants).orElseThrow().point(); f.guide.tick();
        assertEquals(List.of("path", "clear"), names(f));
        f.guide.refresh(account("111", Set.of(giants.id()), null));
        assertFalse(f.guide.view().active()); assertTrue(f.points.values.isEmpty());

        assertTrue(f.guide.startTask(wyrm, account("111", Set.of(giants.id()), null)));
        WorldPoint wyrmEntrance = ActivityDestinations.forTask(wyrm).orElseThrow().point();
        assertEquals(List.of("path", "clear", "path"), names(f));
        assertEquals(wyrmEntrance, f.messages.get(2).getData().get("target"));
        assertTrue("Previous arrival must not suppress the next task's path", f.bridge.ownsTarget());
        assertEquals(1, f.points.values.size()); assertEquals(wyrmEntrance, f.points.values.get(0).getWorldPoint());

        f.bridge.observe(new PluginMessage("shortestpath", "path", Map.of("target", new WorldPoint(3200, 3200, 0))));
        f.guide.navigationChanged(); assertFalse(f.bridge.ownsTarget());
        f.position = wyrmEntrance; f.guide.tick();
        assertEquals("Arrival cannot clear a route another plugin has replaced", List.of("path", "clear", "path"), names(f));
        assertTrue(f.guide.view().active()); assertTrue(f.guide.view().status().contains("reached"));
        f.guide.stop(); assertEquals(List.of("path", "clear", "path"), names(f));
        assertTrue(f.points.values.isEmpty());
    }

    private static CombatAchievement canonicalTask(int id)
    { return AchievementRepository.loadBundled().tasks().stream().filter(task -> task.id() == id).findFirst().orElseThrow(); }

    @Test public void everyBundledTaskStartsItsOwnMarkerAndShortestPathRequest()
    {
        Fixture f = new Fixture(); PlayerProfile account = account("111", Set.of(), null);
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        for (CombatAchievement selected : tasks)
        {
            String identity = selected.id() + " " + selected.name();
            assertTrue(identity, f.guide.startTask(selected, account));
            WorldPoint destination = ActivityDestinations.forTask(selected).orElseThrow().point();
            assertEquals(identity, "path", f.messages.get(f.messages.size() - 1).getName());
            assertEquals(identity, destination, f.messages.get(f.messages.size() - 1).getData().get("target"));
            assertEquals(identity, Integer.valueOf(selected.id()), f.guide.view().taskId());
            assertEquals(identity, selected.description(), f.guide.view().description());
            assertEquals(identity, 1, f.points.values.size());
            assertEquals(identity, destination, f.points.values.get(0).getWorldPoint());
            assertTrue(identity, f.bridge.ownsTarget());
        }
        assertEquals(tasks.size(), f.messages.size());
        assertTrue(account.completedTaskIds().isEmpty());
        f.guide.stop(); assertEquals(tasks.size() + 1, f.messages.size());
        assertEquals("clear", f.messages.get(tasks.size()).getName());
    }

    @Test public void taskCompletionAndAccountChangesRemoveOnlyOurOwnMarkerAndPath()
    {
        for (boolean completed : new boolean[]{false, true})
        {
            Fixture f = new Fixture(); WorldMapPoint other = new WorldMapPoint(new WorldPoint(3200, 3200, 0), null);
            other.setName("Another plugin"); f.points.add(other);
            f.guide.startTask(task(1, "K'ril Tsutsaroth"), account("111", Set.of(), 14));
            assertEquals(Integer.valueOf(14), f.guide.current().killCount());
            f.guide.refresh(account(completed ? "111" : "222", completed ? Set.of(1) : Set.of(), 15));
            assertNull(f.guide.current()); assertFalse(f.guide.view().active());
            assertEquals(List.of(other), f.points.values);
            assertEquals(List.of("path", "clear"), names(f));
            assertFalse(f.changes.get(f.changes.size() - 1).active());
            f.guide.stop(); assertEquals(2, f.messages.size());
        }
    }

    @Test public void externalRoutesAreNeitherReassertedNorClearedByRefreshOrStop()
    {
        Fixture f = new Fixture(); PlayerProfile account = account("111", Set.of(), 14);
        f.guide.startTask(task(1, "K'ril Tsutsaroth"), account);
        f.bridge.observe(new PluginMessage("shortestpath", "path", Map.of("target", new WorldPoint(3200, 3200, 0))));
        f.guide.navigationChanged(); f.guide.refresh(account);
        assertTrue(f.guide.view().status().contains("select again")); assertEquals(1, f.messages.size());
        f.guide.stop(); assertEquals(1, f.messages.size()); assertTrue(f.points.values.isEmpty());
    }

    @Test public void absentShortestPathAndExplicitConfigurationChangesKeepMapAndReminderIndependent()
    {
        Fixture f = new Fixture(); f.available = false;
        f.guide.startTask(task(1, "K'ril Tsutsaroth"), account("111", Set.of(), null));
        assertTrue(f.messages.isEmpty()); assertEquals(1, f.points.values.size()); assertTrue(f.guide.view().active());
        f.available = true; f.config.path = false; f.config.map = false; f.guide.configure();
        assertTrue(f.messages.isEmpty()); assertTrue(f.points.values.isEmpty());
        f.config.path = true; f.config.map = true; f.guide.configure();
        assertEquals(List.of("path"), names(f)); assertEquals(1, f.points.values.size());
        f.config.path = false; f.guide.configure();
        assertEquals(List.of("path", "clear"), names(f)); assertTrue(f.guide.view().active());
    }

    @Test public void arrivingEndsTravelWhileKeepingTheSelectedTaskAndRecordedKc()
    {
        Fixture f = new Fixture(); f.guide.startTask(task(1, "K'ril Tsutsaroth"), account("111", Set.of(), 20));
        f.position = ActivityDestinations.forActivity("K'ril Tsutsaroth").orElseThrow().point();
        f.guide.tick();
        assertEquals(List.of("path", "clear"), names(f)); assertTrue(f.guide.view().active());
        assertTrue(f.guide.view().status().contains("reached")); assertEquals(Integer.valueOf(20), f.guide.current().killCount());
        f.guide.tick(); f.guide.refresh(account("111", Set.of(), 21));
        assertEquals(2, f.messages.size()); assertEquals(Integer.valueOf(21), f.guide.current().killCount());
        Fixture alreadyThere = new Fixture(); alreadyThere.position = f.position;
        alreadyThere.guide.startTask(task(1, "K'ril Tsutsaroth"), account("111", Set.of(), null));
        assertTrue(alreadyThere.messages.isEmpty());
    }

    @Test public void completedOrUnverifiedAccountsCannotStartGuides()
    {
        Fixture f = new Fixture(); CombatAchievement task = task(1, "K'ril Tsutsaroth");
        assertFalse(f.guide.startTask(task, account("111", Set.of(1), null)));
        assertFalse(f.guide.startTask(task, PlayerProfile.builder().accountKey("111").catalogueComplete(true).loaded(false).build()));
        assertFalse(f.guide.startActivity("K'ril Tsutsaroth", PlayerProfile.builder().build()));
        assertTrue(f.messages.isEmpty()); assertTrue(f.points.values.isEmpty()); assertNull(f.guide.current());
    }
    @Test public void safeKnownTasksRemainGuidableWhenTheFullLiveCatalogueIsIncomplete()
    {
        Fixture f = new Fixture();
        PlayerProfile partial = PlayerProfile.builder().accountKey("111").loaded(true).catalogueComplete(false).build();
        assertTrue(f.guide.startTask(task(1, "K'ril Tsutsaroth"), partial));
        assertTrue(f.guide.view().active()); assertEquals(Integer.valueOf(1), f.guide.view().taskId());
        assertEquals(List.of("path"), names(f));
        f.guide.refresh(partial);
        assertTrue("An incomplete full catalogue must not stop a safe selected task", f.guide.view().active());
        assertEquals(List.of("path"), names(f));
        assertTrue(f.guide.startActivity("K'ril Tsutsaroth", partial));
        assertTrue(f.guide.view().active()); assertNull(f.guide.view().taskId());
        f.guide.refresh(PlayerProfile.builder().accountKey("111").loaded(false).catalogueComplete(false).build());
        assertFalse("Unsafe or unsynchronized reader results still stop guidance", f.guide.view().active());
        assertEquals(List.of("path", "path", "clear"), names(f));
    }
    @Test public void aLoadedMatchingBossOnlyEndsTravelWhenNearbyOnTheSamePlane()
    {
        Fixture f = new Fixture(); f.position = new WorldPoint(3000, 3500, 0); f.instanced = true;
        f.guide.startTask(task(1, "K'ril Tsutsaroth"), account("111", Set.of(), null));
        f.npcList.add(npc("K'ril Tsutsaroth", new WorldPoint(3030, 3500, 0))); f.guide.tick();
        assertTrue(f.bridge.ownsTarget()); assertEquals(List.of("path"), names(f));
        f.npcList.clear(); f.npcList.add(npc("K'ril Tsutsaroth", new WorldPoint(3000, 3500, 1))); f.guide.tick();
        assertTrue(f.bridge.ownsTarget()); assertEquals(List.of("path"), names(f));
        f.npcList.clear(); f.npcList.add(npc("K'ril Tsutsaroth", new WorldPoint(3010, 3500, 0))); f.guide.tick();
        assertFalse(f.bridge.ownsTarget()); assertEquals(List.of("path", "clear"), names(f));
        assertTrue(f.guide.view().active()); assertTrue(f.guide.view().status().contains("reached"));
    }
    @Test public void aVerifiedEncounterMemberEndsTravelWithoutCompletingTheTask()
    {
        Fixture f = new Fixture(); f.position = new WorldPoint(3000, 3500, 0); f.instanced = true;
        PlayerProfile account = account("111", Set.of(), null);
        f.guide.startTask(task(1, "Grotesque Guardians"), account);
        f.npcList.add(npc("Gargoyle", new WorldPoint(3001, 3500, 0))); f.guide.tick();
        assertTrue("Ordinary gargoyles must not cancel travel to the rooftop encounter", f.bridge.ownsTarget());
        f.npcList.clear(); f.npcList.add(npc("Dawn", new WorldPoint(3001, 3500, 0))); f.guide.tick();
        assertEquals(List.of("path", "clear"), names(f)); assertFalse(f.bridge.ownsTarget());
        assertTrue(f.guide.view().active()); assertTrue(f.guide.view().status().contains("reached"));
        assertTrue("A visible encounter member does not prove CA completion", account.completedTaskIds().isEmpty());
        assertEquals(Integer.valueOf(1), f.guide.view().taskId());
    }
    private static NPC npc(String name, WorldPoint point)
    { return proxy(NPC.class, (method, args) -> method.equals("getName") ? name : method.equals("getWorldLocation") ? point : null); }
    @Test public void wildernessApproachIsVisibleEvenWhenTheEncounterTaskIsNotWildernessTagged()
    {
        Fixture f = new Fixture(); CombatAchievement task = task(1, "Corporeal Beast");
        assertFalse(task.flags().contains(TaskFlag.WILDERNESS));
        assertTrue(f.guide.startTask(task, account("111", Set.of(), null)));
        assertTrue(f.guide.current().caution().contains("PvP risk"));
        assertTrue(f.guide.view().instructions().contains("Wilderness"));
    }
    private static List<String> names(Fixture f)
    { List<String> names = new ArrayList<>(); f.messages.forEach(message -> names.add(message.getName())); return names; }

    private static final class Fixture
    {
        boolean available = true, instanced; WorldPoint focused, position;
        final List<NPC> npcList = new ArrayList<>();
        final TestConfig config = new TestConfig();
        final RecordingPoints points = new RecordingPoints();
        final List<PluginMessage> messages = new ArrayList<>();
        final List<GuideView> changes = new ArrayList<>();
        final ShortestPathBridge bridge;
        final GuideController guide;
        Fixture()
        {
            ShortestPathBridge[] reference = new ShortestPathBridge[1];
            bridge = new ShortestPathBridge(message -> { messages.add(message); reference[0].observe(message); }, () -> available);
            reference[0] = bridge;
            WorldMap map = proxy(WorldMap.class, (name, args) -> {
                if (name.equals("setWorldMapPositionTarget")) focused = (WorldPoint) args[0]; return null;
            });
            IndexedObjectSet<?> npcs = proxy(IndexedObjectSet.class, (name, args) -> name.equals("iterator") ? npcList.iterator() : null);
            WorldView world = proxy(WorldView.class, (name, args) -> name.equals("npcs") ? npcs : name.equals("isInstance") ? instanced : null);
            Player player = proxy(Player.class, (name, args) -> name.equals("getWorldLocation") ? position : null);
            Client client = proxy(Client.class, (name, args) -> name.equals("getWorldMap") ? map
                : name.equals("getGameState") ? GameState.LOGGED_IN
                : name.equals("getTopLevelWorldView") && position != null ? world
                : name.equals("getLocalPlayer") && position != null ? player : null);
            guide = new GuideController(client, points, bridge, config, changes::add);
        }
    }
    private static final class TestConfig implements CaRouteConfig
    {
        boolean path = true, map = true;
        @Override public boolean useShortestPath() { return path; }
        @Override public boolean guideMapMarker() { return map; }
    }
    private static final class RecordingPoints extends WorldMapPointManager
    {
        final List<WorldMapPoint> values = new ArrayList<>();
        @Override public void add(WorldMapPoint point) { values.add(point); }
        @Override public void remove(WorldMapPoint point) { values.remove(point); }
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String,Object[],Object> source)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object value = source.apply(method.getName(), args);
            if (value != null || !method.getReturnType().isPrimitive() || method.getReturnType() == void.class) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == double.class) return 0d;
            return 0;
        });
    }
}

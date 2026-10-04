package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import com.caroute.domain.*;
import com.caroute.ui.CombatAchievementIcon;
import com.caroute.ui.GuideView;
import java.util.Objects;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/** Client-thread owner of the active guide, its own map marker and optional path lease. */
public final class GuideController
{
    private final Client client;
    private final WorldMapPointManager points;
    private final ShortestPathBridge paths;
    private final CaRouteConfig config;
    private final Consumer<GuideView> changed;
    private volatile ActiveGuide current;
    private WorldMapPoint marker;
    private String account = "";
    private boolean pathEnabled;
    private boolean travelComplete;

    public GuideController(Client client, WorldMapPointManager points, ShortestPathBridge paths,
        CaRouteConfig config, Consumer<GuideView> changed)
    {
        this.client = Objects.requireNonNull(client); this.points = Objects.requireNonNull(points);
        this.paths = Objects.requireNonNull(paths); this.config = Objects.requireNonNull(config);
        this.changed = Objects.requireNonNull(changed); pathEnabled = config.useShortestPath();
    }
    public ActiveGuide current() { return current; }
    public GuideView view() { ActiveGuide guide = current; return guide == null ? GuideView.inactive() : guide.view(); }
    public boolean startTask(CombatAchievement task, PlayerProfile profile)
    {
        if (!verified(profile) || profile.completedTaskIds().contains(task.id())) return false;
        start(task, task.activity(), profile); return true;
    }
    public boolean startActivity(String activity, PlayerProfile profile)
    {
        if (!verified(profile) || activity == null || activity.trim().isEmpty()) return false;
        start(null, activity, profile); return true;
    }
    private static boolean verified(PlayerProfile profile)
    {
        // The reader's loaded flag already requires safe bundled task identities.
        // catalogueComplete describes full live coverage, which may be false after
        // added tasks or a threshold fallback while existing tasks remain safe.
        return profile != null && profile.loaded() && !profile.accountKey().isEmpty();
    }
    private void start(CombatAchievement task, String activity, PlayerProfile profile)
    {
        removeMarker(); account = profile.accountKey(); pathEnabled = config.useShortestPath();
        ActivityDestinations.Destination destination = (task == null
            ? ActivityDestinations.forActivity(activity) : ActivityDestinations.forTask(task)).orElse(null);
        travelComplete = false;
        current = new ActiveGuide(task, activity, destination, "", profile.killCounts().get(activity), caution(task, destination));
        travelComplete = reachedDestination(current);
        updateMarker();
        if (destination != null && pathEnabled && !travelComplete) paths.start(destination.point()); else paths.stop();
        navigationChanged();
    }
    public void refresh(PlayerProfile profile)
    {
        ActiveGuide guide = current;
        if (guide == null) return;
        if (!verified(profile) || !account.equals(profile.accountKey())
            || guide.task() != null && profile.completedTaskIds().contains(guide.task().id()))
        { stop(); return; }
        ActiveGuide updated = guide.withFacts(status(guide), profile.killCounts().get(guide.activity()), caution(guide.task(), guide.destination()));
        if (!Objects.equals(guide.killCount(), updated.killCount()) || !guide.caution().equals(updated.caution())
            || !guide.status().equals(updated.status()))
        { current = updated; publish(); }
    }
    private static String caution(CombatAchievement task, ActivityDestinations.Destination destination)
    {
        if (destination != null && destination.instructions().contains("Wilderness"))
            return "Travel approach in the Wilderness: PvP risk.";
        if (task == null) return "Access and individual task requirements still apply.";
        if (task.flags().contains(TaskFlag.WILDERNESS)) return "Wilderness activity: bring only gear you are willing to risk.";
        if (!task.requirementsKnown()) return "Check access and task requirements before starting.";
        return "";
    }
    public void navigationChanged()
    {
        ActiveGuide guide = current;
        if (guide != null)
        {
            String status = status(guide);
            if (!status.equals(guide.status()))
            { current = guide.withFacts(status, guide.killCount(), guide.caution()); publish(); }
        }
    }
    private String status(ActiveGuide guide)
    {
        if (travelComplete) return "Entrance reached; continue with the selected task";
        if (guide.destination() == null) return "Task guide only; no verified map destination";
        if (!config.useShortestPath()) return "Destination selected; Shortest Path off";
        if (!paths.available()) return "Destination selected; enable Shortest Path for routing";
        return paths.ownsTarget() ? "Target sent to Shortest Path" : "Destination selected; select again to request a route";
    }
    public void configure()
    {
        ActiveGuide guide = current;
        boolean enabled = config.useShortestPath();
        if (!enabled) paths.stop();
        else if (!pathEnabled && guide != null && guide.destination() != null && !travelComplete) paths.start(guide.destination().point());
        pathEnabled = enabled; updateMarker(); navigationChanged();
    }
    private void updateMarker()
    {
        removeMarker(); ActiveGuide guide = current;
        if (guide == null || guide.destination() == null || !config.guideMapMarker()) return;
        marker = new WorldMapPoint(guide.destination().point(), CombatAchievementIcon.create());
        marker.setName("CA Route: " + guide.title());
        marker.setTooltip("CA Route: " + guide.activity() + "<br>" + guide.destination().label());
        marker.setImagePoint(new Point(16, 16)); marker.setSnapToEdge(true); marker.setJumpOnClick(true);
        points.add(marker);
    }
    private void removeMarker() { if (marker != null) { points.remove(marker); marker = null; } }
    public void focusMap()
    {
        ActiveGuide guide = current;
        if (guide != null && guide.destination() != null && client.getWorldMap() != null)
            client.getWorldMap().setWorldMapPositionTarget(guide.destination().point());
    }
    /** End travel at the public approach or a visible matching boss; keep the task reminder. */
    public void tick()
    {
        ActiveGuide guide = current;
        if (guide != null && !travelComplete && reachedDestination(guide))
        { travelComplete = true; paths.stop(); navigationChanged(); }
    }
    private boolean reachedDestination(ActiveGuide guide)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        WorldView world = client.getTopLevelWorldView(); Player player = client.getLocalPlayer();
        if (world == null || player == null) return false;
        net.runelite.api.coords.WorldPoint position = player.getWorldLocation();
        if (position == null) return false;
        if (world.npcs() != null)
            for (NPC npc : world.npcs())
                if (npc != null && guide.matchesNpc(npc.getName()) && npc.getWorldLocation() != null
                    && npc.getWorldLocation().getPlane() == position.getPlane()
                    && position.distanceTo(npc.getWorldLocation()) <= 12) return true;
        return guide.destination() != null && !world.isInstance()
            && position.distanceTo(guide.destination().point()) <= 3;
    }
    public void stop()
    { current = null; account = ""; travelComplete = false; removeMarker(); paths.stop(); publish(); }
    private void publish() { changed.accept(view()); }
}

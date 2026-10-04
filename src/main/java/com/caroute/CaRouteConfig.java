package com.caroute;

import com.caroute.domain.AchievementTier;
import com.caroute.domain.Preference;
import com.caroute.domain.RouteObjective;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CaRouteConfig.GROUP)
public interface CaRouteConfig extends Config
{
    String GROUP = "caroute";

    enum Target
    {
        NEXT_TIER(null), EASY(AchievementTier.EASY), MEDIUM(AchievementTier.MEDIUM),
        HARD(AchievementTier.HARD), ELITE(AchievementTier.ELITE),
        MASTER(AchievementTier.MASTER), GRANDMASTER(AchievementTier.GRANDMASTER);

        private final AchievementTier tier;
        Target(AchievementTier tier) { this.tier = tier; }
        public AchievementTier tier() { return tier; }
        @Override public String toString()
        {
            return tier == null ? "Next tier" : tier.displayName();
        }
    }

    @ConfigItem(keyName = "target", name = "Target tier", description = "Goal for the active account. Next tier advances automatically.", position = 0)
    default Target target() { return Target.NEXT_TIER; }

    @ConfigItem(keyName = "speed", name = "Speed tasks", description = "Prefer not raises planning cost; Avoid removes these tasks.", position = 1)
    default Preference speed() { return Preference.NORMAL; }

    @ConfigItem(keyName = "perfection", name = "Perfection tasks", description = "Preference for tasks requiring perfect execution.", position = 2)
    default Preference perfection() { return Preference.NORMAL; }

    @ConfigItem(keyName = "wilderness", name = "Wilderness", description = "Preference for tasks involving Wilderness risk.", position = 3)
    default Preference wilderness() { return Preference.PREFER_NOT; }

    @ConfigItem(keyName = "group", name = "Group tasks", description = "Preference for tasks requiring other players.", position = 4)
    default Preference group() { return Preference.PREFER_NOT; }

    @ConfigItem(keyName = "raid", name = "Raids", description = "Preference for raid tasks.", position = 5)
    default Preference raid() { return Preference.NORMAL; }

    @ConfigItem(keyName = "longKc", name = "Long kill counts", description = "Preference for long kill-count grinds.", position = 6)
    default Preference longKc() { return Preference.NORMAL; }

    @ConfigItem(keyName = "rng", name = "RNG tasks", description = "Preference for tasks with random outcomes.", position = 7)
    default Preference rng() { return Preference.PREFER_NOT; }

    @ConfigItem(keyName = "allowStretch", name = "Include stretch tasks", description = "Include tasks substantially above recommended stats with preparation warnings.", position = 8)
    default boolean allowStretch() { return false; }

    @ConfigItem(keyName = "observeBank", name = "Observe open bank gear", description = "Locally remember positive item observations from an open bank for this login. Unseen items stay unknown.", position = 9)
    default boolean observeBank() { return false; }

    @ConfigItem(keyName = "objective", name = "Route mode", description = "Tier route, time-budget planning, or completion of one selected boss.", position = 10)
    default RouteObjective objective() { return RouteObjective.EASIEST_PROGRESSION; }

    @Range(min = 1, max = 1440)
    @ConfigItem(keyName = "timeBudgetMinutes", name = "Active-time budget", description = "Minutes for Most points in time budget. Includes reported active preparation; excludes unknown waiting time.", position = 11)
    default int timeBudgetMinutes() { return 60; }

    @ConfigItem(keyName = "selectedActivity", name = "Boss to complete", description = "Exact activity name for Complete one boss; use the Profile picker to choose.", position = 12)
    default String selectedActivity() { return ""; }

    @ConfigSection(name = "Active guide", description = "Travel and visual reminders after explicitly selecting a task or boss.", position = 13)
    String GUIDE_SECTION = "guide";

    @ConfigItem(keyName = "useShortestPath", name = "Use Shortest Path", description = "Send the selected entrance to the enabled Shortest Path plugin. Its travel settings remain in use.", section = GUIDE_SECTION, position = 0)
    default boolean useShortestPath() { return true; }

    @ConfigItem(keyName = "guideMapMarker", name = "World map marker", description = "Mark the verified entrance for the active task or boss.", section = GUIDE_SECTION, position = 1)
    default boolean guideMapMarker() { return true; }

    @ConfigItem(keyName = "guideTaskOverlay", name = "Task reminder", description = "Show the selected task's exact condition and recorded KC when known. Details and route status stay in the sidebar.", section = GUIDE_SECTION, position = 2)
    default boolean guideTaskOverlay() { return true; }

    @ConfigItem(keyName = "guideSceneMarker", name = "Entrance tile", description = "Highlight the selected entrance when it is visible in the overworld.", section = GUIDE_SECTION, position = 3)
    default boolean guideSceneMarker() { return true; }

    @ConfigItem(keyName = "guideNpcHighlight", name = "Boss highlight", description = "Highlight visible NPCs belonging to the selected encounter.", section = GUIDE_SECTION, position = 4)
    default boolean guideNpcHighlight() { return true; }

    @ConfigItem(keyName = "guideMinimapMarker", name = "Minimap marker", description = "Mark the selected entrance when it is within local minimap range.", section = GUIDE_SECTION, position = 5)
    default boolean guideMinimapMarker() { return true; }
}

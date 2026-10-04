package com.caroute.ui;

import com.caroute.CaRouteConfig;
import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import java.awt.*;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiNavigationTest extends RuneLiteUiTestSupport
{
    @Test public void taskLinksUseTheSpecificAchievementRatherThanTheImportedBossPage()
    {
        CombatAchievement adept = task(332), demonbane = task(339);
        assertTrue(adept.wikiUrl().endsWith("/K'ril_Tsutsaroth"));
        assertEquals("https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth_Adept", WikiLinks.task(adept));
        assertEquals("https://oldschool.runescape.wiki/w/Demonbane_Weaponry_II", WikiLinks.task(demonbane));
        CombatAchievement punctuation = CombatAchievement.builder(1000, "Don't Panic: A & B?", "Boss", AchievementTier.EASY)
            .wikiUrl("https://oldschool.runescape.wiki/w/Boss").build();
        assertEquals("https://oldschool.runescape.wiki/w/Don%27t_Panic%3A_A_%26_B%3F", WikiLinks.task(punctuation));
    }

    @Test public void activityLinksPreserveBossIdentityAndUseKnownRaidArticles()
    {
        assertEquals("https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth", WikiLinks.activity("K'ril Tsutsaroth", List.of(wrap(task(332)))));
        assertEquals("https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Challenge_Mode",
            WikiLinks.activity("Chambers of Xeric: Challenge Mode", List.of(wrap(firstActivity("Chambers of Xeric: Challenge Mode")))));
        assertEquals("https://oldschool.runescape.wiki/w/Theatre_of_Blood/Entry_Mode",
            WikiLinks.activity("Theatre of Blood: Entry Mode", List.of(wrap(firstActivity("Theatre of Blood: Entry Mode")))));
        assertEquals("https://oldschool.runescape.wiki/w/Tombs_of_Amascut",
            WikiLinks.activity("Tombs of Amascut: Expert Mode", List.of(wrap(firstActivity("Tombs of Amascut: Expert Mode")))));
        String recent = WikiLinks.activity("Maggot King", List.of(wrap(task(637))));
        assertEquals("https://oldschool.runescape.wiki/w/Special:Search?search=Maggot+King", recent);
        assertTrue(WikiLinks.search(recent)); assertFalse(recent.contains("Maggot_Squasher"));
    }

    @Test public void bundledTaskLinksAreSafeCompleteAndKeepTheirIndividualNames()
    {
        Set<String> urls = new HashSet<>();
        for (CombatAchievement task : AchievementRepository.loadBundled().tasks())
        {
            String link = WikiLinks.task(task); assertFalse(task.name(), link.isEmpty());
            URI parsed = URI.create(link); assertEquals("https", parsed.getScheme()); assertEquals("oldschool.runescape.wiki", parsed.getHost());
            assertEquals("/w/" + task.name().replace(' ', '_'), parsed.getPath());
            assertTrue("Distinct canonical task names retain distinct articles", urls.add(link));
        }
        CombatAchievement unsupported = CombatAchievement.builder(1000, "Unsupported", "Boss", AchievementTier.EASY)
            .wikiUrl("https://example.com/wiki/Boss").build();
        assertEquals("", WikiLinks.task(unsupported)); assertEquals("", WikiLinks.activity("Boss", List.of(wrap(unsupported))));
    }

    @Test public void independentHeaderWikiActionsDoNotToggleAccordionsOrMutateTheAccount() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> opened = new ArrayList<>(); List<RouteObjective> modes = new ArrayList<>();
            CaRoutePanel panel = panel(opened, modes); display(panel, "one", 1, RouteObjective.FASTEST_NEXT_TIER);
            assertTrue(opened.isEmpty()); assertTrue(modes.isEmpty());
            JButton taskWiki = control(panel, "wiki.route.task.332", JButton.class);
            JComponent taskDetails = control(panel, "details.route.task.332", JComponent.class);
            assertFalse(taskDetails.isVisible()); taskWiki.doClick(0); assertFalse(taskDetails.isVisible());
            assertEquals(List.of(WikiLinks.task(task(332))), opened);
            control(panel, "wiki.route.boss", JButton.class).doClick(0);
            JComponent bossDetails = control(panel, "details.boss.K'ril Tsutsaroth", JComponent.class);
            control(panel, "wiki.boss.K'ril Tsutsaroth", JButton.class).doClick(0); assertFalse(bossDetails.isVisible());
            assertEquals(3, opened.size()); assertEquals("https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth", opened.get(1));
            assertEquals(opened.get(1), opened.get(2)); assertTrue(modes.isEmpty());
            control(panel, "collapse.route.task.332", JButton.class).doClick(0);
            assertTrue(taskDetails.isVisible()); control(taskDetails, "task.wiki.332", JButton.class).doClick(0);
            assertEquals(WikiLinks.task(task(332)), opened.get(3));
        });
    }

    @Test public void detachedWikiLinksCannotOpenPagesAfterRerouteLogoutOrAccountSwitch() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> opened = new ArrayList<>(); CaRoutePanel panel = panel(opened, new ArrayList<>());
            display(panel, "one", 1, RouteObjective.FASTEST_NEXT_TIER);
            JButton oldTask = control(panel, "wiki.route.task.332", JButton.class), oldBoss = control(panel, "wiki.route.boss", JButton.class);
            display(panel, "one", 2, RouteObjective.FASTEST_NEXT_TIER); oldTask.doClick(0); oldBoss.doClick(0); assertTrue(opened.isEmpty());
            JButton beforeLogout = control(panel, "wiki.route.task.332", JButton.class);
            panel.showMessage("Log in"); beforeLogout.doClick(0); assertTrue(opened.isEmpty());
            display(panel, "two", 1, RouteObjective.FASTEST_NEXT_TIER); beforeLogout.doClick(0); assertTrue(opened.isEmpty());
            control(panel, "wiki.route.task.332", JButton.class).doClick(0); assertEquals(1, opened.size());
        });
    }

    @Test public void easyToHardShortcutOnlyChangesModeOnAValidExplicitClick() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<RouteObjective> modes = new ArrayList<>(); CaRoutePanel panel = panel(new ArrayList<>(), modes);
            display(panel, "one", 1, RouteObjective.IRONMAN_EFFICIENT); assertTrue(modes.isEmpty());
            JButton shortcut = control(panel, "route.easyToHard", JButton.class); shortcut.doClick(0);
            assertEquals(List.of(RouteObjective.EASIEST_PROGRESSION), modes);
            display(panel, "one", 2, RouteObjective.EASIEST_PROGRESSION); assertNull(named(panel, "route.easyToHard"));
            shortcut.doClick(0); assertEquals(1, modes.size());
        });
    }

    @Test public void routeChoicesWriteOnlyOnCurrentExplicitSelectionsAndNeverStartGuidance() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<CaRouteConfig.Target> goals = new ArrayList<>(); List<RouteObjective> modes = new ArrayList<>();
            List<String> guides = new ArrayList<>(); List<String> opened = new ArrayList<>();
            CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, goals::add,
                (flag, preference) -> {}, (id, owned) -> {}, enabled -> {}, () -> {}, new ProfileActions() {
                    @Override public void objective(RouteObjective objective) { modes.add(objective); }
                }, new GuideActions() {
                    @Override public void startTask(int taskId) { guides.add("task:" + taskId); }
                    @Override public void startActivity(String activity) { guides.add("activity:" + activity); }
                    @Override public void stop() { guides.add("stop"); }
                    @Override public void focusMap() { guides.add("map"); }
                }, opened::add);
            display(panel, "one", 1, RouteObjective.EASIEST_PROGRESSION);
            assertEquals("CA Route", control(panel, "ca.title", JLabel.class).getText());
            assertEquals("The persistent plugin title follows Quest Helper's white header", Color.WHITE,
                control(panel, "ca.title", JLabel.class).getForeground());
            assertTrue("Rendering never changes the goal", goals.isEmpty());
            assertTrue("Rendering never changes the ordering", modes.isEmpty());
            JComboBox<?> oldGoal = control(panel, "route.target", JComboBox.class);
            JComboBox<?> oldMode = control(panel, "route.objective", JComboBox.class);
            oldGoal.setSelectedItem(CaRouteConfig.Target.MEDIUM); oldMode.setSelectedItem(RouteObjective.IRONMAN_EFFICIENT);
            assertEquals(List.of(CaRouteConfig.Target.MEDIUM), goals);
            assertEquals(List.of(RouteObjective.IRONMAN_EFFICIENT), modes);

            display(panel, "one", 2, RouteObjective.EASIEST_PROGRESSION);
            oldGoal.setSelectedItem(CaRouteConfig.Target.HARD); oldMode.setSelectedItem(RouteObjective.FASTEST_NEXT_TIER);
            assertEquals("Detached goal controls cannot change the refreshed account", List.of(CaRouteConfig.Target.MEDIUM), goals);
            assertEquals("Detached ordering controls cannot change the refreshed account", List.of(RouteObjective.IRONMAN_EFFICIENT), modes);
            JComboBox<?> beforeLogoutGoal = control(panel, "route.target", JComboBox.class);
            JComboBox<?> beforeLogoutMode = control(panel, "route.objective", JComboBox.class);
            panel.showMessage("Log in");
            beforeLogoutGoal.setSelectedItem(CaRouteConfig.Target.ELITE); beforeLogoutMode.setSelectedItem(RouteObjective.MAXIMUM_POINTS_PER_HOUR);
            assertEquals("Logged-out controls remain inert", List.of(CaRouteConfig.Target.MEDIUM), goals);
            assertEquals("Logged-out ordering remains inert", List.of(RouteObjective.IRONMAN_EFFICIENT), modes);

            display(panel, "two", 1, RouteObjective.EASIEST_PROGRESSION);
            beforeLogoutGoal.setSelectedItem(CaRouteConfig.Target.MASTER); beforeLogoutMode.setSelectedItem(RouteObjective.BOSS_COMPLETION);
            control(panel, "route.target", JComboBox.class).setSelectedItem(CaRouteConfig.Target.ELITE);
            control(panel, "route.objective", JComboBox.class).setSelectedItem(RouteObjective.FASTEST_NEXT_TIER);
            assertEquals(List.of(CaRouteConfig.Target.MEDIUM, CaRouteConfig.Target.ELITE), goals);
            assertEquals(List.of(RouteObjective.IRONMAN_EFFICIENT, RouteObjective.FASTEST_NEXT_TIER), modes);
            assertTrue("Goal and ordering selections never invoke guide actions", guides.isEmpty());
            assertTrue("Goal and ordering selections never open Wiki links", opened.isEmpty());
        });
    }

    private static CaRoutePanel panel(List<String> opened, List<RouteObjective> modes)
    { return new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {},
        (id, owned) -> {}, enabled -> {}, () -> {}, new ProfileActions() {
            @Override public void objective(RouteObjective objective) { modes.add(objective); }
        }, opened::add); }
    private static void display(CaRoutePanel panel, String account, long version, RouteObjective objective)
    {
        List<TaskAssessment> assessments = AchievementRepository.loadBundled().tasks().stream()
            .filter(task -> task.activity().equals("K'ril Tsutsaroth")).map(WikiNavigationTest::wrap).collect(Collectors.toList());
        BossSession boss = new BossSession("K'ril Tsutsaroth", assessments, 36, 50, 50, List.of(), Confidence.LOW, true);
        RoutePlan plan = new RoutePlan(AchievementTier.HARD, 0, 100, 100, 36, 50, List.of(boss), assessments,
            List.of(), Reachability.UNREACHABLE_NOW, Confidence.LOW, false, version).withBossSessions(List.of(boss))
            .withScope(objective, "", 60, false);
        PlayerProfile profile = PlayerProfile.builder().accountKey(account).version(version).loaded(true).catalogueComplete(true).build();
        panel.display(plan, List.of(), RoutingPreferences.builder().objective(objective).build(), new AccountView(GearView.unknown(), profile, Map.of(), false));
    }
    private static TaskAssessment wrap(CombatAchievement task)
    { return new TaskAssessment(task, new Eligibility(EligibilityState.UNKNOWN_REQUIREMENT, List.of(), Confidence.LOW, true, true), 10, 10, Map.of(), List.of(), Confidence.LOW); }
    private static CombatAchievement task(int id)
    { return AchievementRepository.loadBundled().tasks().stream().filter(task -> task.id() == id).findFirst().orElseThrow(); }
    private static CombatAchievement firstActivity(String activity)
    { return AchievementRepository.loadBundled().tasks().stream().filter(task -> task.activity().equals(activity)).findFirst().orElseThrow(); }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { Component component = named(root, name); assertNotNull("Missing control " + name, component); assertTrue(type.isInstance(component)); return type.cast(component); }
    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { Component result = named(child, name); if (result != null) return result; }
        return null;
    }
}

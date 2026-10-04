package com.caroute.ui;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Canonical task names with synthetic guide facts; this does not assert live-game navigation. */
public class GuideUiTest extends RuneLiteUiTestSupport
{
    @Test public void onlyExplicitStartActionsSelectATaskOrBoss() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Recorder commands = new Recorder(); List<String> opened = new ArrayList<>();
            CaRoutePanel panel = panel(commands, opened); Fixture fixture = fixture("one", 1);
            display(panel, fixture, GuideView.inactive());
            assertTrue(commands.calls.isEmpty());
            Container route = tab(panel, "Route"); int id = fixture.first.task().id();
            control(route, "collapse.route.task." + id, JButton.class).doClick(0);
            control(route, "wiki.route.task." + id, JButton.class).doClick(0);
            control(route, "task.wiki." + id, JButton.class).doClick(0);
            control(panel, "collapse.boss.Amoxliatl", JButton.class).doClick(0);
            control(panel, "wiki.boss.Amoxliatl", JButton.class).doClick(0);
            control(route, "collapse.route.after", JButton.class).doClick(0);
            control(route, "collapse.route.stop.Scurrius", JButton.class).doClick(0);
            control(panel, "profile.activity", JComboBox.class).setSelectedItem("Scurrius");
            assertEquals(3, opened.size()); assertTrue(commands.calls.isEmpty());
            control(route, "task.start." + id, JButton.class).doClick(0);
            control(route, "guide.route.boss", JButton.class).doClick(0);
            control(panel, "guide.boss.Amoxliatl", JButton.class).doClick(0);
            control(route, "guide.route.stop.Scurrius", JButton.class).doClick(0);
            assertEquals(List.of("task:" + id, "boss:Amoxliatl", "boss:Amoxliatl", "boss:Scurrius"), commands.calls);
        });
    }

    @Test public void completedTaskCannotStartAGuideAndUnknownRequirementsAreNotClaimedAsUnlocked() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Recorder commands = new Recorder(); CaRoutePanel panel = panel(commands, new ArrayList<>());
            Fixture fixture = fixture("one", 1); display(panel, fixture, GuideView.inactive());
            assertEquals(EligibilityState.UNKNOWN_REQUIREMENT, fixture.first.eligibility().state());
            JButton unknown = control(tab(panel, "Route"), "task.start." + fixture.first.task().id(), JButton.class);
            assertTrue("The controller validates an explicit guide request; rendering never invents access", unknown.isEnabled());
            Container browser = tab(panel, "Tasks");
            control(browser, "tasks.status", JComboBox.class).setSelectedItem("Completed");
            int id = fixture.completed.task().id();
            control(browser, "collapse.tasks.task." + id, JButton.class).doClick(0);
            JButton start = control(browser, "task.start." + id, JButton.class);
            assertFalse(start.isEnabled()); start.doClick(0); assertTrue(commands.calls.isEmpty());
        });
    }

    @Test public void activeGuideShowsControllerFeedbackAndSupportsMapAndStopWithoutRebuildingThePlan() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Recorder commands = new Recorder(); CaRoutePanel panel = panel(commands, new ArrayList<>());
            Fixture fixture = fixture("one", 1); GuideView view = guide(fixture, true);
            display(panel, fixture, view); assertTrue(commands.calls.isEmpty());
            Container route = tab(panel, "Route");
            assertEquals(view.title(), control(route, "guide.title", JTextArea.class).getText());
            assertEquals(view.description(), control(route, "guide.title", JTextArea.class).getToolTipText());
            assertEquals(view.destination(), control(route, "guide.destination", JTextArea.class).getText());
            assertEquals(view.status(), control(route, "guide.status", JTextArea.class).getText());
            assertEquals(view.instructions(), control(route, "guide.instructions", JTextArea.class).getText());
            assertFalse("Travel instructions stay collapsed in the default guide overview", control(route, "details.guide.travel.Amoxliatl", JComponent.class).isVisible());
            control(route, "collapse.guide.travel.Amoxliatl", JButton.class).doClick(0);
            assertTrue(control(route, "details.guide.travel.Amoxliatl", JComponent.class).isVisible()); assertTrue(commands.calls.isEmpty());
            assertEquals("Open the world map, then center it on this destination", control(route, "guide.map", JButton.class).getToolTipText());
            JButton task = control(route, "task.start." + fixture.first.task().id(), JButton.class);
            control(route, "guide.map", JButton.class).doClick(0); control(route, "guide.stop", JButton.class).doClick(0);
            assertEquals(List.of("map", "stop"), commands.calls);
            panel.displayGuide(guide(fixture, false));
            assertTrue("Guide refreshes retain the account's travel disclosure choice", control(route, "details.guide.travel.Amoxliatl", JComponent.class).isVisible());
            assertSame("A guide update retains existing route controls", task, control(route, "task.start." + fixture.first.task().id(), JButton.class));
            JButton map = control(route, "guide.map", JButton.class); assertFalse(map.isEnabled()); map.doClick(0);
            assertEquals(2, commands.calls.size());
            panel.displayGuide(GuideView.inactive()); assertNull(named(route, "guide.active"));
            assertSame(fixture.plan, panel.currentPlan());
        });
        GuideView absent = new GuideView(null, null, null, null, null, null, false);
        assertFalse(absent.active()); assertEquals("", absent.destination()); assertEquals("", absent.instructions()); assertNull(absent.taskId());
    }

    @Test public void detachedStartMapAndStopControlsStayBoundToTheirOriginalRender() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Recorder commands = new Recorder(); CaRoutePanel panel = panel(commands, new ArrayList<>());
            Fixture fixture = fixture("one", 1); display(panel, fixture, guide(fixture, true));
            JButton oldMap = control(panel, "guide.map", JButton.class), oldStop = control(panel, "guide.stop", JButton.class);
            JButton oldTask = control(tab(panel, "Route"), "task.start." + fixture.first.task().id(), JButton.class);
            JButton oldBoss = control(panel, "guide.route.boss", JButton.class);
            TaskBrowserPanel oldBrowser = findType(panel, TaskBrowserPanel.class);
            panel.displayGuide(guide(fixture, true)); oldMap.doClick(0); oldStop.doClick(0); assertTrue(commands.calls.isEmpty());
            oldTask.doClick(0); assertEquals(List.of("task:" + fixture.first.task().id()), commands.calls); commands.calls.clear();
            Fixture refreshed = fixture("one", 2); display(panel, refreshed, guide(refreshed, true));
            oldTask.doClick(0); oldBoss.doClick(0); oldMap.doClick(0); oldStop.doClick(0); assertTrue(commands.calls.isEmpty());
            JButton beforeSwitch = control(panel, "guide.stop", JButton.class);
            Fixture other = fixture("two", 1); display(panel, other, guide(other, true)); beforeSwitch.doClick(0);
            control(oldBrowser, "tasks.search", JTextField.class).setText(fixture.first.task().name());
            control(oldBrowser, "task.start." + fixture.first.task().id(), JButton.class).doClick(0);
            assertTrue("Lazy rendering in a detached browser must also retain its original generation", commands.calls.isEmpty());
            JButton beforeLogout = control(panel, "guide.map", JButton.class), taskBeforeLogout = control(tab(panel, "Route"), "task.start." + other.first.task().id(), JButton.class);
            panel.showMessage("Log in"); beforeLogout.doClick(0); taskBeforeLogout.doClick(0);
            panel.displayGuide(guide(other, true)); assertNull(named(panel, "guide.active")); assertTrue(commands.calls.isEmpty());
            display(panel, other, guide(other, true)); control(panel, "guide.map", JButton.class).doClick(0);
            assertEquals(List.of("map"), commands.calls);
        });
    }

    @Test public void oldConstructorAndDisplayOverloadsRetainTheirNoOpGuideBehavior() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {});
            Fixture fixture = fixture("one", 1);
            panel.display(fixture.plan, List.of(), fixture.preferences, fixture.account);
            control(panel, "guide.route.boss", JButton.class).doClick(0);
            control(tab(panel, "Route"), "task.start." + fixture.first.task().id(), JButton.class).doClick(0);
            assertNull(named(panel, "guide.active"));
            panel.displayGuide(guide(fixture, true)); assertNotNull(named(panel, "guide.active"));
            panel.display(fixture.plan, List.of(), fixture.preferences, fixture.account); assertNull(named(panel, "guide.active"));
        });
    }

    @Test public void guideAndTaskControlsRemainUsableAtNarrowRuneLiteWidths() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                for (int width : new int[] {225, 280})
                {
                    Recorder commands = new Recorder(); CaRoutePanel panel = panel(commands, new ArrayList<>());
                    Fixture fixture = fixture("preview", 1); display(panel, fixture, guide(fixture, true)); panel.setSize(width, 1000);
                    int id = fixture.first.task().id();
                    control(tab(panel, "Route"), "collapse.route.task." + id, JButton.class).doClick(0);
                    render(panel, "Route", width, "route");
                    for (String name : List.of("guide.map", "guide.stop", "guide.route.boss", "task.start." + id, "task.actions." + id, "task.wiki." + id))
                        assertButtonFits(tab(panel, "Route"), name, width);
                    JPanel active = control(tab(panel, "Route"), "guide.active", JPanel.class);
                    assertTrue("Active guide feedback stays compact", active.getHeight() <= 260);
                    control(tab(panel, "Bosses"), "collapse.boss.Amoxliatl", JButton.class).doClick(0);
                    control(tab(panel, "Bosses"), "collapse.boss.task." + id, JButton.class).doClick(0);
                    render(panel, "Bosses", width, "bosses");
                    for (String name : List.of("guide.boss.Amoxliatl", "task.start." + id, "task.actions." + id, "task.wiki." + id))
                        assertButtonFits(tab(panel, "Bosses"), name, width);
                    control(tab(panel, "Tasks"), "tasks.search", JTextField.class).setText(fixture.first.task().name());
                    control(tab(panel, "Tasks"), "collapse.tasks.task." + id, JButton.class).doClick(0);
                    render(panel, "Tasks", width, "tasks");
                    for (String name : List.of("task.start." + id, "task.actions." + id, "task.wiki." + id)) assertButtonFits(tab(panel, "Tasks"), name, width);
                    assertTrue("Layout and disclosure never start a guide", commands.calls.isEmpty());
                }
            }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static GuideView guide(Fixture fixture, boolean canFocus)
    { return new GuideView(fixture.first.task().name(), fixture.first.task().activity(), fixture.first.task().id(),
        fixture.first.task().description(), "Amoxliatl entrance", "Travel manually. Access has not been verified.", canFocus,
        "Check the activity's access requirements, then travel to the entrance. Enter manually."); }
    private static CaRoutePanel panel(Recorder commands, List<String> opened)
    { return new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {},
        (id, owned) -> {}, enabled -> {}, () -> {}, new ProfileActions(){}, commands, opened::add); }
    private static void display(CaRoutePanel panel, Fixture fixture, GuideView view)
    { panel.display(fixture.plan, List.of("Synthetic preview."), fixture.preferences, fixture.account, view); }
    private static Fixture fixture(String key, long version)
    {
        List<CombatAchievement> bundled = AchievementRepository.loadBundled().tasks();
        List<CombatAchievement> amoxliatl = bundled.stream().filter(task -> task.activity().equals("Amoxliatl")).collect(Collectors.toList());
        TaskAssessment first = wrap(amoxliatl.get(0), EligibilityState.UNKNOWN_REQUIREMENT), second = wrap(amoxliatl.get(1), EligibilityState.UNKNOWN_REQUIREMENT);
        TaskAssessment completed = wrap(amoxliatl.get(2), EligibilityState.COMPLETED);
        TaskAssessment scurrius = wrap(bundled.stream().filter(task -> task.activity().equals("Scurrius")).findFirst().orElseThrow(), EligibilityState.UNKNOWN_REQUIREMENT);
        BossSession amox = session("Amoxliatl", List.of(first, second)), rat = session("Scurrius", List.of(scurrius));
        List<TaskAssessment> assessments = List.of(first, second, completed, scurrius);
        RoutePlan plan = new RoutePlan(AchievementTier.MEDIUM, 118, 169, 51, amox.points() + rat.points(), 20,
            List.of(amox, rat), assessments, List.of(), Reachability.UNREACHABLE_NOW, Confidence.LOW, false, version)
            .withBossSessions(List.of(amox, rat)).withScope(RouteObjective.EASIEST_PROGRESSION, "", 60, false);
        PlayerProfile profile = PlayerProfile.builder().accountKey(key).loaded(true).version(version).catalogueComplete(true).build();
        return new Fixture(plan, first, completed, RoutingPreferences.builder().objective(RouteObjective.EASIEST_PROGRESSION).build(),
            new AccountView(GearView.unknown(), profile, Map.of(), true));
    }
    private static TaskAssessment wrap(CombatAchievement task, EligibilityState state)
    { return new TaskAssessment(task, new Eligibility(state, List.of(), Confidence.LOW, state != EligibilityState.COMPLETED, state != EligibilityState.COMPLETED),
        5, 5, Map.of(), List.of(), Confidence.LOW); }
    private static BossSession session(String activity, List<TaskAssessment> tasks)
    { return new BossSession(activity, tasks, tasks.stream().mapToInt(task -> task.task().points()).sum(), 10, 10, List.of(), Confidence.LOW, true); }
    private static void render(CaRoutePanel panel, String title, int width, String state) throws Exception
    {
        JTabbedPane tabs = tabs(panel); int tab = tabs.indexOfTab(title); assertTrue("Missing tab " + title, tab >= 0); tabs.setSelectedIndex(tab); for (int pass = 0; pass < 5; pass++) layout(panel);
        JScrollPane scroll = (JScrollPane) tabs(panel).getComponentAt(tab);
        assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
        BufferedImage image = new BufferedImage(width, panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        Path path = Paths.get("build", "ui-guide-" + state + "-" + width + ".png"); Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
    }
    private static void assertButtonFits(Container page, String name, int width)
    {
        JButton button = control(page, name, JButton.class); Insets insets = button.getInsets();
        Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), page);
        assertTrue(name + " must stay within the page at " + width + "px: " + bounds, bounds.x >= 0 && bounds.x + bounds.width <= page.getWidth());
        assertTrue(name + " needs a readable full label at " + width + "px: " + button.getSize(),
            button.getWidth() >= button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + insets.left + insets.right);
        assertTrue(name + " must retain usable height", button.getHeight() >= button.getPreferredSize().height);
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static JTabbedPane tabs(Component root) { return findType(root, JTabbedPane.class); }
    private static Container tab(CaRoutePanel panel, String title)
    { JTabbedPane tabs = tabs(panel); int index = tabs.indexOfTab(title); assertTrue("Missing tab " + title, index >= 0); return (Container) ((JScrollPane) tabs.getComponentAt(index)).getViewport().getView(); }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { Component component = named(root, name); assertNotNull("Missing control " + name, component); assertTrue(type.isInstance(component)); return type.cast(component); }
    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { Component result = named(child, name); if (result != null) return result; }
        return null;
    }
    private static <T extends Component> T findType(Component root, Class<T> type)
    {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T result = findType(child, type); if (result != null) return result; }
        return null;
    }
    private static final class Recorder implements GuideActions
    {
        final List<String> calls = new ArrayList<>();
        @Override public void startTask(int id) { calls.add("task:" + id); }
        @Override public void startActivity(String activity) { calls.add("boss:" + activity); }
        @Override public void focusMap() { calls.add("map"); }
        @Override public void stop() { calls.add("stop"); }
    }
    private static final class Fixture
    {
        final RoutePlan plan; final TaskAssessment first, completed; final RoutingPreferences preferences; final AccountView account;
        Fixture(RoutePlan plan, TaskAssessment first, TaskAssessment completed, RoutingPreferences preferences, AccountView account)
        { this.plan = plan; this.first = first; this.completed = completed; this.preferences = preferences; this.account = account; }
    }
}

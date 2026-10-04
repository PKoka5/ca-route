package com.caroute.ui;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import com.caroute.engine.PreparationAdvisor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses real task metadata with synthetic account/route facts, never live-game verification. */
public class CompactUiTest extends RuneLiteUiTestSupport
{
    @Test public void taskTitlesAreInitiallyCompactAndOpeningRevealsTheSpecificDetails() throws Exception
    {
        Fixture fixture = fixture("account-one", 1);
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, fixture); tabs(panel).setSelectedIndex(tabs(panel).indexOfTab("Route")); Container route = tab(panel, "Route");
            for (TaskAssessment assessment : fixture.session.tasks())
            {
                String key = "route.task." + assessment.task().id();
                JButton heading = control(route, "collapse." + key, JButton.class);
                JComponent details = control(route, "details." + key, JComponent.class);
                assertEquals("Collapsed headings show only the task title", assessment.task().name(), heading.getText());
                assertFalse("Task details should start collapsed", details.isVisible());
                assertFalse("Descriptions must not fill the default overview", visibleText(route).contains(assessment.task().description()));
            }
            TaskAssessment selected = fixture.session.tasks().get(0);
            String key = "route.task." + selected.task().id();
            JButton heading = control(route, "collapse." + key, JButton.class);
            JComponent details = control(route, "details." + key, JComponent.class);
            heading.doClick(0);
            assertTrue(details.isVisible());
            assertTrue(visibleText(details).contains(selected.task().description()));
            assertNotNull("Actions belong to the expanded task", named(details, "task.actions." + selected.task().id()));
            heading.doClick(0);
            assertFalse(details.isVisible());
            assertFalse(visibleText(route).contains(selected.task().description()));
        });
    }

    @Test public void disclosuresSurviveReroutingButResetOnLogoutAndAccountChanges() throws Exception
    {
        Fixture first = fixture("account-one", 1);
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, first);
            int id = first.session.tasks().get(0).task().id();
            String header = "collapse.route.task." + id, body = "details.route.task." + id;
            control(tab(panel, "Route"), header, JButton.class).doClick(0);
            assertTrue(control(tab(panel, "Route"), body, JComponent.class).isVisible());
            display(panel, fixture("account-one", 2));
            assertTrue("A refreshed route for the same account preserves the user's open task", control(tab(panel, "Route"), body, JComponent.class).isVisible());
            display(panel, fixture("account-two", 1));
            assertFalse("Another account starts with a compact overview", control(tab(panel, "Route"), body, JComponent.class).isVisible());
            control(tab(panel, "Route"), header, JButton.class).doClick(0);
            panel.showMessage("Log in to view your route");
            display(panel, fixture("account-two", 2));
            assertFalse("Logout resets disclosure state even when the same account logs back in", control(tab(panel, "Route"), body, JComponent.class).isVisible());
        });
    }

    @Test public void taskBrowserRemembersExpandedTasksAcrossFilteringWithoutOpeningOtherTasks() throws Exception
    {
        Fixture fixture = fixture("account-one", 1);
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, fixture); tabs(panel).setSelectedIndex(tabs(panel).indexOfTab("Tasks"));
            Container browser = tab(panel, "Tasks");
            TaskAssessment selected = fixture.session.tasks().get(0);
            String key = "tasks.task." + selected.task().id();
            control(browser, "collapse." + key, JButton.class).doClick(0);
            assertTrue(control(browser, "details." + key, JComponent.class).isVisible());
            JTextField search = control(browser, "tasks.search", JTextField.class);
            search.setText("no task matches this text");
            assertNull(named(browser, "collapse." + key));
            search.setText(selected.task().name());
            assertTrue("Filtering rebuilds the task list while retaining its disclosure choice", control(browser, "details." + key, JComponent.class).isVisible());
            search.setText("");
            for (TaskAssessment assessment : fixture.session.tasks())
                if (assessment.task().id() != selected.task().id())
                    assertFalse(control(browser, "details.tasks.task." + assessment.task().id(), JComponent.class).isVisible());
            assertFalse("Opening a browser task should not open its route copy", control(tab(panel, "Route"), "details.route.task." + selected.task().id(), JComponent.class).isVisible());
        });
    }

    @Test public void compactAndExpandedSidebarsRenderAtRuneLiteWidths() throws Exception
    {
        Fixture fixture = fixture("preview-account", 1);
        assertEquals(8, fixture.session.tasks().size()); assertEquals(23, fixture.session.points());
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                for (int width : new int[] {225, 235, 260, 280})
                {
                    CaRoutePanel panel = panel(); display(panel, fixture); panel.setSize(width, 900);
                    JTabbedPane navigation = tabs(panel); String[] titles = {"Tasks", "Bosses", "Route", "Profile", "Prep"};
                    assertEquals(titles.length, navigation.getTabCount());
                    for (int index = 0; index < titles.length; index++) assertEquals(titles[index], navigation.getTitleAt(index));
                    assertEquals("Tasks is the initial page", "Tasks", navigation.getTitleAt(navigation.getSelectedIndex()));
                    render(panel, "Route", width, "collapsed");
                    JScrollPane routeScroll = (JScrollPane) tabs(panel).getComponentAt(tabs(panel).indexOfTab("Route"));
                    int collapsedHeight = routeScroll.getViewport().getView().getPreferredSize().height;
                    int id = fixture.session.tasks().get(0).task().id();
                    control(tab(panel, "Route"), "collapse.route.task." + id, JButton.class).doClick(0);
                    render(panel, "Route", width, "expanded");
                    JComponent expanded = control(tab(panel, "Route"), "details.route.task." + id, JComponent.class);
                    JButton heading = control(tab(panel, "Route"), "collapse.route.task." + id, JButton.class);
                    JButton actions = control(expanded, "task.actions." + id, JButton.class);
                    assertTrue("Expanded task must allocate space for its content at " + width + "px", expanded.getHeight() >= 100);
                    assertEquals("The expanded title uses the same width as its detail body at " + width + "px", expanded.getWidth(), heading.getWidth());
                    assertTitleFits(heading, width);
                    assertTitlePainted(heading, width);
                    assertTrue("Expanded task must make its actions usable at " + width + "px", actions.getWidth() > 0 && actions.getHeight() > 0);
                    Rectangle actionBounds = SwingUtilities.convertRectangle(actions.getParent(), actions.getBounds(), routeScroll.getViewport().getView());
                    assertTrue("The first expanded task's actions should be within the visible scroll area", routeScroll.getViewport().getViewRect().contains(actionBounds));
                    assertTrue("Expanding a task increases the page's measured height", routeScroll.getViewport().getView().getPreferredSize().height > collapsedHeight + 100);
                    if (width == 225)
                    {
                        render(panel, "Bosses", width, "bosses"); render(panel, "Tasks", width, "tasks");
                    }
                    if (width == 260)
                    {
                        render(panel, "Profile", width, "profile"); render(panel, "Prep", width, "prepare");
                    }
                }
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }

    private static Fixture fixture(String accountKey, long version)
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks().stream()
            .filter(task -> task.activity().equals("Amoxliatl")).collect(Collectors.toList());
        assertEquals("The preview follows the bundled Amoxliatl catalogue", 9, tasks.size());
        List<String> checks = List.of("Check access before starting.", "Historical kill count is unknown.", "Time is approximate.");
        List<TaskAssessment> assessments = new ArrayList<>();
        for (CombatAchievement task : tasks)
        {
            boolean excluded = task.id() == 582; // Speed-Chaser is excluded solely for this eight-task preview.
            assessments.add(new TaskAssessment(task, new Eligibility(excluded ? EligibilityState.CONTENT_EXCLUDED
                : EligibilityState.UNKNOWN_REQUIREMENT, checks, Confidence.LOW, !excluded, !excluded),
                7, 8, Map.of(), checks, Confidence.LOW));
        }
        List<TaskAssessment> selected = assessments.stream().filter(task -> task.eligibility().recommendedNow()).collect(Collectors.toList());
        BossSession session = new BossSession("Amoxliatl", selected,
            selected.stream().mapToInt(task -> task.task().points()).sum(), 42, 48,
            List.of("Shared progression in one activity.", "Check access before travelling."), Confidence.LOW, true);
        PlayerProfile profile = PlayerProfile.builder().accountKey(accountKey).version(version).loaded(true).catalogueComplete(true)
            .currentPoints(118).thresholds(Map.of(AchievementTier.MEDIUM, 169)).skills(Map.of("Attack", 60)).build();
        RoutePlan plan = new RoutePlan(AchievementTier.MEDIUM, 118, 169, 51, session.points(), session.estimatedMinutes(),
            List.of(session), assessments, List.of(), Reachability.UNREACHABLE_NOW, Confidence.LOW, false, version)
            .withBossSessions(List.of(session)).withPreparationSteps(new PreparationAdvisor().advise(assessments, profile));
        RoutingPreferences preferences = RoutingPreferences.builder().target(AchievementTier.MEDIUM).build();
        return new Fixture(plan, session, profile, preferences);
    }

    private static CaRoutePanel panel()
    { return new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {}); }
    private static void display(CaRoutePanel panel, Fixture fixture)
    { panel.display(fixture.plan, List.of("Synthetic preview; no live account data."), fixture.preferences,
        new AccountView(GearView.unknown(), fixture.profile, Map.of(), true)); }
    private static void render(CaRoutePanel panel, String title, int width, String state) throws Exception
    {
        JTabbedPane tabs = tabs(panel); int tabIndex = tabs.indexOfTab(title); assertTrue("Missing tab " + title, tabIndex >= 0); tabs.setSelectedIndex(tabIndex);
        for (int pass = 0; pass < 4; pass++) layout(panel);
        JScrollPane scroll = (JScrollPane) tabs.getComponentAt(tabIndex);
        Rectangle lastTab = tabs.getBoundsAt(4);
        assertTrue("All five tab labels remain visible at " + width + "px: " + lastTab + ", navigation width " + tabs.getWidth()
            + ", font " + tabs.getFont(),
            lastTab.x >= 0 && lastTab.x + lastTab.width <= tabs.getWidth());
        assertEquals("The page follows the viewport at " + width + "px", scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
        BufferedImage image = new BufferedImage(width, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        Path path = Paths.get("build", "ui-compact-" + state + "-" + width + ".png");
        Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component component : parent.getComponents()) if (component instanceof Container) layout((Container) component); }
    private static void assertTitlePainted(JButton heading, int sidebarWidth)
    {
        BufferedImage title = new BufferedImage(heading.getWidth(), heading.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = title.createGraphics(); heading.printAll(graphics); graphics.dispose();
        int whiteTitlePixels = 0;
        int right = title.getWidth() - 26 - (findType(heading, WikiLinkButton.class) == null ? 0 : 24);
        for (int x = 4; x < right; x++) for (int y = 0; y < title.getHeight(); y++)
            if ((title.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) whiteTitlePixels++;
        assertTrue("The expanded heading paints its title, beyond only a chevron, at " + sidebarWidth + "px", whiteTitlePixels > 20);
    }
    private static void assertTitleFits(JButton heading, int sidebarWidth)
    {
        FontMetrics metrics = heading.getFontMetrics(heading.getFont());
        int available = heading.getWidth() - 32 - (findType(heading, WikiLinkButton.class) == null ? 0 : 24);
        assertTrue("Title needs readable horizontal space at " + sidebarWidth + "px", available >= 100);
        assertTrue("One-line titles retain usable native font height", heading.getHeight() >= metrics.getHeight() + 5);
        assertTrue("Long titles do not turn list rows into tall cards", heading.getHeight() <= 28);
        assertEquals("Accessibility keeps the untruncated title", heading.getText(), heading.getAccessibleContext().getAccessibleName());
        assertTrue("The full title remains available in the tooltip", heading.getToolTipText().contains(heading.getText()));
    }
    private static JTabbedPane tabs(Component root)
    { JTabbedPane found = findType(root, JTabbedPane.class); assertNotNull("Missing tab navigation", found); return found; }
    private static Container tab(CaRoutePanel panel, String title)
    { JTabbedPane tabs = tabs(panel); int index = tabs.indexOfTab(title); assertTrue("Missing tab " + title, index >= 0); return (Container) ((JScrollPane) tabs.getComponentAt(index)).getViewport().getView(); }
    private static String visibleText(Component root)
    {
        if (!root.isVisible()) return "";
        StringBuilder text = new StringBuilder();
        if (root instanceof JTextComponent) text.append(((JTextComponent) root).getText()).append('\n');
        if (root instanceof JLabel) text.append(((JLabel) root).getText()).append('\n');
        if (root instanceof AbstractButton) text.append(((AbstractButton) root).getText()).append('\n');
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) text.append(visibleText(child));
        return text.toString();
    }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { Component found = named(root, name); assertNotNull("Missing control " + name, found); assertTrue(type.isInstance(found)); return type.cast(found); }
    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { Component found = named(child, name); if (found != null) return found; }
        return null;
    }
    private static <T extends Component> T findType(Component root, Class<T> type)
    {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T found = findType(child, type); if (found != null) return found; }
        return null;
    }
    private static final class Fixture
    {
        final RoutePlan plan; final BossSession session; final PlayerProfile profile; final RoutingPreferences preferences;
        Fixture(RoutePlan plan, BossSession session, PlayerProfile profile, RoutingPreferences preferences)
        { this.plan = plan; this.session = session; this.profile = profile; this.preferences = preferences; }
    }
}

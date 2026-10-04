package com.caroute.ui;

import com.caroute.domain.*;
import com.caroute.engine.PreparationAdvisor;
import com.caroute.engine.RouteEngine;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompactSecondaryUiTest extends RuneLiteUiTestSupport
{
    @Test public void profileOnlyShowsRelevantScopeAndOptionalSectionsStayOpenAcrossRefreshes() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<RouteObjective> writes = new ArrayList<>(); DisclosureState state = new DisclosureState();
            ProfileActions actions = new ProfileActions() { @Override public void objective(RouteObjective value) { writes.add(value); } };
            RoutingPreferences prefs = RoutingPreferences.builder().build();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(prefs, AccountView.of(GearView.unknown()), List.of("Zulrah"), actions, state);
            assertTrue(control(panel, "profile.objective", JComboBox.class).isVisible());
            assertFalse(control(panel, "profile.budget", JSpinner.class).getParent().isVisible());
            assertFalse(control(panel, "profile.activity", JComboBox.class).getParent().isVisible());
            assertFalse(control(panel, "details.profile.current", JPanel.class).isVisible());
            assertFalse(control(panel, "details.profile.kc", JPanel.class).isVisible()); assertTrue(writes.isEmpty());

            control(panel, "collapse.profile.kc", JButton.class).doClick(0);
            assertTrue(control(panel, "details.profile.kc", JPanel.class).isVisible()); assertTrue(writes.isEmpty());
            ProfilePlanningPanel refreshed = new ProfilePlanningPanel(prefs, AccountView.of(GearView.unknown()), List.of("Zulrah"), actions, state);
            assertTrue(control(refreshed, "details.profile.kc", JPanel.class).isVisible());
            assertFalse(control(refreshed, "details.profile.current", JPanel.class).isVisible()); assertTrue(writes.isEmpty());

            JComboBox<?> mode = control(panel, "profile.objective", JComboBox.class);
            mode.setSelectedItem(RouteObjective.MAXIMUM_POINTS_PER_HOUR);
            assertTrue(control(panel, "profile.budget", JSpinner.class).getParent().isVisible());
            assertFalse(control(panel, "profile.activity", JComboBox.class).getParent().isVisible());
            mode.setSelectedItem(RouteObjective.BOSS_COMPLETION);
            assertFalse(control(panel, "profile.budget", JSpinner.class).getParent().isVisible());
            assertTrue(control(panel, "profile.activity", JComboBox.class).getParent().isVisible());
            assertEquals(List.of(RouteObjective.MAXIMUM_POINTS_PER_HOUR, RouteObjective.BOSS_COMPLETION), writes);
        });
    }

    @Test public void preparationKeepsAdviceAndRelatedTasksHiddenUntilExplicitlyExpanded() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            PreparationStep step = skill(PreparationStep.Purpose.HARD_UNLOCK, 50); DisclosureState state = new DisclosureState();
            String key = "prep.step.HARD_UNLOCK.SKILL.Attack";
            PreparationPanel panel = new PreparationPanel(List.of(step), value -> value, state);
            JPanel details = control(panel, "details." + key, JPanel.class), related = control(panel, "details." + key + ".tasks", JPanel.class);
            assertFalse(details.isVisible()); assertFalse(related.isVisible());
            control(panel, "collapse." + key, JButton.class).doClick(0);
            assertTrue(details.isVisible()); assertFalse(related.isVisible());
            JProgressBar progress = control(panel, "prepare.progress.HARD_UNLOCK.Attack", JProgressBar.class);
            assertEquals(50, progress.getValue()); assertEquals(70, progress.getMaximum()); assertEquals("50 / 70", progress.getString());
            control(panel, "collapse." + key + ".tasks", JButton.class).doClick(0); assertTrue(related.isVisible());
            assertNotNull(control(panel, "prepare.task.42", JPanel.class));
            PreparationPanel refreshed = new PreparationPanel(List.of(step), value -> value, state);
            assertTrue(control(refreshed, "details." + key, JPanel.class).isVisible());
            assertTrue(control(refreshed, "details." + key + ".tasks", JPanel.class).isVisible());
            PreparationPanel freshAccount = new PreparationPanel(List.of(step), value -> value, new DisclosureState());
            assertFalse(control(freshAccount, "details." + key, JPanel.class).isVisible());
        });
    }

    @Test public void unknownSkillRetainsVerificationAdviceWithoutInventingZeroProgress() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            PreparationPanel panel = new PreparationPanel(List.of(skill(PreparationStep.Purpose.VERIFY_REQUIREMENT, null)), value -> value);
            assertNull(findType(panel, JProgressBar.class));
            String key = "prep.step.VERIFY_REQUIREMENT.SKILL.Attack";
            assertFalse(control(panel, "details." + key, JPanel.class).isVisible());
            control(panel, "collapse." + key, JButton.class).doClick(0);
            assertTrue(control(panel, "details." + key, JPanel.class).isVisible());
            assertTrue(text(panel).contains("current level unknown"));
            assertTrue(text(panel).contains("other requirements may apply"));
        });
    }

    @Test public void openedSecondaryPanelsKeepTheirControlsUsableAtTheNarrowSidebarWidth() throws Exception
    {
        String activity = "Chambers of Xeric: Challenge Mode";
        CombatAchievement task = CombatAchievement.builder(42, "A task with a long name to verify that its details wrap in the sidebar", activity, AchievementTier.EASY)
            .requirement(Requirement.skill("Attack", 70)).recommendedGear(Set.of("ratbane-weapon"))
            .requirementsKnown(true).timingKnown(true).build();
        PlayerProfile account = PlayerProfile.builder().accountKey("secondary-preview").version(1).loaded(true).catalogueComplete(true)
            .thresholds(Map.of(AchievementTier.EASY, 10)).skills(Map.of("Attack", 50)).killCounts(Map.of(activity, 123456))
            .gear(GearProfile.unknown().withActivePreparationEstimates(Map.of("ratbane-weapon", 12.5))).build();
        RoutingPreferences prefs = RoutingPreferences.builder().target(AchievementTier.EASY).objective(RouteObjective.BOSS_COMPLETION)
            .selectedActivity(activity).currentActivity(activity).build();
        RoutePlan raw = new RouteEngine().plan(List.of(task), account, prefs);
        RoutePlan plan = raw.withPreparationSteps(new PreparationAdvisor().advise(raw.assessments(), account));
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                for (int width : new int[] {235, 280})
                {
                    CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {});
                    panel.display(plan, List.of(), prefs, new AccountView(new GearView(account.gear(), Map.of(), false, false, 0, 0),
                        account, Map.of(activity, 123456), false));
                    panel.setSize(width, 900);
                    control(panel, "collapse.profile.current", JButton.class).doClick(0);
                    control(panel, "collapse.profile.kc", JButton.class).doClick(0);
                    JScrollPane profileScroll = render(panel, 3, width, "profile-open");
                    for (String name : List.of("profile.objective", "profile.activity", "profile.currentActivity", "profile.kc.activity", "profile.kc.count", "profile.kc.save", "profile.kc.auto"))
                        assertControlFits(profileScroll, control(panel, name, JComponent.class));
                    assertTrue(control(panel, "details.profile.current", JPanel.class).getHeight() > 40);
                    assertTrue(control(panel, "details.profile.kc", JPanel.class).getHeight() > 80);

                    control(panel, "profile.objective", JComboBox.class).setSelectedItem(RouteObjective.MAXIMUM_POINTS_PER_HOUR);
                    render(panel, 3, width, "profile-budget-open");
                    assertControlFits(profileScroll, control(panel, "profile.budget", JSpinner.class));

                    control(panel, "collapse.profile.gear", JButton.class).doClick(0);
                    control(panel, "collapse.gear.item.ratbane-weapon", JButton.class).doClick(0);
                    render(panel, 3, width, "profile-gear-open");
                    JComponent item = control(panel, "details.gear.item.ratbane-weapon", JComponent.class);
                    assertTrue("The nested gear body must allocate its controls", item.getHeight() > 80);
                    for (String name : List.of("gear.ratbane-weapon", "gear.estimate.ratbane-weapon", "gear.estimate.set.ratbane-weapon", "gear.estimate.auto.ratbane-weapon"))
                        assertControlFits(profileScroll, control(item, name, JComponent.class));
                    Rectangle itemBounds = SwingUtilities.convertRectangle(item.getParent(), item.getBounds(), profileScroll.getViewport().getView());
                    profileScroll.getViewport().setViewPosition(new Point(0, Math.max(0, itemBounds.y - 40)));
                    render(panel, 3, width, "profile-gear-focus");

                    String key = "prep.step.HARD_UNLOCK.SKILL.Attack";
                    control(panel, "collapse." + key, JButton.class).doClick(0);
                    control(panel, "collapse." + key + ".tasks", JButton.class).doClick(0);
                    JScrollPane prepScroll = render(panel, 4, width, "prepare-open");
                    JPanel details = control(panel, "details." + key, JPanel.class), related = control(panel, "details." + key + ".tasks", JPanel.class);
                    assertTrue("Expanded preparation advice must be visible", details.getHeight() > 120);
                    assertTrue("Expanded task names must have space", related.getHeight() > 45);
                    assertControlFits(prepScroll, control(panel, "prepare.progress.HARD_UNLOCK.Attack", JProgressBar.class));
                    assertControlFits(prepScroll, control(panel, "collapse." + key + ".tasks", JButton.class));
                    assertControlFits(prepScroll, control(panel, "prepare.task.42", JPanel.class));
                }
            }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static JScrollPane render(CaRoutePanel panel, int tabIndex, int width, String state) throws Exception
    {
        JTabbedPane tabs = findType(panel, JTabbedPane.class); assertNotNull(tabs); tabs.setSelectedIndex(tabIndex);
        for (int pass = 0; pass < 5; pass++) layout(panel);
        JScrollPane scroll = (JScrollPane) tabs.getComponentAt(tabIndex);
        assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
        BufferedImage image = new BufferedImage(width, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        Path target = Path.of("build", "ui-secondary-" + state + "-" + width + ".png");
        Files.createDirectories(target.getParent()); ImageIO.write(image, "png", target.toFile()); return scroll;
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void assertControlFits(JScrollPane scroll, JComponent control)
    {
        assertTrue("The control must have a usable width: " + control.getName(), control.getWidth() > 10);
        assertTrue("The control must have a usable height: " + control.getName(), control.getHeight() > 10);
        assertTrue("The control must fit the height allocated by its parent: " + control.getName() + " " + control.getBounds()
            + ", parent " + control.getParent().getSize(),
            control.getY() >= 0 && control.getY() + control.getHeight() <= control.getParent().getHeight());
        Component view = scroll.getViewport().getView();
        Rectangle bounds = SwingUtilities.convertRectangle(control.getParent(), control.getBounds(), view);
        assertTrue("The control must fit the narrow page horizontally: " + control.getName() + " " + bounds,
            bounds.x >= 0 && bounds.x + bounds.width <= view.getWidth());
    }

    private static PreparationStep skill(PreparationStep.Purpose purpose, Integer current)
    { return new PreparationStep(PreparationStep.Kind.SKILL, purpose, "Attack", 70, current, null, null,
        Map.of(42, "A task requiring Attack"), Map.of(42, 3), Set.of("Boss"), List.of("Other prerequisites can apply.")); }
    private static String text(Component root)
    {
        StringBuilder value = new StringBuilder();
        if (root instanceof JTextArea) value.append(((JTextArea) root).getText());
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) value.append(text(child));
        return value.toString();
    }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { T value = find(root, name, type); assertNotNull("Missing " + name, value); return value; }
    private static <T extends Component> T find(Component root, String name, Class<T> type)
    {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T value = find(child, name, type); if (value != null) return value; }
        return null;
    }
    private static <T extends Component> T findType(Component root, Class<T> type)
    {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T value = findType(child, type); if (value != null) return value; }
        return null;
    }
}

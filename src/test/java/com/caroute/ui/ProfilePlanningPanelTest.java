package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfilePlanningPanelTest extends RuneLiteUiTestSupport
{
    @Test public void constructingLayingOutAndPaintingProfileNeverSendsAccountCommands() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> commands = new ArrayList<>();
            RoutingPreferences preferences = RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION)
                .timeBudgetMinutes(85).selectedActivity("None").currentActivity("Zulrah").build();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(preferences, account(), List.of("None", "Zulrah"), recorder(commands));
            panel.setSize(280, 1400); layout(panel);
            BufferedImage image = new BufferedImage(280, 1400, BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics();
            panel.printAll(graphics); graphics.dispose();
            assertTrue("Rendering persisted settings must not write them back", commands.isEmpty());
            assertEquals(RouteObjective.BOSS_COMPLETION, control(panel, "profile.objective", JComboBox.class).getSelectedItem());
            assertEquals(85, ((Number) control(panel, "profile.budget", JSpinner.class).getValue()).intValue());
            assertFalse(control(panel, "profile.budget", JSpinner.class).isEnabled());
            assertTrue(control(panel, "profile.activity", JComboBox.class).isEnabled());
            assertEquals("None", control(panel, "profile.activity", JComboBox.class).getSelectedItem());
            assertTrue(control(panel, "profile.activity", JComboBox.class).getSelectedIndex() > 0);
            assertEquals("Zulrah", control(panel, "profile.currentActivity", JComboBox.class).getSelectedItem());
        });
    }

    @Test public void objectiveAndBudgetControlsDispatchExactUserSelections() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> commands = new ArrayList<>();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(RoutingPreferences.builder().objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR)
                .timeBudgetMinutes(90).build(), account(), List.of("None", "Zulrah"), recorder(commands));
            assertTrue(control(panel, "profile.budget", JSpinner.class).isEnabled());
            assertFalse(control(panel, "profile.activity", JComboBox.class).isEnabled());
            control(panel, "profile.budget", JSpinner.class).setValue(135);
            control(panel, "profile.objective", JComboBox.class).setSelectedItem(RouteObjective.IRONMAN_EFFICIENT);
            assertEquals(List.of("budget:135", "objective:IRONMAN_EFFICIENT"), commands);
        });
    }

    @Test public void canonicalNoneActivityRemainsDistinctFromNoSelectedOrCurrentActivity() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> commands = new ArrayList<>();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(RoutingPreferences.builder().objective(RouteObjective.BOSS_COMPLETION).build(),
                account(), List.of("None", "Zulrah"), recorder(commands));
            JComboBox<?> selected = control(panel, "profile.activity", JComboBox.class), current = control(panel, "profile.currentActivity", JComboBox.class);
            assertEquals(0, selected.getSelectedIndex()); assertEquals(0, current.getSelectedIndex());
            assertNotEquals("None", selected.getItemAt(0)); assertNotEquals("None", current.getItemAt(0));
            selected.setSelectedItem("None"); assertTrue(selected.getSelectedIndex() > 0);
            selected.setSelectedIndex(0);
            current.setSelectedItem("None"); assertTrue(current.getSelectedIndex() > 0);
            current.setSelectedItem("Zulrah"); current.setSelectedIndex(0);
            assertEquals(List.of("selected:None", "selected:", "current:None", "current:Zulrah", "current:"), commands);
        });
    }

    @Test public void changingKcActivityOnlyReadsAndSaveAutoDispatchExplicitValues() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> commands = new ArrayList<>();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(RoutingPreferences.builder().build(), account(), List.of("None", "Zulrah"), recorder(commands));
            JComboBox<?> activity = control(panel, "profile.kc.activity", JComboBox.class);
            JSpinner count = control(panel, "profile.kc.count", JSpinner.class);
            assertEquals(3, ((Number) count.getValue()).intValue());
            activity.setSelectedItem("Zulrah"); assertEquals(99, ((Number) count.getValue()).intValue()); assertTrue(commands.isEmpty());
            count.setValue(987); assertTrue("Editing KC alone must not persist before Set KC", commands.isEmpty());
            control(panel, "profile.kc.save", JButton.class).doClick(0);
            control(panel, "profile.kc.auto", JButton.class).doClick(0);
            activity.setSelectedItem("None"); assertEquals(3, ((Number) count.getValue()).intValue());
            control(panel, "profile.kc.auto", JButton.class).doClick(0);
            assertEquals(List.of("kc:Zulrah:987", "kc:Zulrah:auto", "kc:None:auto"), commands);
        });
    }

    @Test public void invalidKcTextDoesNotDispatchAndUnknownProfileRendersWithoutWrites() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> commands = new ArrayList<>();
            ProfilePlanningPanel panel = new ProfilePlanningPanel(RoutingPreferences.builder().build(), AccountView.of(GearView.unknown()),
                List.of("Zulrah"), recorder(commands));
            assertTrue(commands.isEmpty()); JSpinner count = control(panel, "profile.kc.count", JSpinner.class);
            assertEquals(0, ((Number) count.getValue()).intValue());
            ((JSpinner.NumberEditor) count.getEditor()).getTextField().setText("not a count");
            control(panel, "profile.kc.save", JButton.class).doClick(0); assertTrue(commands.isEmpty());
        });
    }

    private static AccountView account()
    { return new AccountView(GearView.unknown(), PlayerProfile.builder().loaded(true).killCounts(Map.of("None", 3, "Zulrah", 99)).build(), Map.of("Zulrah", 99), true); }
    private static ProfileActions recorder(List<String> commands)
    {
        return new ProfileActions() {
            @Override public void objective(RouteObjective value) { commands.add("objective:" + value.name()); }
            @Override public void selectedActivity(String value) { commands.add("selected:" + value); }
            @Override public void currentActivity(String value) { commands.add("current:" + value); }
            @Override public void timeBudget(int value) { commands.add("budget:" + value); }
            @Override public void killCount(String activity, Integer value) { commands.add("kc:" + activity + ":" + (value == null ? "auto" : value)); }
        };
    }
    private static void layout(Container container)
    { container.doLayout(); for (Component component : container.getComponents()) if (component instanceof Container) layout((Container) component); }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { T found = find(root, name, type); assertNotNull("Missing named control " + name, found); return found; }
    private static <T extends Component> T find(Component root, String name, Class<T> type)
    {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T found = find(child, name, type); if (found != null) return found; }
        return null;
    }
}

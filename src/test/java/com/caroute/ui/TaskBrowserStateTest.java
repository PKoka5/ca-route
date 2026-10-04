package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Browser choices survive new account facts, but never cross account or logout boundaries. */
public class TaskBrowserStateTest extends RuneLiteUiTestSupport
{
    @Test public void inventoryRefreshRetainsSearchEveryFilterOrderingAndPage() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, "first", 1, 121, "Mechanical", 100);
            selectFilters(panel);
            button(panel, "tasks.next").doClick(0);
            assertEquals("51\u2013100 / 121", count(panel));
            assertNotNull(named(panel, "collapse.tasks.task.50"));

            // This represents the different inventory snapshot that triggers a new route display.
            display(panel, "first", 2, 121, "Mechanical", 200);
            assertEquals("  Task  ", field(panel).getText());
            assertEquals("Remaining tasks", choice(panel, "tasks.status").getSelectedItem());
            assertEquals("Hard", choice(panel, "tasks.tier").getSelectedItem());
            assertEquals("Mechanical", choice(panel, "tasks.type").getSelectedItem());
            assertEquals("Name", choice(panel, "tasks.sort").getSelectedItem());
            assertEquals("51\u2013100 / 121", count(panel));
            assertNotNull(named(panel, "collapse.tasks.task.50"));
            assertNull("Restoring controls must not reset pagination through their listeners", named(panel, "collapse.tasks.task.0"));
        });
    }

    @Test public void restoredPageClampsToChangedResultsAndUnavailableTypeFallsBackToAll() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, "first", 1, 121, "Mechanical", 100);
            selectFilters(panel); button(panel, "tasks.next").doClick(0); button(panel, "tasks.next").doClick(0);
            assertEquals("101\u2013121 / 121", count(panel));
            display(panel, "first", 2, 55, "Mechanical", 200);
            assertEquals("51\u201355 / 55", count(panel));
            assertNotNull(named(panel, "collapse.tasks.task.50")); assertFalse(button(panel, "tasks.next").isEnabled());
            assertTrue(button(panel, "tasks.previous").isEnabled());

            display(panel, "first", 3, 121, "Speed", 300);
            assertEquals("Any task type", choice(panel, "tasks.type").getSelectedItem());
            assertEquals("Hard", choice(panel, "tasks.tier").getSelectedItem());
            assertEquals("51\u2013100 / 121", count(panel));
            assertNotNull(named(panel, "collapse.tasks.task.50"));
        });
    }

    @Test public void accountChangeAndLogoutRestoreDefaultBrowserChoices() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            CaRoutePanel panel = panel(); display(panel, "first", 1, 121, "Mechanical", 100);
            assertDefaultChoices(panel);
            selectFilters(panel); button(panel, "tasks.next").doClick(0);
            display(panel, "second", 2, 121, "Mechanical", 200); assertDefaultChoices(panel);
            selectFilters(panel); button(panel, "tasks.next").doClick(0);
            panel.showMessage("Log in to calculate your CA route.");
            display(panel, "second", 3, 121, "Mechanical", 300); assertDefaultChoices(panel);
        });
    }

    private static void selectFilters(CaRoutePanel panel)
    {
        field(panel).setText("  Task  ");
        choice(panel, "tasks.status").setSelectedItem("Remaining tasks");
        choice(panel, "tasks.tier").setSelectedItem("Hard");
        choice(panel, "tasks.type").setSelectedItem("Mechanical");
        choice(panel, "tasks.sort").setSelectedItem("Name");
    }
    private static void assertDefaultChoices(CaRoutePanel panel)
    {
        assertEquals("", field(panel).getText());
        assertEquals("Recommended now", choice(panel, "tasks.status").getSelectedItem());
        assertEquals("Any tier", choice(panel, "tasks.tier").getSelectedItem());
        assertEquals("Any task type", choice(panel, "tasks.type").getSelectedItem());
        assertEquals("Activity", choice(panel, "tasks.sort").getSelectedItem());
        assertEquals("1\u201350 / 121", count(panel)); assertNotNull(named(panel, "collapse.tasks.task.0"));
    }
    private static CaRoutePanel panel()
    { return new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {}); }
    private static void display(CaRoutePanel panel, String accountKey, long version, int taskCount, String type, int heldItem)
    {
        List<TaskAssessment> assessments = new ArrayList<>();
        for (int id = 0; id < taskCount; id++)
        {
            CombatAchievement task = CombatAchievement.builder(id, String.format(Locale.ROOT, "Task %03d", id), "Boss", AchievementTier.HARD)
                .type(type).requirementsKnown(true).timingKnown(true).build();
            assessments.add(new TaskAssessment(task, new Eligibility(EligibilityState.AVAILABLE, List.of(), Confidence.HIGH, true, false),
                1, 1, Map.of(), List.of(), Confidence.HIGH));
        }
        GearProfile gear = GearProfile.of(Set.of(), Set.of(heldItem), Set.of(), false);
        PlayerProfile profile = PlayerProfile.builder().accountKey(accountKey).version(version).loaded(true).catalogueComplete(true)
            .thresholds(Map.of(AchievementTier.HARD, 100)).gear(gear).build();
        RoutePlan plan = new RoutePlan(AchievementTier.HARD, 0, 100, 100, 0, 0, List.of(), assessments, List.of(),
            Reachability.UNREACHABLE_NOW, Confidence.HIGH, true, version);
        panel.display(plan, List.of(), RoutingPreferences.builder().target(AchievementTier.HARD).build(),
            new AccountView(new GearView(gear, Map.of(), false, false, 0, 0), profile, Map.of(), false));
    }
    private static JTextField field(Container parent) { return (JTextField) named(parent, "tasks.search"); }
    private static JComboBox<?> choice(Container parent, String name) { return (JComboBox<?>) named(parent, name); }
    private static JButton button(Container parent, String name) { return (JButton) named(parent, name); }
    private static String count(Container parent) { return ((JLabel) named(parent, "tasks.count")).getText(); }
    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { Component found = named(child, name); if (found != null) return found; }
        return null;
    }
}

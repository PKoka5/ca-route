package com.caroute.ui;

import com.caroute.CaRouteConfig;
import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaleControlsTest extends RuneLiteUiTestSupport
{
    @Test public void detachedAccountControlsAndAnOpenTaskMenuCannotChangeTheNextAccount() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> writes = new ArrayList<>(); CaRoutePanel panel = panel(writes); display(panel, "123");
            List<Runnable> old = mutations(panel); assertTrue(writes.isEmpty());
            display(panel, "456"); old.forEach(Runnable::run);
            assertTrue("Every removed control must retain its original account render", writes.isEmpty());
            mutations(panel).forEach(Runnable::run);
            assertTrue(writes.containsAll(List.of("task", "skip", "restore", "target", "preference", "gear", "bank", "gear-reset",
                "mode", "budget", "selected", "current", "kc", "prep", "export")));
        });
    }

    @Test public void logoutInvalidatesButtonsAndLazyTaskRenderingRetainsItsOriginalGeneration() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> writes = new ArrayList<>(); CaRoutePanel panel = panel(writes); display(panel, "123");
            List<Runnable> old = mutations(panel);
            TaskBrowserPanel browser = findType(panel, TaskBrowserPanel.class); assertNotNull(browser);
            panel.showMessage("Log in"); old.forEach(Runnable::run); assertTrue(writes.isEmpty());
            display(panel, "456");
            ((JTextField) named(browser, "tasks.search")).setText("Fixture");
            JButton regenerated = (JButton) named(browser, "task.actions.0"); assertNotNull(regenerated);
            ((JMenuItem) regenerated.getComponentPopupMenu().getComponent(0)).doClick(0);
            assertTrue("A detached task browser must not bind its regenerated menu to the new account", writes.isEmpty());
        });
    }

    @Test public void manualPreparationOnlyDispatchesValidatedExplicitEntries() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> writes = new ArrayList<>(); List<Double> estimates = new ArrayList<>();
            CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, value) -> {},
                (id, owned) -> {}, enabled -> {}, () -> {}, new ProfileActions() {
                    @Override public void preparationEstimate(String id, Double value) { estimates.add(value); }
                });
            display(panel, "123");
            JTextField entry = (JTextField) named(panel, "gear.estimate.ratbane-weapon");
            JButton save = (JButton) named(panel, "gear.estimate.set.ratbane-weapon");
            for (String invalid : List.of("-1", "NaN", "Infinity", "100001", "not a time")) { entry.setText(invalid); save.doClick(0); }
            assertTrue(estimates.isEmpty());
            entry.setText("12,5"); save.doClick(0); entry.setText("0"); save.doClick(0);
            ((JButton) named(panel, "gear.estimate.auto.ratbane-weapon")).doClick(0);
            assertEquals(Arrays.asList(12.5, 0.0, null), estimates);
        });
    }

    private static CaRoutePanel panel(List<String> writes)
    {
        return new CaRoutePanel((id, action) -> writes.add("task"), () -> writes.add("restore"), ids -> writes.add("skip"),
            target -> writes.add("target"), (flag, value) -> writes.add("preference"), (id, owned) -> writes.add("gear"),
            enabled -> writes.add("bank"), () -> writes.add("gear-reset"), new ProfileActions() {
                @Override public void objective(RouteObjective value) { writes.add("mode"); }
                @Override public void timeBudget(int value) { writes.add("budget"); }
                @Override public void selectedActivity(String value) { writes.add("selected"); }
                @Override public void currentActivity(String value) { writes.add("current"); }
                @Override public void killCount(String activity, Integer value) { writes.add("kc"); }
                @Override public void preparationEstimate(String id, Double value) { writes.add("prep"); }
                @Override public void exportRoute() { writes.add("export"); }
            });
    }
    private static void display(CaRoutePanel panel, String key)
    {
        CombatAchievement task = CombatAchievement.builder(0, "Fixture", "Boss", AchievementTier.EASY)
            .requirementsKnown(true).timingKnown(true).recommendedGear(Set.of("ratbane-weapon")).build();
        PlayerProfile account = PlayerProfile.builder().accountKey(key).loaded(true).catalogueComplete(true)
            .thresholds(Map.of(AchievementTier.EASY, 1)).build();
        RoutingPreferences prefs = RoutingPreferences.builder().target(AchievementTier.EASY)
            .objective(RouteObjective.BOSS_COMPLETION).selectedActivity("Boss").build();
        panel.display(new RouteEngine().plan(List.of(task), account, prefs), List.of(), prefs,
            new AccountView(GearView.unknown(), account, Map.of(), true));
    }
    private static List<Runnable> mutations(CaRoutePanel panel)
    {
        List<Runnable> actions = new ArrayList<>();
        JComboBox<?> mode = (JComboBox<?>) named(panel, "profile.objective"), target = (JComboBox<?>) named(panel, "profile.target"),
            preference = (JComboBox<?>) named(panel, "profile.preference.speed"), gear = (JComboBox<?>) named(panel, "gear.ratbane-weapon"),
            selected = (JComboBox<?>) named(panel, "profile.activity"), current = (JComboBox<?>) named(panel, "profile.currentActivity");
        JSpinner budget = (JSpinner) named(panel, "profile.budget"), count = (JSpinner) named(panel, "profile.kc.count");
        JTextField prep = (JTextField) named(panel, "gear.estimate.ratbane-weapon");
        actions.add(() -> mode.setSelectedItem(RouteObjective.EASIEST_PROGRESSION));
        actions.add(() -> target.setSelectedItem(CaRouteConfig.Target.HARD));
        actions.add(() -> preference.setSelectedItem(Preference.AVOID)); actions.add(() -> gear.setSelectedIndex(1));
        actions.add(() -> selected.setSelectedIndex(0)); actions.add(() -> current.setSelectedItem("Boss"));
        actions.add(() -> budget.setValue(90)); actions.add(() -> count.setValue(123)); actions.add(() -> prep.setText("12.5"));
        for (String name : List.of("profile.kc.save", "profile.kc.auto", "gear.estimate.set.ratbane-weapon", "gear.estimate.auto.ratbane-weapon",
            "gear.bank", "gear.reset", "profile.restore", "route.skip", "route.export"))
        {
            AbstractButton control = (AbstractButton) named(panel, name); assertNotNull(name, control); actions.add(() -> control.doClick(0));
        }
        JButton task = (JButton) named(panel, "task.actions.0");
        JMenuItem pin = (JMenuItem) task.getComponentPopupMenu().getComponent(0); actions.add(() -> pin.doClick(0));
        return actions;
    }
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
}

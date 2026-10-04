package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static com.caroute.ui.PanelStyles.*;

final class ProfilePlanningPanel extends JPanel
{
    ProfilePlanningPanel(RoutingPreferences preferences, AccountView account, List<String> activities, ProfileActions actions)
    { this(preferences, account, activities, actions, new DisclosureState()); }

    ProfilePlanningPanel(RoutingPreferences preferences, AccountView account, List<String> activities,
        ProfileActions actions, DisclosureState disclosure)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel scope = card(); heading(scope, "Route mode");
        JComboBox<RouteObjective> mode = new JComboBox<>(RouteObjective.values()); mode.setName("profile.objective"); compact(mode);
        mode.setSelectedItem(preferences.objective()); mode.setToolTipText(preferences.objective().description()); scope.add(mode);

        JPanel budgetRow = page(); budgetRow.setOpaque(false); budgetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        text(budgetRow, "Time budget (minutes)", MUTED, 11, false);
        JSpinner budget = new JSpinner(new SpinnerNumberModel(preferences.timeBudgetMinutes(), 1, 1440, 5)); budget.setName("profile.budget"); compact(budget);
        budget.setToolTipText("Estimated active time. Unknown waits and preparation can add more.");
        budget.addChangeListener(event -> actions.timeBudget(((Number) budget.getValue()).intValue())); budgetRow.add(budget); scope.add(budgetRow);

        JPanel activityRow = page(); activityRow.setOpaque(false); activityRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        text(activityRow, "Boss to complete", MUTED, 11, false);
        JComboBox<String> selected = activityPicker(activities, preferences.selectedActivity()); selected.setName("profile.activity");
        selected.setToolTipText("Plan the remaining combat achievements for this activity.");
        selected.addActionListener(event -> actions.selectedActivity(value(selected))); activityRow.add(selected); scope.add(activityRow);

        Runnable updateScope = () -> {
            RouteObjective objective = (RouteObjective) mode.getSelectedItem();
            boolean timed = objective == RouteObjective.MAXIMUM_POINTS_PER_HOUR, boss = objective == RouteObjective.BOSS_COMPLETION;
            budget.setEnabled(timed); budgetRow.setVisible(timed); selected.setEnabled(boss); activityRow.setVisible(boss);
            mode.setToolTipText(objective == null ? null : objective.description()); scope.revalidate(); scope.repaint();
        };
        updateScope.run();
        mode.addActionListener(event -> { updateScope.run(); actions.objective((RouteObjective) mode.getSelectedItem()); });
        put(this, scope);

        CollapsibleSection location = new CollapsibleSection("profile.current", "Current activity",
            preferences.currentActivity().isEmpty() ? "Not set" : preferences.currentActivity(), disclosure);
        location.headerToggle().setToolTipText("Optional, for this login. Removes the selected activity's setup time; clear it after leaving.");
        JPanel locationBody = location.body();
        JComboBox<String> current = activityPicker(activities, preferences.currentActivity()); current.setName("profile.currentActivity");
        current.setToolTipText("Already here? Clear this choice after leaving the activity.");
        current.addActionListener(event -> actions.currentActivity(value(current))); locationBody.add(current);
        text(locationBody, "Already here: skip its setup time.", MUTED, 10, false); put(this, location);

        int knownCounts = account.profile() == null ? 0 : account.profile().killCounts().size();
        CollapsibleSection kc = new CollapsibleSection("profile.kc", "Kill counts", knownCounts + " known", disclosure);
        kc.headerToggle().setToolTipText("Enter historical KC. Auto uses observed game messages; an unobserved count stays unknown.");
        JPanel kcBody = kc.body();
        JComboBox<String> activity = new JComboBox<>(activities.toArray(new String[0])); activity.setName("profile.kc.activity"); compact(activity); kcBody.add(activity);
        JSpinner count = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1)); count.setName("profile.kc.count"); compact(count); kcBody.add(count);
        JLabel known = new JLabel(); known.setForeground(MUTED); compact(known); kcBody.add(known);
        Runnable showCount = () -> {
            known.setForeground(MUTED);
            String name = (String) activity.getSelectedItem();
            Integer currentCount = account.profile() == null ? null : account.profile().killCounts().get(name);
            count.setValue(currentCount == null ? 0 : currentCount);
            known.setText(currentCount == null ? "KC unknown" : (account.killCountOverrides().containsKey(name) ? "Entered KC: " : "Observed KC: ") + currentCount);
        };
        activity.addActionListener(event -> showCount.run()); showCount.run();
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0)); controls.setOpaque(false); controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = new JButton("Set KC"), auto = new JButton("Auto"); save.setName("profile.kc.save"); auto.setName("profile.kc.auto"); compact(save); compact(auto);
        save.setEnabled(!activities.isEmpty()); auto.setEnabled(!activities.isEmpty());
        save.setToolTipText("Use this kill count for planning."); auto.setToolTipText("Restore local game-message observations.");
        save.addActionListener(event -> {
            try { count.commitEdit(); if (activity.getSelectedItem() != null) actions.killCount((String) activity.getSelectedItem(), ((Number) count.getValue()).intValue()); }
            catch (java.text.ParseException invalid) { known.setForeground(RouteWidgets.RED); known.setText("Enter a non-negative whole number"); }
        });
        auto.addActionListener(event -> { if (activity.getSelectedItem() != null) actions.killCount((String) activity.getSelectedItem(), null); });
        controls.add(save); controls.add(auto); kcBody.add(controls); put(this, kc);
    }
    private static JComboBox<String> activityPicker(List<String> activities, String selected)
    {
        List<String> choices = new ArrayList<>(); choices.add("(No activity)"); choices.addAll(activities);
        if (!selected.isEmpty() && !choices.contains(selected)) choices.add(selected);
        JComboBox<String> picker = new JComboBox<>(choices.toArray(new String[0])); compact(picker);
        if (selected.isEmpty()) picker.setSelectedIndex(0); else picker.setSelectedItem(selected); return picker;
    }
    private static String value(JComboBox<String> picker)
    { return picker.getSelectedIndex() == 0 ? "" : (String) picker.getSelectedItem(); }
}

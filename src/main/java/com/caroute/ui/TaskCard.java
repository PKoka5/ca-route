package com.caroute.ui;

import com.caroute.application.TaskAction;
import com.caroute.domain.*;
import java.awt.*;
import java.util.function.*;
import javax.swing.*;
import static com.caroute.ui.PanelStyles.*;

/** One compact title; all task-specific content is disclosed on demand. */
final class TaskCard extends JPanel
{
    TaskCard(String key, TaskAssessment assessment, DisclosureState state, Function<String, String> readable,
        BiConsumer<Integer, TaskAction> action)
    { this(key, assessment, state, readable, action, net.runelite.client.util.LinkBrowser::browse); }
    TaskCard(String key, TaskAssessment assessment, DisclosureState state, Function<String, String> readable,
        BiConsumer<Integer, TaskAction> action, Consumer<String> wikiOpener)
    { this(key, assessment, state, readable, action, wikiOpener, id -> {}); }
    TaskCard(String key, TaskAssessment assessment, DisclosureState state, Function<String, String> readable,
        BiConsumer<Integer, TaskAction> action, Consumer<String> wikiOpener, IntConsumer startTask)
    { this(key, assessment, state, readable, action, wikiOpener, startTask, null); }
    TaskCard(String key, TaskAssessment assessment, DisclosureState state, Function<String, String> readable,
        BiConsumer<Integer, TaskAction> action, Consumer<String> wikiOpener, IntConsumer startTask, PlayerProfile profile)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); setAlignmentX(Component.LEFT_ALIGNMENT);
        CombatAchievement task = assessment.task();
        CollapsibleSection section = new CollapsibleSection(key, task.name(), "", state);
        EligibilityState status = assessment.eligibility().state();
        if (status == EligibilityState.COMPLETED) section.setStatusColor(RouteWidgets.GREEN);
        else if (status == EligibilityState.CONTENT_EXCLUDED || status == EligibilityState.DEFERRED) section.setStatusColor(MUTED);
        else if (!assessment.eligibility().recommendedNow()) section.setStatusColor(RouteWidgets.RED);
        section.headerToggle().setToolTipText(task.name() + " · " + RouteWidgets.status(assessment));
        String wikiUrl = WikiLinks.task(task);
        if (!wikiUrl.isEmpty()) section.setHeaderLink(new WikiLinkButton(key, task.name(), wikiUrl, wikiOpener));
        JPanel details = section.body();
        text(details, task.activity() + " · " + task.tier().displayName() + " · +" + task.points() + " pts", GOLD, 11, false);
        details.add(RouteWidgets.badge(RouteWidgets.status(assessment), RouteWidgets.statusColor(assessment),
            "Open Requirements & gear to check levels, quests and equipment for this task."));
        text(details, task.description(), Color.WHITE, 12, false);
        text(details, "Task estimate: ~" + RouteWidgets.shortTime(assessment.estimatedMinutes()), MUTED, 10, false);
        if (!task.quickGuide().isEmpty())
        {
            CollapsibleSection guide = new CollapsibleSection(key + ".guide", "Quick guide", "", state);
            guide.body().add(new TaskGuidePanel(task.quickGuide()));
            details.add(guide); details.add(Box.createVerticalStrut(6));
        }
        CollapsibleSection notes = new CollapsibleSection(key + ".notes", "Requirements & gear", "", state);
        notes.body().add(new TaskRequirementsPanel(task, profile, readable));
        details.add(notes); details.add(Box.createVerticalStrut(6));
        JButton start = new JButton("Start task"); start.setName("task.start." + task.id()); compact(start);
        start.setEnabled(assessment.eligibility().state() != EligibilityState.COMPLETED);
        start.setToolTipText(start.isEnabled() ? "Show manual guidance for this task. Task conditions still apply." : "This task is already completed.");
        start.addActionListener(event -> startTask.accept(task.id()));
        start.setMaximumSize(new Dimension(Integer.MAX_VALUE, start.getPreferredSize().height)); details.add(start); details.add(Box.createVerticalStrut(4));
        JPanel controls = new JPanel(new GridLayout(1, wikiUrl.isEmpty() ? 1 : 2, 3, 0)); controls.setOpaque(false); controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton actions = new JButton("Actions"); actions.setName("task.actions." + task.id()); compact(actions);
        JPopupMenu menu = new JPopupMenu();
        for (TaskAction choice : TaskAction.values())
        {
            JMenuItem item = new JMenuItem(choice.name().charAt(0) + choice.name().substring(1).toLowerCase(java.util.Locale.ROOT));
            item.setName("task.action." + task.id() + "." + choice.name().toLowerCase(java.util.Locale.ROOT));
            item.addActionListener(event -> action.accept(task.id(), choice)); menu.add(item);
        }
        actions.setComponentPopupMenu(menu); actions.addActionListener(event -> { if (actions.isShowing()) menu.show(actions, 0, actions.getHeight()); });
        controls.add(actions);
        if (!wikiUrl.isEmpty())
        {
            JButton wiki = new JButton("Wiki"); compact(wiki); wiki.setName("task.wiki." + task.id());
            wiki.setToolTipText("Open the task guide on the OSRS Wiki.");
            wiki.addActionListener(event -> wikiOpener.accept(wikiUrl)); controls.add(wiki);
        }
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height)); details.add(controls); add(section);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}

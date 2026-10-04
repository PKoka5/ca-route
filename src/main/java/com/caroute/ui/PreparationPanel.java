package com.caroute.ui;

import com.caroute.domain.PreparationStep;
import java.awt.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.*;
import static com.caroute.ui.PanelStyles.*;

final class PreparationPanel extends JPanel
{
    PreparationPanel(List<PreparationStep> steps, Function<String, String> readable)
    { this(steps, readable, new DisclosureState()); }

    PreparationPanel(List<PreparationStep> steps, Function<String, String> readable, DisclosureState disclosure)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel header = card(); heading(header, "Preparation");
        text(header, steps.isEmpty() ? "No known gaps" : steps.size() + " steps", MUTED, 11, false);
        header.setToolTipText("Preparation for included tasks. Related points are not guaranteed unlock rewards; tasks can have other requirements."); put(this, header);
        JPanel results = page(); add(results); int[] limit = {30}; JButton more = new JButton("Show more"); compact(more);
        more.setName("prepare.more");
        Runnable rebuild = () -> {
            results.removeAll();
            for (PreparationStep step : steps.subList(0, Math.min(limit[0], steps.size())))
            {
                String key = "prep.step." + step.purpose() + "." + step.kind() + "." + step.key();
                String title = readable.apply(step.key()) + (step.kind() == PreparationStep.Kind.SKILL ? " " + step.targetLevel() : "");
                String status = step.unknown() ? "Check" : step.hardRequirement() ? "Required" : "Suggested";
                CollapsibleSection section = new CollapsibleSection(key, title, status, disclosure);
                section.headerToggle().setToolTipText(readable.apply(step.description()));
                section.setStatusColor(step.unknown() ? GOLD : step.hardRequirement() ? RouteWidgets.RED : RouteWidgets.GREEN);
                JPanel details = section.body();
                if (step.kind() == PreparationStep.Kind.SKILL && step.currentLevel() != null)
                {
                    JProgressBar progress = new JProgressBar(0, step.targetLevel()); PanelStyles.progress(progress); progress.setAlignmentX(Component.LEFT_ALIGNMENT);
                    progress.setName("prepare.progress." + step.purpose() + "." + step.key());
                    progress.setValue(Math.min(step.currentLevel(), step.targetLevel())); progress.setStringPainted(true);
                    progress.setString(step.currentLevel() + " / " + step.targetLevel());
                    progress.setToolTipText("Current " + readable.apply(step.key()) + " level / target level"); details.add(progress);
                }
                text(details, readable.apply(step.description()), MUTED, 11, false);
                for (String note : step.notes()) text(details, readable.apply(note), MUTED, 10, false);
                CollapsibleSection related = new CollapsibleSection(key + ".tasks", "Related tasks",
                    Integer.toString(step.taskIds().size()), disclosure);
                text(related.body(), step.potentialPoints() + " related points; other requirements may apply.", MUTED, 10, false);
                for (Map.Entry<Integer, String> task : step.taskNames().entrySet())
                {
                    JPanel name = page(); name.setOpaque(false); name.setName("prepare.task." + task.getKey());
                    text(name, task.getValue(), TEXT, 11, false); related.body().add(name);
                }
                details.add(related); put(results, section);
            }
            more.setVisible(limit[0] < steps.size()); results.revalidate(); results.repaint();
        };
        more.addActionListener(event -> { limit[0] += 30; rebuild.run(); }); add(more); rebuild.run();
    }
}

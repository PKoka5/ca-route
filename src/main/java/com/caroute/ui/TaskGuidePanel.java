package com.caroute.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.caroute.ui.PanelStyles.*;

/** Read-only, numbered task steps. Expanding them never changes the active task or route. */
final class TaskGuidePanel extends JPanel
{
    TaskGuidePanel(List<String> steps)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int index = 0; index < steps.size(); index++)
        {
            JTextArea step = new WrappedText((index + 1) + ". " + steps.get(index));
            step.setName("task.guide.step." + (index + 1)); step.setEditable(false); step.setFocusable(false);
            step.setOpaque(false); step.setForeground(TEXT); step.setFont(font(11, false));
            step.setAlignmentX(Component.LEFT_ALIGNMENT); step.setBorder(new EmptyBorder(2, 0, 4, 0)); add(step);
        }
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}

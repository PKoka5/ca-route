package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.caroute.ui.PanelStyles.*;

/** Small visual summaries drawn by this project; no game/Quest Helper assets. */
final class RouteWidgets
{
    static final Color GREEN = net.runelite.client.ui.ColorScheme.GRAND_EXCHANGE_PRICE,
        RED = net.runelite.client.ui.ColorScheme.PROGRESS_ERROR_COLOR;
    static JPanel metrics(int tasks, int points, double minutes)
    {
        JPanel row = new JPanel(new GridLayout(1, 3, 5, 0)); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(tile("TASKS", Integer.toString(tasks), "tasks")); row.add(tile("POINTS", "+" + points, "points"));
        row.add(tile("EST. TIME", "~" + shortTime(minutes), "time"));
        row.setPreferredSize(new Dimension(180, 26)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26)); return row;
    }
    private static JPanel tile(String label, String value, String icon)
    {
        JPanel tile = new JPanel(); tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS)); tile.setBackground(BACKGROUND);
        tile.setBorder(new EmptyBorder(3, 2, 3, 2));
        JLabel number = new JLabel(value, new MiniIcon(icon, MUTED), SwingConstants.LEFT); number.setForeground(Color.WHITE);
        number.setFont(font(12, false)); number.setIconTextGap(3); number.setAlignmentX(Component.LEFT_ALIGNMENT);
        number.setToolTipText(label + ": " + value); tile.setToolTipText(label + ": " + value);
        number.getAccessibleContext().setAccessibleName(label + ": " + value); tile.add(number); return tile;
    }
    static JLabel badge(String label, Color color, String tooltip)
    {
        JLabel badge = new JLabel(label, new MiniIcon("status", color), SwingConstants.LEFT); badge.setForeground(color);
        badge.setFont(font(11, false)); badge.setIconTextGap(5); badge.setToolTipText(tooltip);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT); badge.setBorder(new EmptyBorder(5, 0, 5, 0)); return badge;
    }
    static String status(TaskAssessment assessment)
    {
        switch (assessment.eligibility().state())
        {
            case COMPLETED: return "Completed";
            case CONTENT_EXCLUDED: return "Excluded";
            case DEFERRED: return "Deferred";
            case QUEST_LOCKED: return "Quest required";
            case SKILL_LOCKED: return "Skill required";
            case GEAR_LOCKED: return "Gear required";
            case UNKNOWN_REQUIREMENT: return "Check requirements";
            case AVAILABLE_BUT_UNDERGEARED: return "Check gear";
            default: return assessment.eligibility().conditional() ? "Check access" : "Available";
        }
    }
    static Color statusColor(TaskAssessment assessment)
    {
        if (assessment.eligibility().state() == EligibilityState.COMPLETED) return GREEN;
        if (assessment.eligibility().state() == EligibilityState.CONTENT_EXCLUDED || assessment.eligibility().state() == EligibilityState.DEFERRED) return MUTED;
        return !assessment.eligibility().recommendedNow() ? RED : assessment.eligibility().conditional()
            || assessment.confidence() == Confidence.LOW || assessment.eligibility().state() == EligibilityState.AVAILABLE_BUT_UNDERGEARED ? GOLD : GREEN;
    }
    static String shortTime(double minutes)
    { int value = (int) Math.ceil(minutes); return value < 60 ? value + "m" : value / 60 + "h" + (value % 60 == 0 ? "" : " " + value % 60 + "m"); }
    private static final class MiniIcon implements Icon
    {
        private final String kind; private final Color color;
        MiniIcon(String kind, Color color) { this.kind = kind; this.color = color; }
        public int getIconWidth() { return 12; } public int getIconHeight() { return 12; }
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.setColor(color);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (kind.equals("time")) { g.drawOval(1, 1, 10, 10); g.drawLine(6, 3, 6, 6); g.drawLine(6, 6, 9, 7); }
            else if (kind.equals("points")) g.fillPolygon(new int[]{6, 11, 6, 1}, new int[]{0, 6, 12, 6}, 4);
            else if (kind.equals("tasks")) { g.drawRect(1, 2, 10, 9); g.drawLine(3, 6, 5, 8); g.drawLine(5, 8, 9, 4); }
            else g.fillOval(3, 3, 7, 7);
            g.dispose();
        }
    }
}

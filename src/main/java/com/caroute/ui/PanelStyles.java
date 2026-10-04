package com.caroute.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class PanelStyles
{
    static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR, CARD = ColorScheme.DARKER_GRAY_COLOR,
        GOLD = ColorScheme.BRAND_ORANGE, MUTED = ColorScheme.LIGHT_GRAY_COLOR, TEXT = Color.WHITE,
        BORDER = ColorScheme.BORDER_COLOR, HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;

    /** RuneLite uses the bundled pixel fonts at their native 16px size. */
    static Font font(int size, boolean bold)
    {
        Font font = bold ? FontManager.getRunescapeBoldFont()
            : size <= 11 ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont();
        return size >= 17 ? font.deriveFont(18f) : font;
    }
    static JPanel page()
    {
        JPanel panel = new TrackedPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND); return panel;
    }
    static JPanel card()
    {
        JPanel panel = page(); panel.setBackground(BACKGROUND); panel.setBorder(new EmptyBorder(4, 2, 4, 2));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel;
    }
    static void put(JPanel page, JPanel card) { page.add(card); page.add(Box.createVerticalStrut(5)); }
    static void heading(JPanel panel, String value) { panel.add(sectionHeading(value)); }
    static JPanel sectionHeading(String value)
    {
        JPanel bar = new JPanel(new BorderLayout()); bar.setBackground(CARD); bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(value); label.setFont(font(12, false)); label.setForeground(TEXT);
        bar.setBorder(new EmptyBorder(5, 4, 5, 4)); bar.add(label);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); bar.setPreferredSize(new Dimension(180, 30)); return bar;
    }
    static JPanel formRow(String title, JComponent control)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0)); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title); label.setForeground(TEXT); label.setFont(font(12, false));
        label.setPreferredSize(new Dimension(55, 26)); label.setLabelFor(control); compact(control);
        control.setMinimumSize(new Dimension(0, 26)); row.add(label, BorderLayout.WEST); row.add(control, BorderLayout.CENTER);
        row.setPreferredSize(new Dimension(180, 26)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26)); return row;
    }
    static void text(JPanel panel, String value, Color color, int size, boolean bold)
    {
        JTextArea label = new WrappedText(value); label.setEditable(false); label.setFocusable(false);
        label.setOpaque(false); label.setForeground(color); label.setFont(font(size, bold));
        label.setAlignmentX(Component.LEFT_ALIGNMENT); label.setBorder(new EmptyBorder(2, 0, 3, 0)); panel.add(label);
    }
    static void compact(JComponent control)
    {
        control.setFont(FontManager.getRunescapeFont()); control.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (control instanceof AbstractButton || control instanceof JComboBox || control instanceof JSpinner
            || control instanceof javax.swing.text.JTextComponent)
        {
            control.setBackground(ColorScheme.CONTROL_COLOR); control.setForeground(Color.WHITE);
        }
        if (control instanceof JButton)
        {
            JButton button = (JButton) control; button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        if (control instanceof JProgressBar) progress((JProgressBar) control);
    }
    static void progress(JProgressBar progress)
    {
        progress.setForeground(GOLD); progress.setBackground(BORDER); progress.setBorderPainted(false);
        progress.setFont(FontManager.getRunescapeSmallFont());
    }
    private static final class TrackedPanel extends JPanel implements Scrollable
    {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
        { return Math.max(18, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

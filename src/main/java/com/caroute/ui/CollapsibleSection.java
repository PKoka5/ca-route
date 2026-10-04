package com.caroute.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.caroute.ui.PanelStyles.*;

/** A flat title row with a right disclosure arrow and a separately clickable Wiki action. */
final class CollapsibleSection extends JPanel
{
    private final JPanel body = page();
    private final HeaderButton header;
    private final String key;
    private final DisclosureState state;
    private boolean expanded;

    CollapsibleSection(String key, String title, String summary, boolean expanded)
    { this(key, title, summary, expanded, null); }
    CollapsibleSection(String key, String title, String summary, DisclosureState state)
    { this(key, title, summary, false, state); }
    CollapsibleSection(String key, String title, String summary, boolean expanded, DisclosureState state)
    {
        this.key = key; this.state = state;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); setAlignmentX(Component.LEFT_ALIGNMENT);
        header = new HeaderButton(title, summary); header.setName("collapse." + key);
        header.getAccessibleContext().setAccessibleName(title); header.setToolTipText(title);
        body.setName("details." + key); body.setBackground(CARD); body.setBorder(new EmptyBorder(6, 6, 6, 6));
        header.addActionListener(event -> setExpanded(!this.expanded)); add(header); add(body);
        this.expanded = state == null ? expanded : state.expanded(key, expanded);
        body.setVisible(this.expanded); updateAccessible();
    }
    JPanel body() { return body; }
    JButton headerToggle() { return header; }
    void setHeaderLink(JButton link)
    {
        if (header.link != null) header.remove(header.link);
        header.link = link;
        if (link != null) header.add(link);
        invalidateAncestors(); revalidate(); repaint();
    }
    boolean isExpanded() { return expanded; }
    void setExpanded(boolean expanded)
    {
        this.expanded = expanded; body.setVisible(expanded);
        if (state != null) state.setExpanded(key, expanded);
        updateAccessible(); invalidateAncestors(); revalidate(); repaint();
    }
    private void invalidateAncestors()
    { for (Container ancestor = this; ancestor != null; ancestor = ancestor.getParent()) ancestor.invalidate(); }
    void setStatusColor(Color color) { header.statusColor = color; header.repaint(); }
    void setSummary(String summary) { header.summary = summary == null ? "" : summary; header.revalidate(); header.repaint(); }
    private void updateAccessible()
    { header.getAccessibleContext().setAccessibleDescription(expanded ? "Expanded; activate to collapse details" : "Collapsed; activate to show details"); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    private final class HeaderButton extends JButton
    {
        private String summary;
        private Color statusColor;
        private JButton link;
        HeaderButton(String title, String summary)
        {
            super(title); this.summary = summary == null ? "" : summary;
            setLayout(null); setFont(font(12, false)); setForeground(TEXT);
            setAlignmentX(Component.LEFT_ALIGNMENT); setBorderPainted(false); setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setRolloverEnabled(true);
        }
        private int summaryWidth(int width)
        { return summary.isEmpty() ? 0 : Math.min(Math.max(30, width / 3), getFontMetrics(font(10, false)).stringWidth(summary)); }
        private int linkWidth() { return link == null ? 0 : 24; }
        private int titleWidth(int width) { return Math.max(20, width - 32 - linkWidth() - summaryWidth(width) - (summary.isEmpty() ? 0 : 8)); }
        @Override public void doLayout()
        { if (link != null) link.setBounds(Math.max(0, getWidth() - 48), (getHeight() - 22) / 2, 22, 22); }
        @Override public Dimension getPreferredSize()
        {
            int width = getParent() != null && getParent().getWidth() > 50 ? getParent().getWidth() : 220;
            return new Dimension(width, Math.max(25, getFontMetrics(getFont()).getHeight() + 5));
        }
        @Override public Dimension getMinimumSize() { return new Dimension(60, getPreferredSize().height); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(getModel().isRollover() || getModel().isPressed() ? HOVER : BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            int cy = getHeight() / 2;
            g.setFont(getFont()); FontMetrics metrics = g.getFontMetrics();
            g.setColor(statusColor == null ? TEXT : statusColor);
            g.drawString(ellipsize(getText(), metrics, titleWidth(getWidth())), 4, cy + (metrics.getAscent() - metrics.getDescent()) / 2);
            if (!summary.isEmpty())
            {
                g.setFont(font(10, false)); FontMetrics small = g.getFontMetrics();
                int w = summaryWidth(getWidth()), x = getWidth() - 28 - linkWidth() - w;
                g.setColor(MUTED); g.drawString(ellipsize(summary, small, w), x, cy + (small.getAscent() - small.getDescent()) / 2);
            }
            int arrowX = getWidth() - 21;
            g.setColor(BORDER); g.fillRect(arrowX, cy - 9, 18, 18);
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.8f));
            if (expanded) { g.drawLine(arrowX + 5, cy - 2, arrowX + 9, cy + 2); g.drawLine(arrowX + 9, cy + 2, arrowX + 13, cy - 2); }
            else { g.drawLine(arrowX + 7, cy - 4, arrowX + 11, cy); g.drawLine(arrowX + 11, cy, arrowX + 7, cy + 4); }
            if (hasFocus()) { g.setColor(GOLD); g.drawRect(1, 1, getWidth() - 3, getHeight() - 3); }
            g.dispose();
        }
    }
    private static String ellipsize(String text, FontMetrics metrics, int width)
    {
        if (metrics.stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + "\u2026") > width) end--;
        return end == 0 ? "" : text.substring(0, end) + "\u2026";
    }
}

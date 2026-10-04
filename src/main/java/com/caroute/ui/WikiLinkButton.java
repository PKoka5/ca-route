package com.caroute.ui;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import static com.caroute.ui.PanelStyles.*;

/** A separate, keyboard-accessible external-link action with a project-drawn icon. */
final class WikiLinkButton extends JButton
{
    WikiLinkButton(String key, String title, String url, Consumer<String> opener)
    {
        setName("wiki." + key); setIcon(new ExternalIcon()); setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String description = WikiLinks.search(url) ? "Search OSRS Wiki for " + title : "Open " + title + " on OSRS Wiki";
        setToolTipText(description); getAccessibleContext().setAccessibleName(description);
        addActionListener(event -> opener.accept(url));
    }
    @Override public Dimension getPreferredSize() { return new Dimension(22, 22); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }
    @Override protected void paintComponent(Graphics graphics)
    {
        if (getModel().isRollover() || getModel().isPressed() || hasFocus())
        {
            graphics.setColor(HOVER); graphics.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(graphics);
    }
    private static final class ExternalIcon implements Icon
    {
        public int getIconWidth() { return 13; }
        public int getIconHeight() { return 13; }
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y);
            AbstractButton button = (AbstractButton) component;
            g.setColor(button.getModel().isRollover() || button.hasFocus() ? Color.WHITE : MUTED);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.3f)); g.drawLine(2, 4, 2, 11); g.drawLine(2, 11, 10, 11); g.drawLine(10, 11, 10, 8);
            g.drawLine(2, 4, 5, 4); g.drawLine(6, 2, 11, 2); g.drawLine(11, 2, 11, 7); g.drawLine(11, 2, 6, 7); g.dispose();
        }
    }
}

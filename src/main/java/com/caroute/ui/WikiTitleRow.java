package com.caroute.ui;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.caroute.ui.PanelStyles.*;

/** Boss title and a small external-link action share the available sidebar width. */
final class WikiTitleRow extends JPanel
{
    WikiTitleRow(String key, String title, String url, Consumer<String> opener)
    {
        setLayout(new BorderLayout(3, 0)); setOpaque(false); setAlignmentX(Component.LEFT_ALIGNMENT);
        final boolean linked = !url.isEmpty();
        JTextArea name = new JTextArea(title) {
            @Override public Dimension getPreferredSize()
            {
                int width = getParent() != null && getParent().getWidth() > 50 ? getParent().getWidth() - (linked ? 25 : 0) : 175;
                setSize(Math.max(40, width), Short.MAX_VALUE);
                return new Dimension(Math.max(40, width), super.getPreferredSize().height);
            }
            @Override public Dimension getMinimumSize() { return new Dimension(30, getPreferredSize().height); }
        };
        name.setLineWrap(true); name.setWrapStyleWord(true); name.setEditable(false); name.setFocusable(false);
        name.setOpaque(false); name.setForeground(Color.WHITE); name.setFont(font(12, false));
        name.setBorder(new EmptyBorder(2, 0, 3, 0)); add(name, BorderLayout.CENTER);
        if (linked) add(new WikiLinkButton(key, title, url, opener), BorderLayout.EAST);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}

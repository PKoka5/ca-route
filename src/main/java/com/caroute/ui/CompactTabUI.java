package com.caroute.ui;

import java.awt.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import static com.caroute.ui.PanelStyles.*;

/** RuneLite's neutral tab surfaces and orange selection, sized for five sidebar tabs. */
final class CompactTabUI extends BasicTabbedPaneUI
{
    @Override protected Insets getTabInsets(int placement, int index) { return new Insets(5, 2, 5, 2); }
    @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 3, 0); }
    @Override protected Insets getContentBorderInsets(int placement) { return new Insets(0, 0, 0, 0); }
    @Override protected void paintTabBackground(Graphics graphics, int placement, int index,
        int x, int y, int width, int height, boolean selected)
    {
        graphics.setColor(selected ? CARD : BACKGROUND); graphics.fillRect(x, y, width, height);
    }
    @Override protected void paintTabBorder(Graphics graphics, int placement, int index,
        int x, int y, int width, int height, boolean selected)
    {
        if (selected) { graphics.setColor(GOLD); graphics.fillRect(x, y + height - 2, width, 2); }
    }
    @Override protected void paintContentBorder(Graphics graphics, int placement, int selectedIndex) { }
}

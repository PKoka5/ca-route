package com.caroute.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Original monochrome quest-star mark, readable on RuneLite's dark toolbar. */
public final class CombatAchievementIcon
{
    private CombatAchievementIcon() { }

    public static BufferedImage create()
    {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(18, 18, 18)); graphics.fillOval(2, 2, 28, 28);
        graphics.setStroke(new BasicStroke(1.5f)); graphics.setColor(new Color(174, 174, 174)); graphics.drawOval(3, 3, 26, 26);
        graphics.setColor(new Color(56, 56, 56)); graphics.fillOval(5, 5, 22, 22);
        Path2D star = new Path2D.Double();
        for (int point = 0; point < 16; point++)
        {
            double angle = -Math.PI / 2 + point * Math.PI / 8;
            double radius = point % 2 == 0 ? 12 : 5.2;
            double x = 16 + Math.cos(angle) * radius, y = 16 + Math.sin(angle) * radius;
            if (point == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        graphics.setPaint(new GradientPaint(6, 4, new Color(238, 238, 238), 25, 28, new Color(130, 130, 130)));
        graphics.fill(star); graphics.setColor(new Color(12, 12, 12)); graphics.setStroke(new BasicStroke(1)); graphics.draw(star);
        graphics.fillOval(13, 13, 6, 6); graphics.setColor(new Color(204, 204, 204)); graphics.drawOval(13, 13, 6, 6);
        graphics.dispose(); return image;
    }
}

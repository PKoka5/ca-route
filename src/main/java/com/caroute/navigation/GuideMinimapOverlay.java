package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import java.awt.*;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.*;

/** A local minimap destination dot without taking over the client's shared hint arrow. */
public final class GuideMinimapOverlay extends Overlay
{
    private final Client client;
    private final Supplier<ActiveGuide> selected;
    private final CaRouteConfig config;
    public GuideMinimapOverlay(Client client, Supplier<ActiveGuide> selected, CaRouteConfig config)
    {
        this.client = client; this.selected = selected; this.config = config;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_WIDGETS); setPriority(PRIORITY_LOW);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        ActiveGuide guide = selected.get(); WorldView world = client.getTopLevelWorldView();
        if (guide == null || guide.destination() == null || !config.guideMinimapMarker()
            || client.getGameState() != GameState.LOGGED_IN || world == null || world.isInstance()
            || world.getPlane() != guide.destination().point().getPlane()) return null;
        LocalPoint location = LocalPoint.fromWorld(world, guide.destination().point());
        if (location != null)
        {
            net.runelite.api.Point dot = Perspective.localToMinimap(client, location);
            if (dot != null) OverlayUtil.renderMinimapLocation(graphics, dot, ColorScheme.BRAND_ORANGE);
        }
        return null;
    }
}

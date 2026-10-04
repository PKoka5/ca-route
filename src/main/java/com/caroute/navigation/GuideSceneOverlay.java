package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import java.awt.*;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.*;

/** Highlights only the selected destination and matching, currently visible NPCs. */
public final class GuideSceneOverlay extends Overlay
{
    private final Client client;
    private final Supplier<ActiveGuide> selected;
    private final CaRouteConfig config;
    public GuideSceneOverlay(Client client, Supplier<ActiveGuide> selected, CaRouteConfig config)
    {
        this.client = client; this.selected = selected; this.config = config;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE); setPriority(PRIORITY_LOW);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        ActiveGuide guide = selected.get(); WorldView world = client.getTopLevelWorldView();
        if (guide == null || world == null || client.getGameState() != GameState.LOGGED_IN) return null;
        if (config.guideSceneMarker() && guide.destination() != null && !world.isInstance()
            && world.getPlane() == guide.destination().point().getPlane())
        {
            LocalPoint location = LocalPoint.fromWorld(world, guide.destination().point());
            if (location != null)
            {
                Polygon tile = Perspective.getCanvasTilePoly(client, location);
                if (tile != null) OverlayUtil.renderPolygon(graphics, tile, ColorScheme.BRAND_ORANGE);
            }
        }
        if (config.guideNpcHighlight() && world.npcs() != null)
            for (NPC npc : world.npcs())
                if (npc != null && guide.matchesNpc(npc.getName()))
                    OverlayUtil.renderActorOverlay(graphics, npc, "CA target", ColorScheme.BRAND_ORANGE);
        return null;
    }
}

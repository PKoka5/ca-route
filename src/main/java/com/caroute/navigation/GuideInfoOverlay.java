package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import com.caroute.domain.CombatAchievement;
import java.awt.*;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;

/** A movable task reminder. Completion and KC come only from the existing account reader. */
public final class GuideInfoOverlay extends OverlayPanel
{
    private final Client client;
    private final Supplier<ActiveGuide> selected;
    private final CaRouteConfig config;

    public GuideInfoOverlay(Plugin plugin, Client client, Supplier<ActiveGuide> selected, CaRouteConfig config)
    {
        super(plugin); this.client = client; this.selected = selected; this.config = config;
        setPosition(OverlayPosition.TOP_LEFT); setPriority(PRIORITY_LOW);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        ActiveGuide guide = selected.get();
        if (guide == null || !config.guideTaskOverlay() || client.getGameState() != GameState.LOGGED_IN) return null;
        panelComponent.getChildren().clear(); panelComponent.setPreferredSize(new Dimension(230, 0));
        line(guide.title(), Color.WHITE);
        CombatAchievement task = guide.task();
        if (task != null)
        {
            line(guide.condition(), ColorScheme.LIGHT_GRAY_COLOR);
            if (task.kcThreshold() > 0 && guide.killCount() != null)
                line("Recorded KC: " + guide.killCount() + " / " + task.kcThreshold(), ColorScheme.LIGHT_GRAY_COLOR);
        }
        else if (guide.destination() != null) line("Travel: " + guide.destination().label(), ColorScheme.LIGHT_GRAY_COLOR);
        if (guide.caution().contains("Wilderness") || guide.caution().contains("PvP"))
            line(guide.caution(), ColorScheme.BRAND_ORANGE);
        return super.render(graphics);
    }
    private void line(String text, Color color)
    { if (!text.isEmpty()) panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(color).build()); }
}

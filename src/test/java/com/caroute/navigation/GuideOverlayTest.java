package com.caroute.navigation;

import com.caroute.CaRouteConfig;
import com.caroute.CaRoutePlugin;
import com.caroute.domain.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideOverlayTest
{
    @Test public void reminderRendersTheSelectedTaskAndOnlyKnownKcThenDisappearsOnStop() throws Exception
    {
        CombatAchievement task = CombatAchievement.builder(1, "K'ril Tsutsaroth Adept", "K'ril Tsutsaroth", AchievementTier.MEDIUM)
            .description("Kill K'ril Tsutsaroth 25 times.").kcThreshold(25).build();
        ActivityDestinations.Destination destination = ActivityDestinations.forActivity(task.activity()).orElseThrow();
        AtomicReference<ActiveGuide> selection = new AtomicReference<>(new ActiveGuide(task, task.activity(), destination,
            "Target sent to Shortest Path", null, "Check access before entering."));
        GuideInfoOverlay overlay = new GuideInfoOverlay(new CaRoutePlugin(), client(), selection::get, new CaRouteConfig(){});
        overlay.setClearChildren(false);
        BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.setColor(new Color(40, 40, 40)); graphics.fillRect(0, 0, 260, 260);
        graphics.setFont(FontManager.getRunescapeFont()); graphics.translate(10, 10);
        overlay.render(graphics); // Native PanelComponent measures children on the preceding frame.
        Dimension size = overlay.render(graphics);
        assertNotNull(size); assertTrue("The reminder remains compact", size.width > 0 && size.height >= 30 && size.height <= 100);
        long unknownLines = lineCount(overlay);
        selection.set(new ActiveGuide(task, task.activity(), destination, "Entrance reached; continue with the selected task",
            20, "Check access before entering."));
        graphics.setColor(new Color(40, 40, 40)); graphics.fillRect(-10, -10, 260, 260);
        assertNotNull(overlay.render(graphics));
        assertEquals("Known KC adds one line to the rendered reminder", unknownLines + 1, lineCount(overlay));
        graphics.setColor(new Color(40, 40, 40)); graphics.fillRect(-10, -10, 260, 260);
        overlay.render(graphics);
        graphics.dispose(); Path preview = Path.of("build", "ui-active-task-overlay.png");
        Files.createDirectories(preview.getParent()); ImageIO.write(image, "png", preview.toFile());
        selection.set(null); Graphics2D after = image.createGraphics(); assertNull(overlay.render(after)); after.dispose();
    }
    @Test public void disabledTaskReminderRendersNothing()
    {
        CombatAchievement task = CombatAchievement.builder(1, "Selected task", "Kraken", AchievementTier.EASY).build();
        ActiveGuide guide = new ActiveGuide(task, task.activity(), null, "Task guide only", null, "");
        GuideInfoOverlay overlay = new GuideInfoOverlay(new CaRoutePlugin(), client(), () -> guide,
            new CaRouteConfig() { @Override public boolean guideTaskOverlay() { return false; } });
        BufferedImage image = new BufferedImage(260, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); assertNull(overlay.render(graphics)); graphics.dispose();
    }
    private static long lineCount(GuideInfoOverlay overlay)
    { return overlay.getPanelComponent().getChildren().stream().filter(LineComponent.class::isInstance).count(); }
    private static Client client()
    {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> method.getName().equals("getGameState") ? GameState.LOGGED_IN : null);
    }
}

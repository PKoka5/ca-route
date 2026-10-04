package com.caroute.ui;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerRequirementsUiTest extends RuneLiteUiTestSupport
{
    @Test public void wyrmRequirementsArePlayerFacingInRouteBossesAndTasks() throws Exception
    {
        CombatAchievement wyrm = AchievementRepository.loadBundled().tasks().get(287);
        assertEquals("Wyrm", wyrm.activity());
        PlayerProfile account = PlayerProfile.builder().accountKey("requirements-preview").version(1)
            .loaded(true).catalogueComplete(true).thresholds(Map.of(AchievementTier.EASY, 1))
            .skills(Map.of("Slayer", 70, "Defence", 70, "Prayer", 43, "Ranged", 75, "Attack", 75, "Strength", 75)).build();
        RoutingPreferences preferences = RoutingPreferences.builder().target(AchievementTier.EASY).build();
        RoutePlan plan = new RouteEngine().plan(List.of(wyrm), account, preferences);
        assertEquals(1, plan.stops().size());
        assertTrue("The engine still retains planning uncertainty internally", plan.assessments().get(0).reasons()
            .stream().anyMatch(reason -> reason.contains("heuristic") || reason.contains("KC is unknown")));
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, pref) -> {});
                panel.display(plan, List.of(), preferences, new AccountView(GearView.unknown(), account, Map.of(), false));
                JTabbedPane tabs = find(panel, JTabbedPane.class);
                String[] titles = {"Route", "Bosses", "Tasks"};
                String[] keys = {"route.task.287", "boss.task.287", "tasks.task.287"};
                for (int index = 0; index < keys.length; index++)
                {
                    int tabIndex = tabs.indexOfTab(titles[index]); assertTrue("Missing tab " + titles[index], tabIndex >= 0);
                    tabs.setSelectedIndex(tabIndex);
                    JScrollPane scroll = (JScrollPane) tabs.getComponentAt(tabIndex);
                    Container page = (Container) scroll.getViewport().getView();
                    if (index == 1) button(page, "collapse.boss.Wyrm").doClick(0);
                    button(page, "collapse." + keys[index]).doClick(0);
                    button(page, "collapse." + keys[index] + ".notes").doClick(0);
                    JComponent details = (JComponent) named(page, "details." + keys[index]);
                    String text = text(details);
                    assertTrue(text, text.contains("62 Slayer"));
                    assertTrue("The account snapshot reaches every task view", text.contains("70"));
                    assertTrue(text, text.toLowerCase().contains("boots of stone"));
                    assertTrue(text, text.contains("Kourend & Kebos"));
                    for (String technical : List.of("KC is unknown", "ETA assumes", "heuristic estimates",
                        "Access requirements are not completely verified", "readiness metadata", "partial ownership"))
                        assertFalse("Task details leaked engine text: " + technical, text.contains(technical));
                }
                int routeIndex = tabs.indexOfTab("Route"); tabs.setSelectedIndex(routeIndex);
                for (int width : new int[]{225, 280})
                {
                    panel.setSize(width, 850);
                    for (int pass = 0; pass < 4; pass++) layout(panel);
                    JScrollPane scroll = (JScrollPane) tabs.getComponentAt(routeIndex);
                    assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
                    BufferedImage preview = new BufferedImage(width, 850, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = preview.createGraphics(); panel.printAll(graphics); graphics.dispose();
                    Path file = Path.of("build", "ui-player-requirements-" + width + ".png");
                    Files.createDirectories(file.getParent()); ImageIO.write(preview, "png", file.toFile());
                }
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static JButton button(Component root, String name)
    { Component found = named(root, name); assertNotNull(name, found); return (JButton) found; }
    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { Component found = named(child, name); if (found != null) return found; }
        return null;
    }
    private static <T extends Component> T find(Component root, Class<T> type)
    {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T found = find(child, type); if (found != null) return found; }
        return null;
    }
    private static String text(Component root)
    {
        StringBuilder result = new StringBuilder();
        if (root instanceof JTextComponent) result.append(((JTextComponent) root).getText()).append('\n');
        if (root instanceof JLabel) result.append(((JLabel) root).getText()).append('\n');
        if (root instanceof AbstractButton) result.append(((AbstractButton) root).getText()).append('\n');
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) result.append(text(child));
        return result.toString();
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
}

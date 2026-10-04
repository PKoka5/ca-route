package com.caroute.ui;

import com.caroute.domain.*;
import com.caroute.engine.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Renders the actual five-tab sidebar for review at RuneLite's narrow width. */
public class ReleasePreviewTest extends RuneLiteUiTestSupport
{
    @Test public void budgetProfileAndPreparationRenderWithinTheSidebar() throws Exception
    {
        List<CombatAchievement> tasks = List.of(
            CombatAchievement.builder(1, "A clean first kill", "Scurrius", AchievementTier.EASY)
                .description("Complete a kill without taking avoidable damage. This is a synthetic preview.")
                .recommendedGear(Set.of("ratbane-weapon")).requirementsKnown(true).timingKnown(true).secondsPerKill(120).setupMinutes(3).build(),
            CombatAchievement.builder(2, "Train before returning", "Locked boss", AchievementTier.EASY)
                .requirement(Requirement.skill("Attack", 70)).requirementsKnown(true).timingKnown(true).build());
        PlayerProfile account = PlayerProfile.builder().accountKey("123").version(1).loaded(true).catalogueComplete(true)
            .skills(Map.of("Attack", 50)).killCounts(Map.of("Scurrius", 15)).thresholds(Map.of(AchievementTier.EASY, 10))
            .gear(GearProfile.unknown().withActivePreparationEstimates(Map.of("ratbane-weapon", 10.0))).build();
        RoutingPreferences prefs = RoutingPreferences.builder().target(AchievementTier.EASY)
            .objective(RouteObjective.MAXIMUM_POINTS_PER_HOUR).timeBudgetMinutes(30).build();
        RoutePlan raw = new RouteEngine().plan(tasks, account, prefs);
        RoutePlan plan = raw.withPreparationSteps(new PreparationAdvisor().advise(raw.assessments(), account));
        assertFalse(plan.preparationSteps().isEmpty());
        SwingUtilities.invokeAndWait(() -> {
            try {
                CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, pref) -> {});
                panel.display(plan, List.of("Synthetic preview; no live account data."), prefs,
                    new AccountView(GearView.unknown(), account, Map.of(), true));
                panel.setSize(280, 1000); JTabbedPane tabs = find(panel, JTabbedPane.class); assertEquals(5, tabs.getTabCount());
                String[] titles = {"Route", "Profile", "Prep"}; String[] names = {"route", "profile", "prepare"};
                for (int i = 0; i < titles.length; i++)
                {
                    int index = tabs.indexOfTab(titles[i]); assertTrue("Missing tab " + titles[i], index >= 0);
                    tabs.setSelectedIndex(index); layout(panel);
                    JScrollPane scroll = (JScrollPane) tabs.getComponentAt(index);
                    assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
                    BufferedImage image = new BufferedImage(280, 1000, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                    Path target = Path.of("build", "ui-" + names[i] + "-rc3.png"); Files.createDirectories(target.getParent()); ImageIO.write(image, "png", target.toFile());
                }
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component component : parent.getComponents()) if (component instanceof Container) layout((Container) component); }
    private static <T extends Component> T find(Container parent, Class<T> type)
    {
        for (Component component : parent.getComponents())
        {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container) { T child = find((Container) component, type); if (child != null) return child; }
        }
        return null;
    }
}

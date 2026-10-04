package com.caroute.ui;

import com.caroute.data.GearCapabilityRepository;
import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class GearControlsTest extends RuneLiteUiTestSupport
{
    @Test public void profileGearControlsPublishExplicitChoicesAndBankOptIn() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> choices = new ArrayList<>();
            List<Boolean> banks = new ArrayList<>();
            int[] resets = {0};
            CaRoutePanel panel = new CaRoutePanel((id, action) -> {}, () -> {}, ids -> {}, target -> {}, (flag, preference) -> {},
                (id, owned) -> choices.add(id + "=" + owned), banks::add, () -> resets[0]++);
            PlayerProfile account = PlayerProfile.builder().loaded(true).catalogueComplete(true)
                .thresholds(Map.of(AchievementTier.EASY, 1)).build();
            CombatAchievement task = CombatAchievement.builder(0, "Example", "Example boss", AchievementTier.EASY)
                .requirementsKnown(true).timingKnown(true).build();
            RoutePlan plan = new RouteEngine().plan(Collections.singletonList(task), account, RoutingPreferences.builder().build());
            panel.display(plan, Collections.emptyList(), RoutingPreferences.builder().build(), GearView.unknown());
            assertTrue("Rendering must not submit manual overrides", choices.isEmpty());
            String capability = GearCapabilityRepository.loadBundled().definitions().get(0).id();
            JComboBox<?> combo = (JComboBox<?>) find(panel, "gear." + capability);
            select(combo, "Owned"); select(combo, "Missing"); select(combo, "Auto");
            assertEquals(List.of(capability + "=true", capability + "=false", capability + "=null"), choices);
            ((JCheckBox) find(panel, "gear.bank")).doClick();
            assertEquals(List.of(true), banks);
            ((JButton) find(panel, "gear.reset")).doClick();
            assertEquals(1, resets[0]);
            panel.display(plan, Collections.emptyList(), RoutingPreferences.builder().build(),
                new GearView(GearProfile.unknown(), Map.of(), true, false, 0, 0));
            renderGearPreview(panel);
        });
    }

    private static void renderGearPreview(CaRoutePanel panel)
    {
        try
        {
            JTabbedPane tabs = (JTabbedPane) find(panel, "ca.tabs");
            tabs.setSelectedIndex(3); panel.setSize(280, 1000); layout(panel); layout(panel);
            JScrollPane profile = (JScrollPane) tabs.getComponentAt(3);
            assertEquals("Profile content must fit the sidebar width", profile.getViewport().getExtentSize().width, profile.getViewport().getView().getWidth());
            profile.getViewport().setViewPosition(new Point(0, 450)); layout(panel);
            BufferedImage image = new BufferedImage(280, 1000, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
            Path target = Path.of("build", "gear-profile-preview.png"); Files.createDirectories(target.getParent());
            ImageIO.write(image, "png", target.toFile());
        }
        catch (Exception ex) { throw new IllegalStateException("Cannot render the gear profile preview", ex); }
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }

    private static void select(JComboBox<?> combo, String label)
    {
        for (int i = 0; i < combo.getItemCount(); i++)
            if (combo.getItemAt(i).toString().equals(label)) { combo.setSelectedIndex(i); return; }
        fail("Missing gear choice: " + label);
    }
    private static Component find(Container parent, String name)
    {
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) { Component match = find((Container) child, name); if (match != null) { return match; } }
        }
        return null;
    }
}

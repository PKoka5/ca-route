package com.caroute.ui;

import com.caroute.domain.*;
import com.caroute.data.AchievementRepository;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskGuidePanelTest extends RuneLiteUiTestSupport
{
    private static final List<String> STEPS = List.of("Travel to the dungeon and equip suitable protective boots before entering.",
        "Use a ranged weapon from a distance and Protect from Magic to reduce incoming damage during the fight.",
        "Kill one Wyrm while keeping your protection active, then check the achievement completion in game.");

    @Test public void openingTheStaticGuideRevealsNumberedStepsWithoutStartingOrChangingATask() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> effects = new ArrayList<>(); DisclosureState state = new DisclosureState();
            TaskCard card = card(state, effects);
            JButton task = button(card, "collapse.test.task.42"); task.doClick(0);
            JButton guide = button(card, "collapse.test.task.42.guide");
            JComponent body = (JComponent) named(card, "details.test.task.42.guide");
            assertFalse("A task opens without filling its view with guide text", body.isVisible());
            assertFalse(visibleText(card).contains(STEPS.get(0)));
            guide.doClick(0); assertTrue(body.isVisible());
            for (int index = 0; index < STEPS.size(); index++)
                assertTrue(visibleText(body).contains((index + 1) + ". " + STEPS.get(index)));
            assertFalse("The guide does not open preparation details", ((JComponent) named(card, "details.test.task.42.notes")).isVisible());
            guide.doClick(0); assertFalse(body.isVisible()); guide.doClick(0);
            assertTrue("Rerendering retains the disclosure choice", ((JComponent) named(card(state, effects), "details.test.task.42.guide")).isVisible());
            assertFalse("A new account starts with its guide closed", ((JComponent) named(card(new DisclosureState(), effects), "details.test.task.42.guide")).isVisible());
            assertTrue("Guide disclosure never starts, changes or completes a task or opens a browser", effects.isEmpty());
        });
    }

    @Test public void numberedStepsWrapWithinTheNarrowRuneLiteSidebar() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            TaskCard card = card(new DisclosureState(), new ArrayList<>());
            button(card, "collapse.test.task.42").doClick(0);
            button(card, "collapse.test.task.42.guide").doClick(0);
            for (int width : new int[]{225, 280})
            {
                card.setSize(width, 850);
                for (int pass = 0; pass < 4; pass++) layout(card);
                Container guide = (Container) named(card, "details.test.task.42.guide");
                assertTrue("Expanded guidance allocates enough height at " + width, guide.getHeight() > 80);
                for (int index = 0; index < STEPS.size(); index++)
                {
                    JTextArea step = (JTextArea) named(guide, "task.guide.step." + (index + 1));
                    assertTrue(step.getLineWrap()); assertTrue(step.getWrapStyleWord());
                    assertTrue("A step retains useful horizontal space", step.getWidth() > 100);
                    assertTrue("Wrapped content receives multiple text lines", step.getHeight() >= 2 * step.getFontMetrics(step.getFont()).getHeight());
                    assertTrue(step.getX() + step.getWidth() <= step.getParent().getWidth());
                    assertFalse("Guide steps are read-only", step.isEditable());
                }
                try { renderBundledPreview(width); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }
        });
    }

    private static TaskCard card(DisclosureState state, List<String> effects)
    {
        CombatAchievement task = CombatAchievement.builder(42, "A Slithery Encounter", "Wyrm", AchievementTier.EASY)
            .description("Kill a Wyrm.").quickGuide(STEPS).wikiUrl("https://oldschool.runescape.wiki/w/Wyrm").build();
        TaskAssessment assessment = new TaskAssessment(task, new Eligibility(EligibilityState.AVAILABLE, List.of(), Confidence.HIGH, true, false),
            1, 1, Map.of(), List.of(), Confidence.HIGH);
        return new TaskCard("test.task.42", assessment, state, value -> value,
            (id, action) -> effects.add("action"), url -> effects.add("wiki"), id -> effects.add("start"));
    }
    private static void renderBundledPreview(int width) throws Exception
    {
        CombatAchievement wyrm = AchievementRepository.loadBundled().tasks().get(287);
        TaskAssessment assessment = new TaskAssessment(wyrm, new Eligibility(EligibilityState.UNKNOWN_REQUIREMENT, List.of(), Confidence.LOW, true, false),
            1, 1, Map.of(), List.of(), Confidence.LOW);
        TaskCard card = new TaskCard("preview.wyrm", assessment, new DisclosureState(), value -> value,
            (id, action) -> {}, url -> {}, id -> {});
        button(card, "collapse.preview.wyrm").doClick(0);
        button(card, "collapse.preview.wyrm.guide").doClick(0);
        card.setSize(width, 800);
        for (int pass = 0; pass < 4; pass++) layout(card);
        int height = card.getPreferredSize().height;
        card.setSize(width, height);
        for (int pass = 0; pass < 4; pass++) layout(card);
        Container guide = (Container) named(card, "details.preview.wyrm.guide");
        for (int index = 0; index < wyrm.quickGuide().size(); index++)
        {
            JTextArea step = (JTextArea) named(guide, "task.guide.step." + (index + 1));
            Rectangle2D end = step.modelToView2D(step.getDocument().getLength() - 1);
            assertNotNull(end);
            assertTrue("The last guide line must be fully allocated at " + width + ": step " + (index + 1)
                + " bounds=" + step.getBounds() + " end=" + end + " parent=" + step.getParent().getBounds(),
                end.getMaxY() <= step.getHeight() - step.getInsets().bottom);
            Point origin = SwingUtilities.convertPoint(step, 0, 0, guide);
            assertTrue("The guide container must not clip the last line at " + width,
                origin.y + end.getMaxY() <= guide.getHeight());
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.setColor(PanelStyles.BACKGROUND);
        graphics.fillRect(0, 0, width, height); card.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", Path.of("build", "ui-task-quick-guide-" + width + ".png").toFile());
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
    private static String visibleText(Component root)
    {
        if (!root.isVisible()) return "";
        StringBuilder value = new StringBuilder();
        if (root instanceof JTextComponent) value.append(((JTextComponent) root).getText()).append('\n');
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) value.append(visibleText(child));
        return value.toString();
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
}

package com.caroute.ui;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskBrowserPanelTest extends RuneLiteUiTestSupport
{
    @Test public void paginationMakesTheLastTaskAccessibleAndResetsAfterSearch() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<TaskAssessment> tasks = new ArrayList<>();
            for (int id = 0; id < 121; id++) tasks.add(assessment(id, String.format(Locale.ROOT, "Task %03d", id), "Boss", "Mechanical",
                AchievementTier.EASY, EligibilityState.AVAILABLE, true, id + 1, id + 1, 1, "Description " + id));
            TaskBrowserPanel panel = browser(tasks);
            JButton previous = control(panel, "tasks.previous", JButton.class), next = control(panel, "tasks.next", JButton.class);
            assertEquals(sequence(0, 49), rendered(panel)); assertFalse(previous.isEnabled()); assertTrue(next.isEnabled());
            next.doClick(0); assertEquals(sequence(50, 99), rendered(panel)); assertTrue(previous.isEnabled());
            next.doClick(0); assertEquals(sequence(100, 120), rendered(panel)); assertFalse(next.isEnabled());
            assertTrue(control(panel, "tasks.count", JLabel.class).getText().startsWith("101"));
            assertTrue(control(panel, "tasks.count", JLabel.class).getText().endsWith("/ 121"));
            previous.doClick(0); assertEquals(sequence(50, 99), rendered(panel));
            control(panel, "tasks.search", JTextField.class).setText(" task 120 ");
            assertEquals(List.of(120), rendered(panel)); assertFalse(previous.isEnabled()); assertFalse(next.isEnabled());
            control(panel, "tasks.search", JTextField.class).setText("no matching task anywhere");
            assertTrue(rendered(panel).isEmpty()); assertEquals("0 tasks", control(panel, "tasks.count", JLabel.class).getText());
            assertFalse(previous.isEnabled()); assertFalse(next.isEnabled());
        });
    }

    @Test public void realCatalogueFinalIndexIsSearchableAndSearchAlsoFindsActivityAndDescription() throws Exception
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        SwingUtilities.invokeAndWait(() -> {
            List<TaskAssessment> tasks = new ArrayList<>();
            for (CombatAchievement task : repository.tasks()) tasks.add(wrap(task, EligibilityState.AVAILABLE, true, 1, 1));
            TaskBrowserPanel panel = browser(tasks);
            CombatAchievement finalTask = repository.tasks().get(repository.tasks().size() - 1);
            control(panel, "tasks.search", JTextField.class).setText(finalTask.name().toUpperCase(Locale.ROOT));
            assertTrue("Last canonical task must remain accessible beyond the initial page", rendered(panel).contains(finalTask.id()));
            TaskBrowserPanel fields = browser(List.of(
                assessment(0, "Alpha", "Zulrah", "Mechanical", AchievementTier.EASY, EligibilityState.AVAILABLE, true, 1, 1, 1, "Unique crystal condition"),
                assessment(1, "Beta", "Other boss", "Speed", AchievementTier.HARD, EligibilityState.AVAILABLE, true, 1, 1, 1, "Other description")));
            control(fields, "tasks.search", JTextField.class).setText("ZULRAH"); assertEquals(List.of(0), rendered(fields));
            control(fields, "tasks.search", JTextField.class).setText("CRYSTAL condition"); assertEquals(List.of(0), rendered(fields));
        });
    }

    @Test public void statusTierAndTaskTypeFiltersKeepTheirMeaningAndCompose() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<TaskAssessment> tasks = List.of(
                assessment(0, "A", "Boss", "Mechanical", AchievementTier.EASY, EligibilityState.AVAILABLE, true, 1, 1, 1, ""),
                assessment(1, "B", "Boss", "Speed", AchievementTier.HARD, EligibilityState.SKILL_LOCKED, false, 1, 1, 1, ""),
                assessment(2, "C", "Boss", "Mechanical", AchievementTier.MEDIUM, EligibilityState.CONTENT_EXCLUDED, false, 1, 1, 1, ""),
                assessment(3, "D", "Boss", "Speed", AchievementTier.MASTER, EligibilityState.DEFERRED, false, 1, 1, 1, ""),
                assessment(4, "E", "Boss", "Speed", AchievementTier.GRANDMASTER, EligibilityState.COMPLETED, false, 1, 1, 1, ""),
                assessment(5, "F", "Boss", "Mechanical", AchievementTier.ELITE, EligibilityState.UNKNOWN_REQUIREMENT, true, 1, 1, 1, ""));
            TaskBrowserPanel panel = browser(tasks); JComboBox<?> status = control(panel, "tasks.status", JComboBox.class);
            assertEquals("Recommended now", status.getSelectedItem()); assertEquals(List.of(0, 5), rendered(panel));
            status.setSelectedItem("Remaining tasks"); assertEquals(List.of(0, 1, 2, 3, 5), rendered(panel));
            status.setSelectedItem("Recommended now"); assertEquals(List.of(0, 5), rendered(panel));
            status.setSelectedItem("Needs preparation"); assertEquals(List.of(1), rendered(panel));
            status.setSelectedItem("Excluded / deferred"); assertEquals(List.of(2, 3), rendered(panel));
            status.setSelectedItem("Completed"); assertEquals(List.of(4), rendered(panel));
            status.setSelectedItem("All tasks"); assertEquals(List.of(0, 1, 2, 3, 4, 5), rendered(panel));
            control(panel, "tasks.tier", JComboBox.class).setSelectedItem("Hard");
            control(panel, "tasks.type", JComboBox.class).setSelectedItem("Speed");
            assertEquals(List.of(1), rendered(panel));
            control(panel, "tasks.type", JComboBox.class).setSelectedItem("Mechanical"); assertTrue(rendered(panel).isEmpty());
        });
    }

    @Test public void sortModesUseCorrectSignalsAndStableNameThenIdTies() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            TaskBrowserPanel panel = browser(List.of(
                assessment(3, "Alpha", "A", "Mechanical", AchievementTier.HARD, EligibilityState.AVAILABLE, true, 3, 6, 2, ""),
                assessment(0, "Zeta", "A", "Mechanical", AchievementTier.EASY, EligibilityState.AVAILABLE, true, 8, 100, 9, ""),
                assessment(2, "Beta", "A", "Mechanical", AchievementTier.MEDIUM, EligibilityState.AVAILABLE, true, 5, 8, 1, ""),
                assessment(1, "Alpha", "B", "Mechanical", AchievementTier.GRANDMASTER, EligibilityState.AVAILABLE, true, 3, 12, 2, "")));
            JComboBox<?> sort = control(panel, "tasks.sort", JComboBox.class);
            assertEquals(List.of(3, 2, 0, 1), rendered(panel));
            sort.setSelectedItem("Name"); assertEquals(List.of(1, 3, 2, 0), rendered(panel));
            sort.setSelectedItem("Tier"); assertEquals(List.of(0, 2, 3, 1), rendered(panel));
            sort.setSelectedItem("Estimated time"); assertEquals(List.of(1, 3, 2, 0), rendered(panel));
            sort.setSelectedItem("Planning cost per point"); assertEquals(List.of(1, 3, 2, 0), rendered(panel));
            sort.setSelectedItem("Difficulty"); assertEquals(List.of(2, 1, 3, 0), rendered(panel));
        });
    }

    @Test public void primaryFiltersStayVisibleAndFitNarrowSidebarsWhileTaskTypeIsOptional() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{194, 225, 280})
            {
                TaskBrowserPanel panel = browser(List.of(
                    assessment(0, "A", "Boss", "Mechanical", AchievementTier.EASY, EligibilityState.AVAILABLE, true, 1, 1, 1, ""),
                    assessment(1, "B", "Boss", "Speed", AchievementTier.HARD, EligibilityState.SKILL_LOCKED, false, 1, 1, 1, "")));
                control(panel, "tasks.status", JComboBox.class).setSelectedItem("Remaining tasks");
                JPanel sidebar = new JPanel(new BorderLayout()); sidebar.add(panel, BorderLayout.NORTH); sidebar.setSize(width, 500);
                for (int pass = 0; pass < 3; pass++) layout(sidebar);
                JComponent extra = control(panel, "details.tasks.filters", JComponent.class);
                assertFalse("The optional task type should start collapsed", extra.isVisible());
                for (String name : List.of("tasks.status", "tasks.tier", "tasks.sort"))
                {
                    JComboBox<?> choice = control(panel, name, JComboBox.class);
                    JLabel label = control(panel, name + ".label", JLabel.class);
                    assertTrue("Primary filters remain visible at " + width + "px", visibleWithin(panel, choice));
                    assertTrue("Filter labels must fit at " + width + "px", label.getWidth() >= label.getFontMetrics(label.getFont()).stringWidth(label.getText()));
                    assertTrue("The dropdown retains usable width at " + width + "px", choice.getWidth() >= 80);
                    assertTrue("Primary filter rows remain compact", choice.getHeight() <= 25 && choice.getHeight() >= 20);
                }
                JComboBox<?> tier = control(panel, "tasks.tier", JComboBox.class); tier.setSelectedItem("Hard");
                assertEquals(List.of(1), rendered(panel));
                assertFalse("Changing a primary filter does not open advanced filters", extra.isVisible());
                control(panel, "collapse.tasks.filters", JButton.class).doClick(0); assertTrue(extra.isVisible());
                for (int pass = 0; pass < 3; pass++) layout(sidebar);
                JComboBox<?> type = control(panel, "tasks.type", JComboBox.class);
                assertTrue(visibleWithin(panel, type)); type.setSelectedItem("Speed"); assertEquals(List.of(1), rendered(panel));
                assertEquals("Speed", type.getToolTipText());
                control(panel, "collapse.tasks.filters", JButton.class).doClick(0); assertFalse(extra.isVisible());
                assertEquals("The hidden task type remains applied", List.of(1), rendered(panel));
                assertTrue(visibleWithin(panel, control(panel, "tasks.search", JTextField.class)));
                JComponent section = control(panel, "tasks.results.heading", JComponent.class);
                for (int pass = 0; pass < 3; pass++) layout(sidebar);
                assertEquals("The results use a compact section bar", 25, section.getHeight());
            }
        });
    }

    @Test public void compactChoiceLabelsKeepOriginalFilterValuesAndTooltips() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            TaskBrowserPanel panel = browser(List.of(
                assessment(0, "A", "Boss", "Mechanical", AchievementTier.EASY, EligibilityState.AVAILABLE, true, 1, 1, 1, ""),
                assessment(1, "B", "Boss", "Speed", AchievementTier.HARD, EligibilityState.SKILL_LOCKED, false, 1, 1, 1, "")));
            JComboBox<?> status = control(panel, "tasks.status", JComboBox.class);
            assertChoiceLabel(status, "Recommended now", "Recommended");
            status.setSelectedItem("Needs preparation"); assertEquals(List.of(1), rendered(panel));
            assertChoiceLabel(status, "Needs preparation", "Preparation");
            status.setSelectedItem("All tasks"); assertEquals(List.of(0, 1), rendered(panel)); assertChoiceLabel(status, "All tasks", "All");
            assertChoiceLabel(control(panel, "tasks.tier", JComboBox.class), "Any tier", "All");
            JComboBox<?> sort = control(panel, "tasks.sort", JComboBox.class); sort.setSelectedItem("Planning cost per point");
            assertChoiceLabel(sort, "Planning cost per point", "Cost / point");
            sort.setSelectedItem("Estimated time"); assertChoiceLabel(sort, "Estimated time", "Time");
        });
    }

    private static TaskBrowserPanel browser(List<TaskAssessment> values)
    { return new TaskBrowserPanel(values, (card, task) -> { card.putClientProperty("test.task.id", task.task().id()); card.add(new JLabel(task.task().name())); }); }
    private static TaskAssessment assessment(int id, String name, String activity, String type, AchievementTier tier,
        EligibilityState state, boolean recommended, double minutes, double cost, int difficulty, String description)
    { return wrap(CombatAchievement.builder(id, name, activity, tier).type(type).difficulty(difficulty).description(description).build(), state, recommended, minutes, cost); }
    private static TaskAssessment wrap(CombatAchievement task, EligibilityState state, boolean recommended, double minutes, double cost)
    { return new TaskAssessment(task, new Eligibility(state, List.of(), Confidence.MEDIUM, recommended, state == EligibilityState.UNKNOWN_REQUIREMENT), minutes, cost, Map.of(), List.of(), Confidence.MEDIUM); }
    private static List<Integer> sequence(int first, int last)
    { List<Integer> values = new ArrayList<>(); for (int id = first; id <= last; id++) values.add(id); return values; }
    private static List<Integer> rendered(Component component)
    { List<Integer> values = new ArrayList<>(); collect(component, values); return values; }
    private static void collect(Component component, List<Integer> values)
    {
        if (component instanceof JComponent && ((JComponent) component).getClientProperty("test.task.id") != null)
            values.add((Integer) ((JComponent) component).getClientProperty("test.task.id"));
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) collect(child, values);
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static boolean visibleWithin(Component root, Component child)
    { for (Component current = child; current != null; current = current.getParent()) { if (!current.isVisible()) return false; if (current == root) return true; } return false; }
    private static void assertChoiceLabel(JComboBox<?> choice, String value, String text)
    {
        assertEquals(value, choice.getSelectedItem()); assertEquals(value, choice.getToolTipText());
        @SuppressWarnings("unchecked") ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) choice.getRenderer();
        Component rendered = renderer.getListCellRendererComponent(new JList<>(), choice.getSelectedItem(), -1, false, false);
        assertTrue(rendered instanceof JLabel); assertEquals(text, ((JLabel) rendered).getText());
        assertEquals(value, ((JLabel) rendered).getToolTipText());
    }
    private static <T extends Component> T control(Component root, String name, Class<T> type)
    { T found = find(root, name, type); assertNotNull("Missing named control " + name, found); return found; }
    private static <T extends Component> T find(Component root, String name, Class<T> type)
    {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        if (root instanceof Container) for (Component child : ((Container) root).getComponents())
        { T found = find(child, name, type); if (found != null) return found; }
        return null;
    }
}

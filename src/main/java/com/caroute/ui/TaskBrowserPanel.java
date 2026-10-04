package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import net.runelite.client.ui.components.IconTextField;
import static com.caroute.ui.PanelStyles.*;

/** Search, explicit filters and pagination keep every task accessible in a narrow sidebar. */
final class TaskBrowserPanel extends JPanel
{
    private static final int PAGE_SIZE = 50;
    private final List<TaskAssessment> assessments;
    private final BiConsumer<JPanel, TaskAssessment> render;
    private final JTextField search;
    private final JComboBox<String> status = new JComboBox<>(new String[]{"Remaining tasks", "Recommended now", "Needs preparation", "Excluded / deferred", "Completed", "All tasks"});
    private final JComboBox<String> tier = new JComboBox<>(new String[]{"Any tier", "Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"});
    private final JComboBox<String> type;
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Activity", "Name", "Tier", "Estimated time", "Planning cost per point", "Difficulty"});
    private final JPanel results = page();
    private final JButton previous = new JButton("Previous"), next = new JButton("Next");
    private final JLabel count = new JLabel();
    private int pageIndex;
    private final CollapsibleSection filters;

    /** Presentation choices only; never holds task facts, components or action callbacks. */
    static final class Snapshot
    {
        private final String query, status, tier, type, sort;
        private final int pageIndex;
        private Snapshot(String query, String status, String tier, String type, String sort, int pageIndex)
        { this.query = query; this.status = status; this.tier = tier; this.type = type; this.sort = sort; this.pageIndex = pageIndex; }
    }

    TaskBrowserPanel(List<TaskAssessment> assessments, BiConsumer<JPanel, TaskAssessment> render)
    { this(assessments, render, new DisclosureState()); }

    TaskBrowserPanel(List<TaskAssessment> assessments, BiConsumer<JPanel, TaskAssessment> render, DisclosureState disclosure)
    { this(assessments, render, disclosure, null); }

    TaskBrowserPanel(List<TaskAssessment> assessments, BiConsumer<JPanel, TaskAssessment> render,
        DisclosureState disclosure, Snapshot snapshot)
    {
        this.assessments = Collections.unmodifiableList(new ArrayList<>(assessments)); this.render = render;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); setAlignmentX(Component.LEFT_ALIGNMENT);
        IconTextField nativeSearch = new IconTextField(); nativeSearch.setIcon(IconTextField.Icon.SEARCH);
        nativeSearch.setBackground(CARD); nativeSearch.setHoverBackgroundColor(HOVER);
        JTextField nativeField = searchField(nativeSearch, nativeSearch.getDocument());
        JComponent searchControl;
        if (nativeField != null) { search = nativeField; searchControl = nativeSearch; }
        else
        {
            // The actual text field must retain its name/document for keyboard use and guarded task rendering.
            search = new JTextField(); JPanel fallback = page(); fallback.setOpaque(false);
            text(fallback, "Search tasks", MUTED, 11, false); fallback.add(search); searchControl = fallback;
        }
        TreeSet<String> types = new TreeSet<>(); assessments.forEach(a -> types.add(a.task().type()));
        List<String> typeChoices = new ArrayList<>(); typeChoices.add("Any task type"); typeChoices.addAll(types);
        type = new JComboBox<>(typeChoices.toArray(new String[0]));
        search.setName("tasks.search"); status.setName("tasks.status"); tier.setName("tasks.tier"); type.setName("tasks.type"); sort.setName("tasks.sort");
        status.setSelectedItem("Recommended now");
        // Restore before attaching listeners: their user-change reset must not erase the saved page.
        if (snapshot != null)
        {
            search.setText(snapshot.query); restoreChoice(status, snapshot.status); restoreChoice(tier, snapshot.tier);
            restoreChoice(type, snapshot.type); restoreChoice(sort, snapshot.sort); pageIndex = snapshot.pageIndex;
        }
        search.setToolTipText("Search task, activity or description");
        searchControl.setToolTipText(search.getToolTipText()); searchControl.setName("tasks.search.container");
        filters = new CollapsibleSection("tasks.filters", "More filters", "", disclosure);
        for (JComponent control : Arrays.asList(search, status, tier, type, sort))
        { compact(control); control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25)); }
        status.setRenderer(compactChoices(Map.of("Remaining tasks", "Remaining", "Recommended now", "Recommended",
            "Needs preparation", "Preparation", "Excluded / deferred", "Excluded", "All tasks", "All")));
        tier.setRenderer(compactChoices(Map.of("Any tier", "All")));
        type.setRenderer(compactChoices(Map.of("Any task type", "All")));
        sort.setRenderer(compactChoices(Map.of("Estimated time", "Time", "Planning cost per point", "Cost / point")));
        if (nativeField != null) nativeSearch.setBackground(CARD);
        searchControl.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchControl.setPreferredSize(new Dimension(220, 30));
        searchControl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JPanel choices = new JPanel(new GridBagLayout()); choices.setOpaque(false);
        choices.setAlignmentX(Component.LEFT_ALIGNMENT); choices.setName("tasks.filters.form");
        formRow(choices, "Filters", status, 0); formRow(choices, "Difficulty", tier, 1); formRow(choices, "Ordering", sort, 2);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        add(choices); add(Box.createVerticalStrut(3));
        JPanel extra = new JPanel(new GridBagLayout()); extra.setOpaque(false);
        extra.setAlignmentX(Component.LEFT_ALIGNMENT); formRow(extra, "Task type", type, 0);
        extra.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25)); filters.body().add(extra);
        add(filters); add(Box.createVerticalStrut(5)); add(searchControl); add(Box.createVerticalStrut(5));
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { reset(); }
            public void removeUpdate(DocumentEvent event) { reset(); }
            public void changedUpdate(DocumentEvent event) { reset(); }
        });
        for (JComboBox<String> control : Arrays.asList(status, tier, type, sort)) control.addActionListener(event -> reset());
        previous.setName("tasks.previous"); next.setName("tasks.next"); count.setName("tasks.count"); count.setForeground(MUTED); compact(previous); compact(next); compact(count);
        previous.addActionListener(event -> { pageIndex--; rebuild(); }); next.addActionListener(event -> { pageIndex++; rebuild(); });
        JPanel navigation = new JPanel(new GridLayout(1, 2, 4, 0)); navigation.setOpaque(false); navigation.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24)); navigation.setPreferredSize(new Dimension(220, 24));
        navigation.add(previous); navigation.add(next); add(navigation); add(Box.createVerticalStrut(5));
        JPanel section = new JPanel(new BorderLayout(4, 0)); section.setBackground(CARD); section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setName("tasks.results.heading"); section.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        JLabel title = new JLabel("Tasks"); compact(title); title.setForeground(TEXT); section.add(title, BorderLayout.WEST);
        count.setHorizontalAlignment(SwingConstants.RIGHT); section.add(count, BorderLayout.CENTER);
        section.setPreferredSize(new Dimension(220, 25)); section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        add(section); add(results); rebuild();
    }
    Snapshot snapshot()
    { return new Snapshot(search.getText(), selected(status), selected(tier), selected(type), selected(sort), pageIndex); }
    private static String selected(JComboBox<String> picker)
    { return picker.getSelectedItem() == null ? "" : (String) picker.getSelectedItem(); }
    private static void restoreChoice(JComboBox<String> picker, String choice)
    {
        for (int index = 0; index < picker.getItemCount(); index++)
            if (picker.getItemAt(index).equals(choice)) { picker.setSelectedIndex(index); return; }
    }
    private static void formRow(JPanel form, String label, JComboBox<String> control, int row)
    {
        JLabel title = new JLabel(label); compact(title); title.setForeground(TEXT);
        title.setLabelFor(control); title.setName(control.getName() + ".label");
        title.setPreferredSize(new Dimension(84, 24)); title.setMinimumSize(new Dimension(60, 24));
        control.setPreferredSize(new Dimension(128, 24)); control.setMinimumSize(new Dimension(0, 24));
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridy = row;
        constraints.gridx = 0; constraints.weightx = 0; constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL; constraints.insets = new Insets(0, 0, 1, 4); form.add(title, constraints);
        constraints.gridx = 1; constraints.weightx = 1; constraints.insets = new Insets(0, 0, 1, 0); form.add(control, constraints);
        control.setToolTipText(String.valueOf(control.getSelectedItem()));
        control.addActionListener(event -> control.setToolTipText(String.valueOf(control.getSelectedItem())));
    }
    private static ListCellRenderer<Object> compactChoices(Map<String, String> labels)
    {
        return new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                String original = value == null ? "" : value.toString();
                label.setText(labels.getOrDefault(original, original)); label.setToolTipText(original);
                return label;
            }
        };
    }
    private static JTextField searchField(Container parent, Document document)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JTextField && ((JTextField) child).getDocument() == document) return (JTextField) child;
            if (child instanceof Container) { JTextField match = searchField((Container) child, document); if (match != null) return match; }
        }
        return null;
    }
    private void reset() { pageIndex = 0; rebuild(); }
    private void rebuild()
    {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        List<TaskAssessment> filtered = assessments.stream().filter(a -> matchesStatus(a))
            .filter(a -> tier.getSelectedIndex() == 0 || a.task().tier().ordinal() == tier.getSelectedIndex() - 1)
            .filter(a -> type.getSelectedIndex() == 0 || a.task().type().equals(type.getSelectedItem()))
            .filter(a -> (a.task().name() + " " + a.task().activity() + " " + a.task().description()).toLowerCase(Locale.ROOT).contains(query))
            .sorted(order()).collect(Collectors.toList());
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        filters.setSummary(type.getSelectedIndex() == 0 ? "" : "1 active");
        pageIndex = Math.max(0, Math.min(pages - 1, pageIndex));
        int start = pageIndex * PAGE_SIZE, end = Math.min(filtered.size(), start + PAGE_SIZE);
        previous.setEnabled(pageIndex > 0); next.setEnabled(pageIndex + 1 < pages);
        count.setText(filtered.isEmpty() ? "0 tasks" : (start + 1) + "–" + end + " / " + filtered.size());
        results.removeAll();
        for (TaskAssessment assessment : filtered.subList(start, end)) { JPanel card = page(); render.accept(card, assessment); results.add(card); }
        if (filtered.isEmpty()) { JPanel empty = card(); text(empty, "No matching tasks", MUTED, 12, false); put(results, empty); }
        results.revalidate(); results.repaint();
    }
    private boolean matchesStatus(TaskAssessment assessment)
    {
        EligibilityState state = assessment.eligibility().state();
        switch (status.getSelectedIndex())
        {
            case 1: return assessment.eligibility().recommendedNow();
            case 2: return state != EligibilityState.COMPLETED && state != EligibilityState.CONTENT_EXCLUDED && state != EligibilityState.DEFERRED && !assessment.eligibility().recommendedNow();
            case 3: return state == EligibilityState.CONTENT_EXCLUDED || state == EligibilityState.DEFERRED;
            case 4: return state == EligibilityState.COMPLETED;
            case 5: return true;
            default: return state != EligibilityState.COMPLETED;
        }
    }
    private Comparator<TaskAssessment> order()
    {
        Comparator<TaskAssessment> order;
        switch (sort.getSelectedIndex())
        {
            case 1: order = Comparator.comparing(a -> a.task().name()); break;
            case 2: order = Comparator.comparingInt(a -> a.task().tier().ordinal()); break;
            case 3: order = Comparator.comparingDouble(TaskAssessment::estimatedMinutes); break;
            case 4: order = Comparator.comparingDouble(a -> a.planningCost() / a.task().points()); break;
            case 5: order = Comparator.comparingInt(a -> a.task().difficulty()); break;
            default: order = Comparator.comparing(a -> a.task().activity()); break;
        }
        return order.thenComparing(a -> a.task().name()).thenComparingInt(a -> a.task().id());
    }
}

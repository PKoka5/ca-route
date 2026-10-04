package com.caroute.ui;

import com.caroute.domain.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.caroute.ui.PanelStyles.*;

/** Practical preparation for a player, based only on immutable task and account facts. */
final class TaskRequirementsPanel extends JPanel
{
    private enum Fact { MET, UNMET, UNKNOWN }
    private final PlayerProfile profile;
    private final Function<String, String> readable;
    private int nextRow;

    TaskRequirementsPanel(CombatAchievement task, PlayerProfile profile, Function<String, String> readable)
    {
        this.profile = profile; this.readable = Objects.requireNonNull(readable);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); setAlignmentX(Component.LEFT_ALIGNMENT);
        setName("task.requirements." + task.id());
        boolean information = false;
        if (hasLeaves(task.requirement()))
        {
            subsection("Required"); addRequirement(task.requirement()); information = true;
        }
        if (!task.playerPreparation().isEmpty())
        {
            subsection("Equipment & access");
            for (String note : task.playerPreparation()) text(this, note, TEXT, 11, false);
            information = true;
        }
        Map<String, Integer> hardSkills = new TreeMap<>(); Set<String> hardGear = new TreeSet<>();
        collectMandatory(task.requirement(), hardSkills, hardGear);
        List<Map.Entry<String, Integer>> suggestedSkills = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(task.recommendedSkills()).entrySet())
            if (entry.getValue() > hardSkills.getOrDefault(entry.getKey(), 0)) suggestedSkills.add(entry);
        List<Map<String, Integer>> suggestedStyles = new ArrayList<>();
        for (Map<String, Integer> style : task.recommendedAlternatives())
            if (!style.isEmpty() && style.entrySet().stream().anyMatch(entry -> entry.getValue() > hardSkills.getOrDefault(entry.getKey(), 0)))
                suggestedStyles.add(new TreeMap<>(style));
        Set<String> suggestedGear = new TreeSet<>(task.recommendedGear()); suggestedGear.removeAll(hardGear);
        if (!suggestedSkills.isEmpty() || !suggestedStyles.isEmpty() || !suggestedGear.isEmpty())
        {
            subsection("Recommended");
            for (Map.Entry<String, Integer> entry : suggestedSkills) addLeaf(Requirement.skill(entry.getKey(), entry.getValue()));
            for (Map<String, Integer> style : suggestedStyles)
            {
                List<Requirement> choices = new ArrayList<>();
                style.forEach((skill, level) -> choices.add(Requirement.skill(skill, level)));
                Requirement alternatives = Requirement.any(choices.toArray(new Requirement[0]));
                row("Combat style: " + recommendedStyle(style), evaluate(alternatives));
            }
            for (String capability : suggestedGear) addLeaf(Requirement.gear(capability));
            information = true;
        }
        if (!information) text(this, "Check the Wiki guide for entry requirements and equipment.", MUTED, 11, false);
    }

    private void subsection(String title)
    {
        JLabel label = new JLabel(title); label.setForeground(MUTED); label.setFont(font(11, false));
        label.setAlignmentX(Component.LEFT_ALIGNMENT); label.setBorder(new EmptyBorder(nextRow == 0 ? 2 : 6, 0, 2, 0)); add(label);
    }
    private void addRequirement(Requirement requirement)
    {
        if (requirement.kind() == Requirement.Kind.ALL)
        { for (Requirement child : requirement.children()) addRequirement(child); }
        else if (requirement.kind() == Requirement.Kind.ANY)
            row("One of: " + describe(requirement), evaluate(requirement));
        else addLeaf(requirement);
    }
    private void addLeaf(Requirement requirement) { row(describe(requirement), evaluate(requirement)); }

    private String describe(Requirement requirement)
    {
        switch (requirement.kind())
        {
            case SKILL:
                Integer current = profile == null ? null : profile.skills().get(requirement.key());
                return requirement.level() + " " + readable.apply(requirement.key()) + " \u00b7 You: " + (current == null ? "unknown" : current);
            case QUEST:
                QuestStatus actual = profile == null ? null : profile.quests().get(requirement.key());
                String target = requirement.questStatus() == QuestStatus.COMPLETED ? "complete"
                    : requirement.questStatus() == QuestStatus.IN_PROGRESS ? "start" : "not started";
                return readable.apply(requirement.key()) + " - " + target + " \u00b7 You: "
                    + (actual == null ? "check quest progress" : questState(actual));
            case GEAR:
                GearProfile.CapabilityState ownership = profile == null ? GearProfile.CapabilityState.UNKNOWN : profile.gear().capability(requirement.key());
                return readable.apply(requirement.key()) + " \u00b7 " + (ownership == GearProfile.CapabilityState.PRESENT ? "Owned"
                    : ownership == GearProfile.CapabilityState.MISSING ? "Missing" : "Check ownership");
            default:
                List<String> children = new ArrayList<>();
                for (Requirement child : requirement.children()) if (hasLeaves(child)) children.add(describe(child));
                return "(" + String.join(requirement.kind() == Requirement.Kind.ANY ? " or " : " and ", children) + ")";
        }
    }
    private static String questState(QuestStatus value)
    { return value == QuestStatus.COMPLETED ? "complete" : value == QuestStatus.IN_PROGRESS ? "in progress" : "not started"; }

    private String recommendedStyle(Map<String, Integer> style)
    {
        if (new HashSet<>(style.values()).size() == 1)
        {
            List<String> names = new ArrayList<>();
            style.keySet().forEach(skill -> names.add(readable.apply(skill)));
            return style.values().iterator().next() + " " + String.join(" / ", names);
        }
        List<String> choices = new ArrayList<>();
        style.forEach((skill, level) -> choices.add(level + " " + readable.apply(skill)));
        return String.join(" or ", choices);
    }

    private Fact evaluate(Requirement requirement)
    {
        switch (requirement.kind())
        {
            case SKILL:
                Integer actual = profile == null ? null : profile.skills().get(requirement.key());
                return actual == null ? Fact.UNKNOWN : actual >= requirement.level() ? Fact.MET : Fact.UNMET;
            case QUEST:
                QuestStatus quest = profile == null ? null : profile.quests().get(requirement.key());
                return quest == null ? Fact.UNKNOWN : quest.ordinal() >= requirement.questStatus().ordinal() ? Fact.MET : Fact.UNMET;
            case GEAR:
                GearProfile.CapabilityState gear = profile == null ? GearProfile.CapabilityState.UNKNOWN : profile.gear().capability(requirement.key());
                return gear == GearProfile.CapabilityState.PRESENT ? Fact.MET : gear == GearProfile.CapabilityState.MISSING ? Fact.UNMET : Fact.UNKNOWN;
            default:
                boolean alternative = requirement.kind() == Requirement.Kind.ANY, unknown = false;
                for (Requirement child : requirement.children())
                {
                    Fact fact = evaluate(child);
                    if (alternative && fact == Fact.MET) return Fact.MET;
                    if (!alternative && fact == Fact.UNMET) return Fact.UNMET;
                    unknown |= fact == Fact.UNKNOWN;
                }
                return unknown ? Fact.UNKNOWN : alternative ? Fact.UNMET : Fact.MET;
        }
    }
    private static boolean hasLeaves(Requirement requirement)
    {
        if (requirement.kind() != Requirement.Kind.ALL && requirement.kind() != Requirement.Kind.ANY) return true;
        for (Requirement child : requirement.children()) if (hasLeaves(child)) return true;
        return false;
    }
    private static void collectMandatory(Requirement requirement, Map<String, Integer> skills, Set<String> gear)
    {
        switch (requirement.kind())
        {
            case ALL: for (Requirement child : requirement.children()) collectMandatory(child, skills, gear); break;
            case SKILL: skills.merge(requirement.key(), requirement.level(), Math::max); break;
            case GEAR: gear.add(requirement.key()); break;
            default: break; // An alternative is not an individual mandatory requirement.
        }
    }
    private void row(String value, Fact fact)
    {
        String identity = "task.requirement.row." + nextRow++;
        JPanel row = new JPanel(new BorderLayout(4, 0))
        { @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); } };
        row.setName(identity); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color color = fact == Fact.MET ? RouteWidgets.GREEN : fact == Fact.UNMET ? RouteWidgets.RED : GOLD;
        String status = fact == Fact.MET ? "Met" : fact == Fact.UNMET ? "Unmet" : "Check";
        JLabel indicator = new JLabel(new StatusIcon(fact, color)); indicator.setName(identity + ".status");
        indicator.setVerticalAlignment(SwingConstants.TOP); indicator.setBorder(new EmptyBorder(5, 0, 0, 0));
        indicator.setToolTipText(status); indicator.getAccessibleContext().setAccessibleName(status + ": " + value);
        JTextArea content = new WrappedText(value); content.setName(identity + ".text");
        content.setEditable(false); content.setFocusable(false); content.setOpaque(false); content.setForeground(TEXT);
        content.setFont(font(11, false)); content.setBorder(new EmptyBorder(2, 0, 3, 0));
        row.add(indicator, BorderLayout.WEST); row.add(content, BorderLayout.CENTER); add(row);
    }
    private static final class StatusIcon implements Icon
    {
        private final Fact fact; private final Color color;
        StatusIcon(Fact fact, Color color) { this.fact = fact; this.color = color; }
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 10; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.setColor(color); g.setStroke(new BasicStroke(2));
            if (fact == Fact.MET) { g.drawLine(1, 5, 4, 8); g.drawLine(4, 8, 9, 1); }
            else if (fact == Fact.UNMET) { g.drawLine(2, 2, 8, 8); g.drawLine(8, 2, 2, 8); }
            else g.fillOval(2, 2, 6, 6);
            g.dispose();
        }
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}

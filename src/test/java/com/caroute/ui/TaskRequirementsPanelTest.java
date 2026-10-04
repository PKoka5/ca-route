package com.caroute.ui;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.Test;
import static org.junit.Assert.*;

/** Checks player-facing facts and alternatives without reading a live client. */
public class TaskRequirementsPanelTest extends RuneLiteUiTestSupport
{
    @Test public void requiredSkillsRemainVisibleWhenMetAndUnknownLevelsNeverBecomeZero() throws Exception
    {
        CombatAchievement task = task().requiredSkills(Map.of("Slayer", 62)).build();
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel met = panel(task, skills(Map.of("Slayer", 70)));
            assertTrue(text(met).contains("62 Slayer \u00b7 You: 70")); assertStatus(met, "62 Slayer", "Met");
            TaskRequirementsPanel unmet = panel(task, skills(Map.of("Slayer", 55)));
            assertTrue(text(unmet).contains("62 Slayer \u00b7 You: 55")); assertStatus(unmet, "62 Slayer", "Unmet");
            TaskRequirementsPanel unreadable = panel(task, skills(Map.of()));
            assertTrue(text(unreadable).contains("62 Slayer \u00b7 You: unknown")); assertStatus(unreadable, "62 Slayer", "Check");
            TaskRequirementsPanel noProfile = panel(task, null);
            assertTrue(text(noProfile).contains("You: unknown")); assertStatus(noProfile, "62 Slayer", "Check");
            assertFalse(text(noProfile).contains("You: 0"));
        });
    }

    @Test public void nestedAlternativesKeepTheirAndOrMeaningAndAggregateAccountStatus() throws Exception
    {
        CombatAchievement task = task().requirement(Requirement.all(Requirement.skill("Slayer", 62),
            Requirement.any(Requirement.all(Requirement.skill("Attack", 70), Requirement.skill("Strength", 70)),
                Requirement.skill("Magic", 75)))).build();
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel met = panel(task, skills(Map.of("Slayer", 70, "Attack", 75, "Strength", 50, "Magic", 80)));
            String value = text(met);
            assertTrue(value.contains("One of: ((70 Attack"));
            assertTrue(value.contains(" and 70 Strength")); assertTrue(value.contains(" or 75 Magic"));
            assertEquals("Nested alternatives form one requirement row", 2, rows(met).size());
            assertStatus(met, "One of:", "Met");
            TaskRequirementsPanel unmet = panel(task, skills(Map.of("Slayer", 70, "Attack", 75, "Strength", 50, "Magic", 60)));
            assertStatus(unmet, "One of:", "Unmet");
            TaskRequirementsPanel unknown = panel(task, skills(Map.of("Slayer", 70, "Attack", 75, "Strength", 50)));
            assertStatus(unknown, "One of:", "Check");
        });
    }

    @Test public void questProgressAndGearOwnershipAreDescribedWithoutClaimingEquipmentIsWorn() throws Exception
    {
        CombatAchievement task = task().requirement(Requirement.all(Requirement.quest("A useful quest", QuestStatus.COMPLETED),
            Requirement.quest("A started quest", QuestStatus.IN_PROGRESS), Requirement.gear("protective-boots"))).build();
        GearProfile owned = GearProfile.of(Set.of(), Set.of(23037), Set.of("protective-boots"), false);
        PlayerProfile account = PlayerProfile.builder().quests(Map.of("A useful quest", QuestStatus.COMPLETED,
            "A started quest", QuestStatus.NOT_STARTED)).gear(owned).build();
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel panel = panel(task, account);
            assertTrue(text(panel).contains("A useful quest - complete \u00b7 You: complete"));
            assertTrue(text(panel).contains("A started quest - start \u00b7 You: not started"));
            assertStatus(panel, "A useful quest", "Met"); assertStatus(panel, "A started quest", "Unmet");
            assertTrue(text(panel).contains("Protective boots \u00b7 Owned")); assertStatus(panel, "Protective boots", "Met");
            assertFalse(text(panel).toLowerCase(Locale.ROOT).contains("equipped"));
            assertFalse(text(panel).toLowerCase(Locale.ROOT).contains("worn"));
            TaskRequirementsPanel unknown = panel(task, null);
            assertTrue(text(unknown).contains("check quest progress")); assertTrue(text(unknown).contains("Check ownership"));
            assertStatus(unknown, "A useful quest", "Check"); assertStatus(unknown, "Protective boots", "Check");
            PlayerProfile missing = PlayerProfile.builder().gear(GearProfile.of(Set.of(), Set.of(), Set.of(),
                Set.of("protective-boots"), false)).build();
            TaskRequirementsPanel absent = panel(task, missing);
            assertTrue(text(absent).contains("Protective boots \u00b7 Missing")); assertStatus(absent, "Protective boots", "Unmet");
        });
    }

    @Test public void recommendationsStaySeparateAndCombatStylesRemainAlternatives() throws Exception
    {
        CombatAchievement task = task().requiredSkills(Map.of("Slayer", 62)).minimumGear(Set.of("protective-boots"))
            .recommendedSkills(Map.of("Slayer", 60, "Prayer", 43)).recommendedGear(Set.of("protective-boots", "ranged-weapon"))
            .recommendedAlternatives(List.of(Map.of("Ranged", 70, "Magic", 70))).build();
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel panel = panel(task, skills(Map.of("Slayer", 70, "Prayer", 43, "Ranged", 75, "Magic", 60)));
            String value = text(panel); assertTrue(value.contains("Required")); assertTrue(value.contains("Recommended"));
            assertTrue(value.contains("Combat style: 70 Magic / Ranged"));
            assertStatus(panel, "Combat style:", "Met");
            assertEquals("A lower suggested Slayer level adds no duplicate", 1, occurrences(value, "Slayer"));
            assertEquals("Required boots need no repeated suggestion", 1, occurrences(value, "Protective boots"));
            assertTrue(value.contains("ranged-weapon"));
        });
    }

    @Test public void sourcedEquipmentNotesStayNeutralAndDoNotInventOwnershipChecks() throws Exception
    {
        CombatAchievement task = task().playerPreparation(List.of("Wear boots of stone, boots of brimstone or granite boots.",
            "The Elite Kourend & Kebos Diary removes the dungeon heat protection requirement.")).build();
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel panel = panel(task, null);
            assertTrue(text(panel).contains("Equipment & access")); assertTrue(text(panel).contains("boots of stone"));
            assertTrue(text(panel).contains("Elite Kourend & Kebos Diary"));
            assertTrue("Notes are advice without an invented verified state", rows(panel).isEmpty());
            assertFalse(text(panel).contains("Missing")); assertFalse(text(panel).contains("Owned"));
        });
    }

    @Test public void bundledWyrmShowsItsLevelAndProtectiveBootsInsteadOfEngineDiagnostics() throws Exception
    {
        CombatAchievement wyrm = AchievementRepository.loadBundled().tasks().stream().filter(task -> task.id() == 287).findFirst().orElseThrow();
        assertEquals("Wyrm", wyrm.activity());
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel panel = panel(wyrm, skills(Map.of("Slayer", 70, "Prayer", 70, "Defence", 75, "Ranged", 80)));
            String value = text(panel);
            assertTrue(value.contains("62 Slayer")); assertTrue(value.toLowerCase(Locale.ROOT).contains("boots of stone"));
            assertStatus(panel, "62 Slayer", "Met");
            assertFalse(value.contains("Access requirements are not completely verified"));
            assertFalse(value.contains("KC is unknown")); assertFalse(value.contains("heuristic estimates"));
            assertFalse(value.contains("no observed progress"));
        });
    }

    @Test public void aTaskWithoutPreparationFactsPointsToItsWikiWithoutPromisingNoRequirements() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            TaskRequirementsPanel panel = panel(task().build(), null);
            assertEquals("Check the Wiki guide for entry requirements and equipment.\n", text(panel));
            assertTrue(rows(panel).isEmpty()); assertFalse(text(panel).contains("No requirements"));
        });
    }

    private static CombatAchievement.Builder task()
    { return CombatAchievement.builder(42, "A test task", "A test activity", AchievementTier.EASY); }
    private static PlayerProfile skills(Map<String, Integer> skills) { return PlayerProfile.builder().skills(skills).build(); }
    private static TaskRequirementsPanel panel(CombatAchievement task, PlayerProfile profile)
    { return new TaskRequirementsPanel(task, profile, value -> value.replace("protective-boots", "Protective boots")); }
    private static int occurrences(String value, String fragment)
    {
        int count = 0, position = 0;
        while ((position = value.indexOf(fragment, position)) >= 0) { count++; position += fragment.length(); }
        return count;
    }
    private static List<JPanel> rows(Container container)
    {
        List<JPanel> result = new ArrayList<>();
        for (Component child : container.getComponents())
        {
            if (child instanceof JPanel && child.getName() != null && child.getName().startsWith("task.requirement.row."))
                result.add((JPanel) child);
            else if (child instanceof Container) result.addAll(rows((Container) child));
        }
        return result;
    }
    private static void assertStatus(TaskRequirementsPanel panel, String fragment, String expected)
    {
        JPanel row = rows(panel).stream().filter(value -> text(value).contains(fragment)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing preparation row: " + fragment));
        JLabel indicator = Arrays.stream(row.getComponents()).filter(value -> value instanceof JLabel)
            .map(value -> (JLabel) value).findFirst().orElseThrow();
        assertEquals(expected, indicator.getToolTipText());
        assertTrue(indicator.getAccessibleContext().getAccessibleName().startsWith(expected + ":"));
    }
    private static String text(Container container)
    {
        StringBuilder value = new StringBuilder();
        for (Component child : container.getComponents())
        {
            if (child instanceof JTextComponent) value.append(((JTextComponent) child).getText()).append('\n');
            else if (child instanceof JLabel && ((JLabel) child).getText() != null) value.append(((JLabel) child).getText()).append('\n');
            else if (child instanceof Container) value.append(text((Container) child));
        }
        return value.toString();
    }
}

package com.caroute.data;

import com.caroute.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AchievementRepositoryTest
{
    @Test public void snapshotHasExplicitIdentityEvidenceAndCorrectExecutionUnits()
    {
        AchievementRepository repository=AchievementRepository.loadBundled();assertEquals(655,repository.tasks().size());
        assertEquals(2697,repository.tasks().stream().mapToInt(CombatAchievement::points).sum());assertFalse(repository.diagnostics().isEmpty());
        for(CombatAchievement task:repository.tasks()) { assertEquals(task.tier().points(),task.points());assertFalse(task.provenance().isEmpty());assertFalse("Imported access metadata is not exhaustive",task.requirementsKnown());assertTrue(task.completionCount()>=1); }
        CombatAchievement rats=repository.tasks().get(525);assertTrue(rats.description().contains("6 Giant Rats"));assertEquals(0,rats.kcThreshold());assertEquals(1,rats.completionCount());
        assertEquals(150,repository.tasks().get(226).kcThreshold());
        assertEquals(100,repository.tasks().get(172).completionCount());assertFalse(repository.tasks().get(172).overlapGroup().isEmpty());
    }
    @Test public void invalidSchemaAndCatalogueIdentityFailLoudly() throws Exception
    {
        String source;
        try(InputStream in=getClass().getResourceAsStream("/com/caroute/achievements.json")) { assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8); }
        for(String invalid:List.of(source.replace("\"schemaVersion\": 1","\"schemaVersion\": 99"),source.replaceFirst("\"id\": 1,","\"id\": 0,")))
        { try { AchievementRepository.load(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8)));fail("Invalid metadata must not disappear silently"); }catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Cannot load"));} }
    }
    @Test public void activityBaselineIsSeparateFromRewardTierAndTaskChallenge()
    {
        List<CombatAchievement> tasks=AchievementRepository.loadBundled().tasks();
        assertEquals(2,tasks.stream().filter(task->task.activity().equals("Scurrius")).findFirst().orElseThrow().activityDifficulty());
        assertEquals(4,tasks.stream().filter(task->task.activity().equals("K'ril Tsutsaroth")).findFirst().orElseThrow().activityDifficulty());
        CombatAchievement barrows=tasks.get(30);assertEquals(AchievementTier.HARD,barrows.tier());assertEquals(2,barrows.activityDifficulty());
        assertTrue(tasks.stream().allMatch(task->task.activityDifficulty()>=1&&task.activityDifficulty()<=10));
    }
    @Test public void practicalWyrmPreparationDoesNotInventAGearOrDiaryLock()
    {
        CombatAchievement wyrm = AchievementRepository.loadBundled().tasks().get(287);
        assertEquals("A Slithery Encounter", wyrm.name());
        assertEquals(Integer.valueOf(62), wyrm.requiredSkills().get("Slayer"));
        assertTrue(wyrm.playerPreparation().stream().anyMatch(note -> note.contains("boots of stone")));
        assertTrue(wyrm.playerPreparation().stream().anyMatch(note -> note.contains("elite Kourend & Kebos Diary")));
        assertTrue("Protection has a diary exemption, so notes do not add an unconditional gear lock", wyrm.minimumGear().isEmpty());
        assertFalse("A few practical reminders do not prove exhaustive access", wyrm.requirementsKnown());
        try { wyrm.playerPreparation().add("Mutable notes"); fail("Preparation notes must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test public void allHydraTasksUseTheVerifiedNinetyFiveSlayerRequirement()
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        List<CombatAchievement> hydra = new ArrayList<>();
        for (CombatAchievement task : tasks) if (task.activity().equals("Alchemical Hydra")) hydra.add(task);
        assertEquals(12, hydra.size());
        for (CombatAchievement task : hydra)
        {
            assertEquals(task.name(), Integer.valueOf(95), task.requiredSkills().get("Slayer"));
            assertFalse("Correcting one level does not verify every access requirement", task.requirementsKnown());
            assertTrue("An assignment is player advice, not an invented checked profile fact", task.playerPreparation()
                .stream().anyMatch(note -> note.toLowerCase(Locale.ROOT).contains("assignment")));
        }
    }
    @Test public void everyTaskLoadsACompactStandaloneGuideWithoutChangingItsAccessState()
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        assertEquals(655, tasks.size());
        for (CombatAchievement task : tasks)
        {
            List<String> steps = task.quickGuide();
            assertTrue(task.name() + " needs a useful short guide", steps.size() >= 2 && steps.size() <= 4);
            int words = 0;
            for (String step : steps)
            {
                assertFalse(task.name(), step.trim().isEmpty());
                assertTrue(task.name() + " must fit the sidebar", step.length() <= 350);
                words += step.trim().split("\\s+").length;
            }
            assertTrue(task.name() + " must stay compact", words <= 100);
            assertFalse("Reading a guide does not verify account access", task.requirementsKnown());
        }
        List<String> guide = tasks.get(287).quickGuide();
        try { guide.add("Alter catalogue text"); fail("Bundled instructions must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
}

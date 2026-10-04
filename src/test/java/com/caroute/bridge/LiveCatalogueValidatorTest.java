package com.caroute.bridge;

import com.caroute.data.AchievementRepository;
import com.caroute.domain.AchievementTier;
import com.caroute.domain.CombatAchievement;
import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveCatalogueValidatorTest
{
    private final LiveCatalogueValidator validator = new LiveCatalogueValidator();

    @Test public void validatesEveryBundledIdentityAndKeepsStructIdsSeparateFromCompletionIndices()
    {
        List<CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        FakeSource source = new FakeSource(tasks);
        for (List<Integer> rows : source.enums.values()) Collections.reverse(rows);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertEquals(LiveCatalogueValidator.Status.VERIFIED, result.status());
        assertFalse(result.retryable());
        assertTrue(result.snapshotSafe()); assertTrue(result.identityComplete()); assertTrue(result.catalogueComplete());
        assertEquals(655, result.liveTaskCount()); assertEquals(655, result.verifiedStructIds().size());
        assertEquals(Integer.valueOf(10640), result.verifiedStructIds().get(640));
        assertEquals(Integer.valueOf(2697), result.thresholds().get(AchievementTier.GRANDMASTER));
        try { result.verifiedStructIds().put(0, 2); fail("Mapping must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void addedLiveTaskAllowsKnownSubsetButBlocksFullCoverageEvenAtUnchangedAccountPoints()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.add(20000, new LiveCatalogueValidator.Identity(6, "New easy task", 1), AchievementTier.EASY);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertTrue(result.snapshotSafe()); assertFalse(result.identityComplete()); assertFalse(result.catalogueComplete());
        assertEquals(LiveCatalogueValidator.Status.DRIFT, result.status()); assertEquals(6, result.verifiedStructIds().size());
        assertFalse(result.retryable());
        assertTrue(result.diagnostics().stream().anyMatch(s -> s.contains("absent from the bundled catalogue")));
    }

    @Test public void reusedIdWithSamePointTotalSuppressesRouting()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.rows.put(10000, new LiveCatalogueValidator.Identity(0, "Replacement easy task", 1));
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertEquals(Integer.valueOf(21), result.thresholds().get(AchievementTier.GRANDMASTER));
        assertFalse(result.snapshotSafe()); assertFalse(result.catalogueComplete()); assertTrue(result.verifiedStructIds().isEmpty());
        assertEquals(LiveCatalogueValidator.Status.DRIFT, result.status());
    }

    @Test public void missingBundledTaskAndDuplicateCompletionIndexAreUnsafe()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        // Keep the tier enum nonempty, but replace its only expected ID with a new one.
        source.rows.put(10000, new LiveCatalogueValidator.Identity(8, "Other easy task", 1));
        assertFalse(validator.validate(source, tasks, 672).snapshotSafe());
        source = new FakeSource(tasks);
        source.add(20000, new LiveCatalogueValidator.Identity(0, "Task 0", 1), AchievementTier.EASY);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertFalse(result.snapshotSafe()); assertTrue(result.verifiedStructIds().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(s -> s.contains("reused by multiple structs")));
    }

    @Test public void changedTierOrContradictoryTierParameterIsUnsafe()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.rows.put(10000, new LiveCatalogueValidator.Identity(0, "Task 0", 2));
        assertFalse(validator.validate(source, tasks, 672).snapshotSafe());
        source.enums.get(AchievementTier.EASY).remove(Integer.valueOf(10000));
        source.enums.get(AchievementTier.MEDIUM).add(10000);
        source.add(20000, new LiveCatalogueValidator.Identity(8, "Other easy task", 1), AchievementTier.EASY);
        assertFalse(validator.validate(source, tasks, 672).snapshotSafe());
    }

    @Test public void cacheReadFailuresWaitAndDoNotAuthorizeCompletions()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.rows.remove(10000);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertEquals(LiveCatalogueValidator.Status.UNAVAILABLE, result.status()); assertFalse(result.snapshotSafe());
        source = new FakeSource(tasks);
        for (AchievementTier tier : AchievementTier.values()) source.thresholdOverrides.put(tier, source.threshold(tier));
        source.enums.remove(AchievementTier.HARD);
        result = validator.validate(source, tasks, 672);
        assertEquals(LiveCatalogueValidator.Status.UNAVAILABLE, result.status()); assertFalse(result.snapshotSafe());
    }

    @Test public void unavailableThresholdsKeepVerifiedSubsetAndDoNotClaimGrandmasterCoverage()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.thresholdOverrides.put(AchievementTier.GRANDMASTER, 0);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertTrue(result.snapshotSafe()); assertTrue(result.identityComplete()); assertFalse(result.catalogueComplete());
        assertEquals(LiveCatalogueValidator.Status.UNAVAILABLE, result.status());
        assertTrue(result.retryable());
        assertFalse(result.thresholds().containsKey(AchievementTier.GRANDMASTER));
        source.thresholdOverrides.put(AchievementTier.GRANDMASTER, 20);
        result = validator.validate(source, tasks, 672);
        assertTrue(result.snapshotSafe()); assertFalse(result.catalogueComplete());
        assertEquals(LiveCatalogueValidator.Status.DRIFT, result.status());
        assertFalse(result.retryable());
    }

    @Test public void mixedUnavailableAndDriftingFactsStillRetryAfterLoginSynchronization()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        for (AchievementTier tier : AchievementTier.values()) source.thresholdOverrides.put(tier, source.threshold(tier));
        source.enums.remove(AchievementTier.HARD);
        source.thresholdOverrides.put(AchievementTier.MEDIUM, 999);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertEquals(LiveCatalogueValidator.Status.DRIFT, result.status());
        assertTrue(result.retryable()); assertFalse(result.snapshotSafe()); assertFalse(result.catalogueComplete());
    }

    @Test public void changedTierWeightsAreUnsafeAndMissingWeightsRemainExplicitlyUnknown()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.weights.put(6, 7);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertTrue(result.identityComplete()); assertFalse(result.snapshotSafe()); assertFalse(result.catalogueComplete());
        source.weights.clear(); result = validator.validate(source, tasks, 672);
        assertTrue(result.snapshotSafe()); assertFalse(result.catalogueComplete());
        assertEquals(LiveCatalogueValidator.Status.UNAVAILABLE, result.status());
    }

    @Test public void newTasksBeyondSupportedCompletionCapacityCannotMakeCatalogueComplete()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.add(20000, new LiveCatalogueValidator.Identity(672, "Beyond supported words", 6), AchievementTier.GRANDMASTER);
        LiveCatalogueValidator.Result result = validator.validate(source, tasks, 672);
        assertTrue(result.snapshotSafe()); assertFalse(result.catalogueComplete());
        assertTrue(result.diagnostics().stream().anyMatch(s -> s.contains("completion-word capacity")));
    }

    @Test public void namesIgnoreOnlyFormattingAndSpacingNotSemanticChanges()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        source.rows.put(10000, new LiveCatalogueValidator.Identity(0, " <col=ffff00>Task</col>\u00a0 0 ", 1));
        assertTrue(validator.validate(source, tasks, 672).catalogueComplete());
        source.rows.put(10000, new LiveCatalogueValidator.Identity(0, "task 0", 1));
        assertFalse(validator.validate(source, tasks, 672).snapshotSafe());
    }

    @Test public void clientAdapterReadsRealParamsAndEnumValuesRatherThanAssumingEnumKeysAreTaskIndices()
    {
        List<CombatAchievement> tasks = fixture(); FakeSource source = new FakeSource(tasks);
        Client client = client(source, true, GameState.LOGGED_IN);
        assertTrue(validator.validate(client, tasks, 672).catalogueComplete());
        assertFalse(validator.validate(client(source, true, GameState.LOGIN_SCREEN), tasks, 672).snapshotSafe());
        try { validator.validate(client(source, false, GameState.LOGGED_IN), tasks, 672); fail("Require client thread"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("client thread")); }
    }

    private static List<CombatAchievement> fixture()
    {
        List<CombatAchievement> tasks = new ArrayList<>();
        for (AchievementTier tier : AchievementTier.values())
            tasks.add(CombatAchievement.builder(tier.ordinal(), "Task " + tier.ordinal(), "Example", tier).build());
        return tasks;
    }

    private static final class FakeSource implements LiveCatalogueValidator.Source
    {
        final Map<AchievementTier, List<Integer>> enums = new EnumMap<>(AchievementTier.class);
        final Map<Integer, LiveCatalogueValidator.Identity> rows = new HashMap<>();
        final Map<Integer, Integer> weights = new TreeMap<>();
        final Map<AchievementTier, Integer> thresholdOverrides = new EnumMap<>(AchievementTier.class);
        FakeSource(List<CombatAchievement> tasks)
        {
            for (AchievementTier tier : AchievementTier.values()) { enums.put(tier, new ArrayList<>()); weights.put(tier.points(), tier.points()); }
            for (CombatAchievement task : tasks)
                add(10000 + task.id(), new LiveCatalogueValidator.Identity(task.id(), task.name(), task.tier().points()), task.tier());
        }
        void add(int struct, LiveCatalogueValidator.Identity identity, AchievementTier tier)
        { enums.get(tier).add(struct); rows.put(struct, identity); }
        @Override public List<Integer> taskStructIds(AchievementTier tier) { return enums.get(tier); }
        @Override public LiveCatalogueValidator.Identity taskIdentity(int structId) { return rows.get(structId); }
        @Override public Map<Integer, Integer> tierPoints() { return weights; }
        @Override public int threshold(AchievementTier target)
        {
            if (thresholdOverrides.containsKey(target)) return thresholdOverrides.get(target);
            int points = 0;
            for (AchievementTier tier : AchievementTier.values())
            { points += enums.getOrDefault(tier, Collections.emptyList()).size() * tier.points(); if (tier == target) return points; }
            throw new AssertionError();
        }
    }

    private static Client client(FakeSource source, boolean onThread, GameState state)
    {
        int[] thresholdIds = {VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
            VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER};
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) -> {
            switch (method.getName())
            {
                case "isClientThread": return onThread;
                case "getGameState": return state;
                case "getVarbitValue":
                    for (AchievementTier tier : AchievementTier.values()) if ((int) args[0] == thresholdIds[tier.ordinal()]) return source.threshold(tier);
                    throw new AssertionError("Unexpected varbit " + args[0]);
                case "getEnum":
                    int id = (int) args[0];
                    if (id == LiveCatalogueValidator.TIER_POINTS_ENUM) return enumeration(new ArrayList<>(source.weights.keySet()), new ArrayList<>(source.weights.values()));
                    for (AchievementTier tier : AchievementTier.values()) if (id == LiveCatalogueValidator.TASK_ENUMS[tier.ordinal()])
                        return enumeration(Collections.singletonList(765 + tier.ordinal()), source.taskStructIds(tier));
                    throw new AssertionError("Unexpected enum " + id);
                case "getStructComposition":
                    int structId = (int) args[0]; LiveCatalogueValidator.Identity row = source.taskIdentity(structId);
                    return (StructComposition) Proxy.newProxyInstance(StructComposition.class.getClassLoader(), new Class<?>[]{StructComposition.class}, (p, m, a) -> {
                        if (m.getName().equals("getId")) return structId;
                        if (m.getName().equals("getStringValue") && (int) a[0] == LiveCatalogueValidator.NAME_PARAM) return row.name();
                        if (m.getName().equals("getIntValue") && (int) a[0] == LiveCatalogueValidator.COMPLETION_INDEX_PARAM) return row.completionIndex();
                        if (m.getName().equals("getIntValue") && (int) a[0] == LiveCatalogueValidator.TIER_PARAM) return row.tier();
                        throw new AssertionError("Unexpected struct API " + m);
                    });
                default: throw new AssertionError("Unexpected client API " + method);
            }
        });
    }

    private static EnumComposition enumeration(List<Integer> keys, List<Integer> values)
    {
        return (EnumComposition) Proxy.newProxyInstance(EnumComposition.class.getClassLoader(), new Class<?>[]{EnumComposition.class}, (proxy, method, args) -> {
            switch (method.getName())
            {
                case "size": return values.size();
                case "getKeys": return keys.stream().mapToInt(Integer::intValue).toArray();
                case "getIntVals": return values.stream().mapToInt(Integer::intValue).toArray();
                default: throw new AssertionError("Unexpected enum API " + method);
            }
        });
    }
}

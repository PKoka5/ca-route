package com.caroute.bridge;

import com.caroute.domain.AchievementTier;
import com.caroute.domain.CombatAchievement;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarbitID;

/**
 * Validates bundled completion indices against the logged-in client's cache.
 * Numeric cache definitions are revision-specific, not guaranteed RuneLite API constants.
 * See docs/LIVE_CATALOGUE.md for evidence and the fail-closed policy.
 */
public final class LiveCatalogueValidator
{
    // Numeric definitions from inspected tracker sources; API compiled against RuneLite 1.13.1.
    static final int[] TASK_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};
    static final int COMPLETION_INDEX_PARAM = 1306;
    static final int NAME_PARAM = 1308;
    static final int TIER_PARAM = 1310;
    static final int TIER_POINTS_ENUM = 4846;
    private static final int[] THRESHOLD_VARBITS = {
        VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
        VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER
    };

    public enum Status { VERIFIED, UNAVAILABLE, DRIFT }

    /** Pure source boundary for deterministic headless tests. No source may use the network. */
    public interface Source
    {
        List<Integer> taskStructIds(AchievementTier tier);
        Identity taskIdentity(int structId);
        Map<Integer, Integer> tierPoints();
        int threshold(AchievementTier tier);
    }

    public static final class Identity
    {
        private final int completionIndex;
        private final String name;
        private final int tier;
        public Identity(int completionIndex, String name, int tier)
        { this.completionIndex = completionIndex; this.name = name; this.tier = tier; }
        public int completionIndex() { return completionIndex; }
        public String name() { return name; }
        public int tier() { return tier; }
    }

    public static final class Result
    {
        private final Status status;
        private final boolean snapshotSafe, identityComplete, catalogueComplete, retryable;
        private final Map<Integer, Integer> verifiedStructIds;
        private final Map<AchievementTier, Integer> thresholds;
        private final List<String> diagnostics;
        private final int liveTaskCount;
        private Result(Status status, boolean snapshotSafe, boolean identityComplete, boolean catalogueComplete, boolean retryable,
            Map<Integer, Integer> verifiedStructIds, Map<AchievementTier, Integer> thresholds,
            List<String> diagnostics, int liveTaskCount)
        {
            this.status = status; this.snapshotSafe = snapshotSafe;
            this.identityComplete = identityComplete; this.catalogueComplete = catalogueComplete;
            this.retryable = retryable;
            this.verifiedStructIds = Collections.unmodifiableMap(new TreeMap<>(verifiedStructIds));
            EnumMap<AchievementTier, Integer> copy = new EnumMap<>(AchievementTier.class);
            copy.putAll(thresholds); this.thresholds = Collections.unmodifiableMap(copy);
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
            this.liveTaskCount = liveTaskCount;
        }
        public Status status() { return status; }
        /** Any unavailable source fact may synchronize later, even when other facts show drift. */
        public boolean retryable() { return retryable; }
        /** All bundled IDs still refer to the same live tasks; additions can coexist with this. */
        public boolean snapshotSafe() { return snapshotSafe; }
        /** Full identity coverage in both directions, independent of numerical threshold readiness. */
        public boolean identityComplete() { return identityComplete; }
        /** Full identity coverage, supported tier weights, and matching live numerical thresholds. */
        public boolean catalogueComplete() { return catalogueComplete; }
        /** Verified bundled bit-index -> struct-ID mapping, empty when the snapshot is unsafe. */
        public Map<Integer, Integer> verifiedStructIds() { return verifiedStructIds; }
        /** Positive live values only. Missing values must use a labelled fallback in the caller. */
        public Map<AchievementTier, Integer> thresholds() { return thresholds; }
        public List<String> diagnostics() { return diagnostics; }
        public int liveTaskCount() { return liveTaskCount; }
    }

    /** Must run on the client thread, after login. Cache the result only for the current login. */
    public Result validate(Client client, List<CombatAchievement> bundled, int completionCapacity)
    {
        if (!client.isClientThread()) throw new IllegalStateException("CA catalogue must be read on the client thread");
        if (client.getGameState() != GameState.LOGGED_IN)
            return unavailable("Waiting for login before validating the live CA catalogue");
        return validate(new ClientSource(client), bundled, completionCapacity);
    }

    public Result validate(Source source, List<CombatAchievement> bundled, int completionCapacity)
    {
        Objects.requireNonNull(source); Objects.requireNonNull(bundled);
        if (completionCapacity < 1) throw new IllegalArgumentException("Completion capacity must be positive");
        List<String> diagnostics = new ArrayList<>();
        Map<Integer, CombatAchievement> expected = new TreeMap<>();
        boolean expectedDrift = false, drift = false, unavailable = false, identityUnavailable = false;
        for (CombatAchievement task : bundled)
        {
            if (expected.put(task.id(), task) != null || task.id() < 0 || task.id() >= completionCapacity)
            { diagnostics.add("Invalid bundled CA completion index " + task.id()); expectedDrift = true; drift = true; }
        }
        if (expected.isEmpty())
        { diagnostics.add("Bundled CA catalogue is empty"); expectedDrift = true; drift = true; }
        Map<Integer, Integer> matched = new TreeMap<>();
        Set<Integer> seenStructs = new HashSet<>(), seenIndices = new HashSet<>();
        EnumMap<AchievementTier, Integer> liveCounts = new EnumMap<>(AchievementTier.class);
        int liveCount = 0, extraCount = 0;
        boolean liveTableWellFormed = true;
        for (AchievementTier tier : AchievementTier.values())
        {
            List<Integer> structs;
            try
            {
                structs = source.taskStructIds(tier);
                if (structs == null || structs.isEmpty()) throw new IllegalStateException("Empty task enum");
            }
            catch (RuntimeException ex)
            {
                diagnostics.add("Live " + tier.displayName() + " CA task enum is unavailable; retry after synchronization");
                unavailable = true; identityUnavailable = true; liveTableWellFormed = false; continue;
            }
            liveCounts.put(tier, structs.size());
            for (Integer structId : structs)
            {
                liveCount++;
                if (structId == null || structId < 0 || !seenStructs.add(structId))
                {
                    diagnostics.add("Invalid or duplicate live CA struct " + structId);
                    drift = true; expectedDrift = true; liveTableWellFormed = false; continue;
                }
                Identity row;
                try
                {
                    row = source.taskIdentity(structId);
                    if (row == null || normalizeName(row.name()).isEmpty()) throw new IllegalStateException("Missing task identity");
                }
                catch (RuntimeException ex)
                {
                    diagnostics.add("Live CA struct " + structId + " is unavailable; retry after synchronization");
                    unavailable = true; identityUnavailable = true; liveTableWellFormed = false; continue;
                }
                int id = row.completionIndex();
                CombatAchievement task = expected.get(id);
                if (id < 0 || id >= completionCapacity)
                {
                    diagnostics.add("Live CA " + id + " exceeds supported completion-word capacity " + completionCapacity);
                    drift = true; liveTableWellFormed = false;
                }
                if (!seenIndices.add(id))
                {
                    diagnostics.add("Live CA completion index " + id + " is reused by multiple structs");
                    drift = true; liveTableWellFormed = false;
                    if (task != null) { expectedDrift = true; matched.remove(id); }
                    continue;
                }
                if (row.tier() != tier.points())
                {
                    diagnostics.add("Live CA " + id + " tier parameter disagrees with its task enum");
                    drift = true; liveTableWellFormed = false;
                    if (task != null) expectedDrift = true;
                    continue;
                }
                if (task == null)
                {
                    diagnostics.add("Live CA " + id + " (" + normalizeName(row.name()) + ") is absent from the bundled catalogue");
                    extraCount++; drift = true; continue;
                }
                if (task.tier() != tier || !normalizeName(task.name()).equals(normalizeName(row.name())))
                {
                    diagnostics.add("Bundled CA " + id + " identity changed: expected " + task.name() + " / "
                        + task.tier().displayName() + ", live " + normalizeName(row.name()) + " / " + tier.displayName());
                    expectedDrift = true; drift = true; continue;
                }
                matched.put(id, structId);
            }
        }
        for (int id : expected.keySet()) if (!matched.containsKey(id) && !identityUnavailable)
        {
            diagnostics.add("Bundled CA " + id + " has no verified live identity"); expectedDrift = true; drift = true;
        }

        boolean weightsKnown = true, pointDrift = false;
        try
        {
            Map<Integer, Integer> weights = source.tierPoints();
            if (weights == null || weights.isEmpty()) throw new IllegalStateException("Missing tier points");
            if (weights.size() != AchievementTier.values().length) pointDrift = true;
            for (AchievementTier tier : AchievementTier.values())
                if (!Objects.equals(weights.get(tier.points()), tier.points())) pointDrift = true;
            if (pointDrift) { diagnostics.add("Live CA tier point weights differ from the supported 1–6 model"); drift = true; }
        }
        catch (RuntimeException ex)
        { weightsKnown = false; unavailable = true; diagnostics.add("Live CA tier point enum is unavailable"); }

        Map<AchievementTier, Integer> thresholds = new EnumMap<>(AchievementTier.class);
        boolean thresholdsMatch = true;
        int cumulative = 0, previous = 0;
        for (AchievementTier tier : AchievementTier.values())
        {
            cumulative += liveCounts.getOrDefault(tier, 0) * tier.points();
            int value = 0;
            try { value = source.threshold(tier); } catch (RuntimeException ignored) { }
            if (value <= 0)
            {
                diagnostics.add("Live " + tier.displayName() + " CA threshold is unavailable");
                unavailable = true; thresholdsMatch = false; continue;
            }
            thresholds.put(tier, value);
            if (value <= previous || (liveTableWellFormed && weightsKnown && !pointDrift && value != cumulative))
            {
                diagnostics.add("Live " + tier.displayName() + " CA threshold " + value + " disagrees with the live task catalogue");
                drift = true; thresholdsMatch = false;
            }
            previous = value;
        }
        boolean identitySafe = !identityUnavailable && !expectedDrift && matched.size() == expected.size();
        boolean safe = identitySafe && !pointDrift;
        boolean identityComplete = identitySafe && liveTableWellFormed && extraCount == 0 && liveCount == expected.size();
        boolean complete = identityComplete && weightsKnown && !pointDrift && thresholdsMatch;
        if (complete) diagnostics.add("Live CA catalogue verified: " + liveCount + " task identities, tier weights and thresholds");
        else if (safe) diagnostics.add("Bundled CA identities are verified; full live catalogue coverage is incomplete (Grandmaster remains blocked)");
        Status status = drift ? Status.DRIFT : unavailable ? Status.UNAVAILABLE : Status.VERIFIED;
        return new Result(status, safe, identityComplete, complete, unavailable, safe ? matched : Collections.emptyMap(), thresholds, diagnostics, liveCount);
    }

    /** Strip game formatting tags, normalize spacing; retain case, punctuation and wording. */
    static String normalizeName(String name)
    { return name == null ? "" : name.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }

    private static Result unavailable(String reason)
    { return new Result(Status.UNAVAILABLE, false, false, false, true, Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(reason), 0); }

    private static final class ClientSource implements Source
    {
        private final Client client;
        private ClientSource(Client client) { this.client = client; }
        private EnumComposition enumeration(int enumId)
        {
            EnumComposition value = client.getEnum(enumId);
            if (value == null || value.getKeys() == null || value.getIntVals() == null
                || value.size() != value.getKeys().length || value.getKeys().length != value.getIntVals().length)
                throw new IllegalStateException("Unreadable enum " + enumId);
            return value;
        }
        @Override public List<Integer> taskStructIds(AchievementTier tier)
        {
            EnumComposition value = enumeration(TASK_ENUMS[tier.ordinal()]);
            Set<Integer> keys = new HashSet<>();
            for (int key : value.getKeys()) if (!keys.add(key)) throw new IllegalStateException("Duplicate enum key");
            List<Integer> structs = new ArrayList<>();
            for (int id : value.getIntVals()) structs.add(id);
            return structs;
        }
        @Override public Identity taskIdentity(int structId)
        {
            StructComposition value = client.getStructComposition(structId);
            if (value == null || value.getId() != structId) throw new IllegalStateException("Unreadable struct");
            return new Identity(value.getIntValue(COMPLETION_INDEX_PARAM), value.getStringValue(NAME_PARAM), value.getIntValue(TIER_PARAM));
        }
        @Override public Map<Integer, Integer> tierPoints()
        {
            EnumComposition value = enumeration(TIER_POINTS_ENUM);
            Map<Integer, Integer> weights = new TreeMap<>();
            int[] keys = value.getKeys(), values = value.getIntVals();
            for (int i = 0; i < keys.length; i++)
                if (weights.put(keys[i], values[i]) != null) throw new IllegalStateException("Duplicate tier point key");
            return weights;
        }
        @Override public int threshold(AchievementTier tier)
        { return client.getVarbitValue(THRESHOLD_VARBITS[tier.ordinal()]); }
    }
}

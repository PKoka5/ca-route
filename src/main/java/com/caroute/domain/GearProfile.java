package com.caroute.domain;

import java.util.*;

public final class GearProfile
{
    /** Ownership evidence for one capability, independent of coverage for other gear. */
    public enum CapabilityState { PRESENT, MISSING, UNKNOWN }
    private final Set<Integer> equipped, owned;
    private final Set<String> capabilities, knownMissing;
    private final boolean ownershipComplete;
    private final Map<String, Double> activePreparationEstimates;
    private GearProfile(Set<Integer> equipped, Set<Integer> owned, Set<String> capabilities, Set<String> knownMissing, boolean ownershipComplete)
    { this(equipped, owned, capabilities, knownMissing, ownershipComplete, Collections.emptyMap()); }
    private GearProfile(Set<Integer> equipped, Set<Integer> owned, Set<String> capabilities, Set<String> knownMissing, boolean ownershipComplete, Map<String, Double> estimates)
    {
        this.equipped = Immutable.set(equipped); this.owned = Immutable.set(owned);
        this.capabilities = Immutable.set(capabilities);
        Set<String> missing = new TreeSet<>(knownMissing); missing.removeAll(capabilities);
        this.knownMissing = Immutable.set(missing); this.ownershipComplete = ownershipComplete;
        this.activePreparationEstimates = Immutable.map(estimates);
    }
    public static GearProfile of(Set<Integer> equipped, Set<Integer> owned, Set<String> capabilities, boolean ownershipComplete)
    { return of(equipped, owned, capabilities, Collections.emptySet(), ownershipComplete); }
    public static GearProfile of(Set<Integer> equipped, Set<Integer> owned, Set<String> capabilities, Set<String> knownMissing, boolean ownershipComplete)
    { return new GearProfile(equipped, owned, capabilities, knownMissing, ownershipComplete); }
    public static GearProfile unknown() { return of(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), false); }
    public Set<Integer> equipped() { return equipped; }
    public Set<Integer> owned() { return owned; }
    public Set<String> capabilities() { return capabilities; }
    public Set<String> knownMissing() { return knownMissing; }
    public boolean ownershipComplete() { return ownershipComplete; }
    /** Account-local, user-reported active minutes; no inferred GP or calendar duration. */
    public Map<String, Double> activePreparationEstimates() { return activePreparationEstimates; }
    public GearProfile withActivePreparationEstimates(Map<String, Double> estimates)
    {
        Map<String, Double> copy = new TreeMap<>();
        estimates.forEach((key, value) -> {
            if (key == null || key.isEmpty() || value == null || !Double.isFinite(value) || value < 0 || value > 100000)
                throw new IllegalArgumentException("Invalid manual preparation estimate");
            copy.put(key, value);
        });
        return new GearProfile(equipped, owned, capabilities, knownMissing, ownershipComplete, copy);
    }
    public CapabilityState capability(String key)
    {
        Objects.requireNonNull(key);
        return capabilities.contains(key) ? CapabilityState.PRESENT
            : knownMissing.contains(key) || ownershipComplete ? CapabilityState.MISSING : CapabilityState.UNKNOWN;
    }
    /** Positive observation wins over a conflicting manual absence; unobserved facts can be overridden. */
    public GearProfile withOverrides(Map<String, Boolean> overrides)
    {
        Set<String> present = new TreeSet<>(capabilities), missing = new TreeSet<>(knownMissing);
        overrides.forEach((key, value) -> {
            Objects.requireNonNull(key); Objects.requireNonNull(value);
            if (value) { present.add(key); missing.remove(key); }
            else if (!present.contains(key)) missing.add(key);
        });
        return of(equipped, owned, present, missing, ownershipComplete).withActivePreparationEstimates(activePreparationEstimates);
    }
}

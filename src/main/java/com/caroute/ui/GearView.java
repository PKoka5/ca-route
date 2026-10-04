package com.caroute.ui;

import com.caroute.domain.GearProfile;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable presentation snapshot, never reads mutable client/account state on the EDT. */
public final class GearView
{
    private final GearProfile gear;
    private final Map<String, Boolean> overrides;
    private final boolean bankEnabled, bankObserved;
    private final int bankItems;
    private final long bankObservedAt;

    public GearView(GearProfile gear, Map<String, Boolean> overrides, boolean bankEnabled,
        boolean bankObserved, int bankItems, long bankObservedAt)
    {
        this.gear = gear;
        this.overrides = Collections.unmodifiableMap(new TreeMap<>(overrides));
        this.bankEnabled = bankEnabled;
        this.bankObserved = bankObserved;
        this.bankItems = bankItems;
        this.bankObservedAt = bankObservedAt;
    }

    public static GearView unknown()
    { return new GearView(GearProfile.unknown(), Collections.emptyMap(), false, false, 0, 0); }
    public GearProfile gear() { return gear; }
    public Map<String, Boolean> overrides() { return overrides; }
    public boolean bankEnabled() { return bankEnabled; }
    public boolean bankObserved() { return bankObserved; }
    public int bankItems() { return bankItems; }
    public long bankObservedAt() { return bankObservedAt; }
}

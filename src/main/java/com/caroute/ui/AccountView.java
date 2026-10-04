package com.caroute.ui;

import com.caroute.domain.PlayerProfile;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable presentation facts captured with the route request. */
public final class AccountView
{
    private final GearView gear;
    private final PlayerProfile profile;
    private final Map<String, Integer> killCountOverrides;
    private final boolean exportAvailable;
    public AccountView(GearView gear, PlayerProfile profile, Map<String, Integer> killCountOverrides, boolean exportAvailable)
    {
        this.gear = gear; this.profile = profile;
        this.killCountOverrides = Collections.unmodifiableMap(new TreeMap<>(killCountOverrides));
        this.exportAvailable = exportAvailable;
    }
    public static AccountView of(GearView gear)
    { return new AccountView(gear, null, Collections.emptyMap(), false); }
    public GearView gear() { return gear; }
    public PlayerProfile profile() { return profile; }
    public Map<String, Integer> killCountOverrides() { return killCountOverrides; }
    public boolean exportAvailable() { return exportAvailable; }
}

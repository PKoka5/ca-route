package com.caroute.application;

import com.caroute.CaRouteConfig;
import com.caroute.domain.Preference;
import com.caroute.domain.RouteObjective;
import com.caroute.domain.RoutingPreferences;
import com.caroute.domain.TaskFlag;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mutated only on the client thread; every published value is copied by the domain builder. */
public final class AccountState
{
    public int schema = 3;
    public CaRouteConfig.Target target = CaRouteConfig.Target.NEXT_TIER;
    public boolean allowStretch;
    public Map<TaskFlag, Preference> preferences = new EnumMap<>(TaskFlag.class);
    public Set<Integer> pins = new HashSet<>();
    public Set<Integer> exclusions = new HashSet<>();
    public Map<Integer, Long> snoozedUntil = new HashMap<>();
    public Map<String, Integer> killCounts = new HashMap<>();
    public Set<String> manualGear = new HashSet<>();
    public Map<String, Boolean> gearOverrides = new HashMap<>();
    public boolean observeBank;
    public RouteObjective objective = RouteObjective.FASTEST_NEXT_TIER;
    public String selectedActivity = "";
    public int timeBudgetMinutes = 60;
    public Map<String, Double> preparationEstimates = new HashMap<>();
    public Map<String, Integer> killCountOverrides = new HashMap<>();
    public transient String currentActivity = "";
    // Session-only skip is never mistaken for permanent game completion.
    public transient Set<Integer> skipped = new HashSet<>();

    public static AccountState defaults(CaRouteConfig config)
    {
        AccountState state = new AccountState();
        state.applyConfig(config);
        return state;
    }

    public void applyConfig(CaRouteConfig config)
    {
        target = config.target();
        allowStretch = config.allowStretch();
        observeBank = config.observeBank();
        objective = config.objective(); selectedActivity = config.selectedActivity(); timeBudgetMinutes = Math.max(1, Math.min(1440, config.timeBudgetMinutes()));
        preferences.put(TaskFlag.SPEED, config.speed());
        preferences.put(TaskFlag.PERFECTION, config.perfection());
        preferences.put(TaskFlag.WILDERNESS, config.wilderness());
        preferences.put(TaskFlag.GROUP, config.group());
        preferences.put(TaskFlag.RAID, config.raid());
        preferences.put(TaskFlag.LONG_KC, config.longKc());
        preferences.put(TaskFlag.RNG, config.rng());
    }

    public void sanitize(Set<Integer> canonicalIds, long now)
    {
        if (schema < 1 || schema > 3) { throw new IllegalArgumentException("Unsupported account settings version"); }
        if (target == null) { target = CaRouteConfig.Target.NEXT_TIER; }
        if (preferences == null) { preferences = new EnumMap<>(TaskFlag.class); }
        preferences.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        if (pins == null) { pins = new HashSet<>(); }
        if (exclusions == null) { exclusions = new HashSet<>(); }
        if (snoozedUntil == null) { snoozedUntil = new HashMap<>(); }
        if (killCounts == null) { killCounts = new HashMap<>(); }
        if (manualGear == null) { manualGear = new HashSet<>(); }
        if (gearOverrides == null) { gearOverrides = new HashMap<>(); }
        if (skipped == null) { skipped = new HashSet<>(); }
        if (objective == null) { objective = RouteObjective.FASTEST_NEXT_TIER; }
        if (selectedActivity == null) { selectedActivity = ""; }
        if (timeBudgetMinutes < 1 || timeBudgetMinutes > 1440) { timeBudgetMinutes = 60; }
        if (currentActivity == null) { currentActivity = ""; }
        if (preparationEstimates == null) { preparationEstimates = new HashMap<>(); }
        if (killCountOverrides == null) { killCountOverrides = new HashMap<>(); }
        preparationEstimates.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() < 0 || e.getValue() > 100000);
        killCountOverrides.entrySet().removeIf(e -> e.getKey() == null || e.getKey().trim().isEmpty() || e.getValue() == null || e.getValue() < 0);
        pins.retainAll(canonicalIds);
        exclusions.retainAll(canonicalIds);
        skipped.retainAll(canonicalIds);
        snoozedUntil.entrySet().removeIf(e -> !canonicalIds.contains(e.getKey())
            || e.getValue() == null || e.getValue() <= now);
        killCounts.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 0);
        manualGear.removeIf(s -> s == null || s.trim().isEmpty());
        if (schema == 1) { manualGear.forEach(capability -> gearOverrides.putIfAbsent(capability, true)); }
        if (gearOverrides.containsKey("bowfa_crystal")) { gearOverrides.putIfAbsent("bowfa-crystal", gearOverrides.remove("bowfa_crystal")); }
        gearOverrides.entrySet().removeIf(e -> e.getKey() == null || !e.getKey().matches("[a-z][a-z0-9_-]*") || e.getValue() == null);
        manualGear.clear();
        schema = 3;
    }

    public void setGearOverride(String capability, Boolean owned)
    {
        if (capability == null || !capability.matches("[a-z][a-z0-9_-]*")) { throw new IllegalArgumentException("Invalid gear capability"); }
        if (owned == null) { gearOverrides.remove(capability); }
        else { gearOverrides.put(capability, owned); }
    }

    /** Changing one RuneLite setting must not reset the account's other sidebar choices. */
    public void applyConfigChange(String key, CaRouteConfig config)
    {
        switch (key)
        {
            case "target": target = config.target(); break;
            case "allowStretch": allowStretch = config.allowStretch(); break;
            case "observeBank": observeBank = config.observeBank(); break;
            case "objective": objective = config.objective(); break;
            case "selectedActivity": selectedActivity = config.selectedActivity(); break;
            case "timeBudgetMinutes": timeBudgetMinutes = Math.max(1, Math.min(1440, config.timeBudgetMinutes())); break;
            case "speed": preferences.put(TaskFlag.SPEED, config.speed()); break;
            case "perfection": preferences.put(TaskFlag.PERFECTION, config.perfection()); break;
            case "wilderness": preferences.put(TaskFlag.WILDERNESS, config.wilderness()); break;
            case "group": preferences.put(TaskFlag.GROUP, config.group()); break;
            case "raid": preferences.put(TaskFlag.RAID, config.raid()); break;
            case "longKc": preferences.put(TaskFlag.LONG_KC, config.longKc()); break;
            case "rng": preferences.put(TaskFlag.RNG, config.rng()); break;
            default: break;
        }
    }

    public void act(int taskId, TaskAction action, long now)
    {
        switch (action)
        {
            case PIN: if (!pins.remove(taskId)) { pins.add(taskId); } break;
            case EXCLUDE: exclusions.add(taskId); break;
            case SNOOZE: snoozedUntil.put(taskId, now + 24L * 60 * 60 * 1000); break;
            case SKIP: skipped.add(taskId); break;
            case RESTORE:
                pins.remove(taskId);
                exclusions.remove(taskId);
                snoozedUntil.remove(taskId);
                skipped.remove(taskId);
                break;
            default: throw new IllegalArgumentException("Unknown task action");
        }
    }

    public RoutingPreferences routingPreferences(long now)
    {
        Set<Integer> deferred = new HashSet<>(skipped);
        snoozedUntil.forEach((id, until) -> { if (until > now) { deferred.add(id); } });
        RoutingPreferences.Builder builder = RoutingPreferences.builder()
            .target(target.tier()).objective(objective).selectedActivity(selectedActivity).timeBudgetMinutes(timeBudgetMinutes).currentActivity(currentActivity)
            .allowStretch(allowStretch).pinnedTaskIds(pins)
            .excludedTaskIds(exclusions).deferredTaskIds(deferred);
        preferences.forEach(builder::preference);
        return builder.build();
    }

    public Map<String, Integer> effectiveKillCounts()
    { Map<String, Integer> result = new HashMap<>(killCounts); result.putAll(killCountOverrides); return result; }

    public String taskCustomization(int taskId, long now)
    {
        if (exclusions.contains(taskId)) { return "Manually excluded"; }
        if (snoozedUntil.getOrDefault(taskId, 0L) > now) { return "Snoozed for 24 hours"; }
        if (skipped.contains(taskId)) { return "Skipped for this login"; }
        if (pins.contains(taskId)) { return "Pinned to route"; }
        return "";
    }
}

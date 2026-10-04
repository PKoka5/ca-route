package com.caroute.domain;

import java.util.*;

public final class RoutingPreferences
{
    private final AchievementTier target; private final RouteObjective objective; private final Map<TaskFlag,Preference> preferences;
    private final Set<Integer> excluded,pinned,deferred; private final boolean allowStretch; private final String currentActivity,selectedActivity; private final int timeBudgetMinutes;
    private RoutingPreferences(Builder b){target=b.target;objective=b.objective;preferences=Immutable.map(b.preferences);excluded=Immutable.set(b.excluded);pinned=Immutable.set(b.pinned);deferred=Immutable.set(b.deferred);allowStretch=b.allowStretch;currentActivity=b.currentActivity;selectedActivity=b.selectedActivity;timeBudgetMinutes=b.timeBudgetMinutes;}
    public static Builder builder(){return new Builder();} public AchievementTier target(){return target;} public RouteObjective objective(){return objective;} public Map<TaskFlag,Preference> preferences(){return preferences;} public Preference preference(TaskFlag flag){return preferences.getOrDefault(flag,Preference.NORMAL);} public Set<Integer> excludedTaskIds(){return excluded;} public Set<Integer> pinnedTaskIds(){return pinned;} public Set<Integer> deferredTaskIds(){return deferred;} public boolean allowStretch(){return allowStretch;} public String currentActivity(){return currentActivity;}
    public static final class Builder
    {
        private AchievementTier target; private RouteObjective objective=RouteObjective.FASTEST_NEXT_TIER; private final Map<TaskFlag,Preference> preferences=new EnumMap<>(TaskFlag.class); private Set<Integer> excluded=Collections.emptySet(),pinned=Collections.emptySet(),deferred=Collections.emptySet(); private boolean allowStretch; private String currentActivity="",selectedActivity=""; private int timeBudgetMinutes=60;
        public Builder selectedActivity(String value){selectedActivity=Objects.requireNonNull(value).trim();return this;}
        public Builder timeBudgetMinutes(int value){if(value<1||value>1440)throw new IllegalArgumentException("Time budget must be 1..1440 minutes");timeBudgetMinutes=value;return this;}
        public Builder target(AchievementTier v){target=v;return this;} public Builder objective(RouteObjective v){objective=Objects.requireNonNull(v);return this;} public Builder preference(TaskFlag f,Preference p){preferences.put(Objects.requireNonNull(f),Objects.requireNonNull(p));return this;}
        public Builder excludedTaskIds(Set<Integer> v){excluded=Immutable.set(v);return this;} public Builder pinnedTaskIds(Set<Integer> v){pinned=Immutable.set(v);return this;} public Builder deferredTaskIds(Set<Integer> v){deferred=Immutable.set(v);return this;} public Builder allowStretch(boolean v){allowStretch=v;return this;} public Builder currentActivity(String v){currentActivity=Objects.requireNonNull(v);return this;} public RoutingPreferences build(){return new RoutingPreferences(this);}
    }
    public String selectedActivity(){return selectedActivity;}
    public int timeBudgetMinutes(){return timeBudgetMinutes;}
}

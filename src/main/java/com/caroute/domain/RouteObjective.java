package com.caroute.domain;
public enum RouteObjective
{
    FASTEST_NEXT_TIER("Fastest to target tier", "Cover the selected tier with the lowest estimated preparation and encounter cost."),
    MAXIMUM_POINTS_PER_HOUR("Most points in time budget", "Collect as many points as possible within your active-time budget."),
    EASIEST_PROGRESSION("Easy to hard", "Start with the easiest feasible activity and task range, then progress to harder work using your recorded stats, gear and experience."),
    BOSS_COMPLETION("Complete one boss", "Plan every remaining task at the selected activity, with explicit blockers."),
    MINIMUM_FRICTION("Minimum friction", "Prefer fewer setups, group arrangements and inconvenient task conditions."),
    IRONMAN_EFFICIENT("Ironman preparation", "Give extra weight to missing gear, preparation and restricted-account friction.");

    private final String label, description;
    RouteObjective(String label, String description) { this.label = label; this.description = description; }
    public String displayName() { return label; }
    public String description() { return description; }
    @Override public String toString() { return label; }
}

package com.caroute.domain;

public enum AchievementTier
{
    EASY(1), MEDIUM(2), HARD(3), ELITE(4), MASTER(5), GRANDMASTER(6);
    private final int points;
    AchievementTier(int points) { this.points = points; }
    public int points() { return points; }
    public String displayName() { return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT); }
}

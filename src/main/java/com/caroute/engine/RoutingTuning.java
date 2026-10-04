package com.caroute.engine;

/** Version 1 heuristics. These weights are explanatory costs, not elapsed minutes. */
public final class RoutingTuning
{
    public static final int SEVERE_SKILL_SHORTFALL=20;
    public static final int SEVERE_TOTAL_SHORTFALL=50;
    public static final double STAT_COST_PER_SQUARED_LEVEL=0.025;
    public static final double PREFER_NOT_PENALTY=20;
    public static final double MISSING_RECOMMENDED_GEAR_PENALTY=8;
    public static final double UNKNOWN_FACT_PENALTY=2;
    public static final int MAX_EXHAUSTIVE_CHOICES=10;
    private RoutingTuning(){}
}

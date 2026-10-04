package com.caroute.domain;

import java.util.*;

public final class RoutePlan
{
    private final List<BossSession> bossSessions;
    private final RouteObjective objective;
    private final String selectedActivity;
    private final int timeBudgetMinutes;
    private final boolean scopeComplete;
    private final List<PreparationStep> preparationSteps;
    private final AchievementTier target; private final int currentPoints,threshold,pointsRemaining,projectedPoints; private final double estimatedMinutes; private final List<BossSession> stops; private final List<TaskAssessment> assessments; private final List<String> blockers; private final Reachability reachability; private final Confidence confidence; private final boolean optimal; private final long profileVersion;
    public RoutePlan(AchievementTier target,int currentPoints,int threshold,int pointsRemaining,int projectedPoints,double estimatedMinutes,List<BossSession> stops,List<TaskAssessment> assessments,List<String> blockers,Reachability reachability,Confidence confidence,boolean optimal,long profileVersion){this(target,currentPoints,threshold,pointsRemaining,projectedPoints,estimatedMinutes,stops,assessments,blockers,reachability,confidence,optimal,profileVersion,Collections.emptyList());}
    private RoutePlan(AchievementTier target,int currentPoints,int threshold,int pointsRemaining,int projectedPoints,double estimatedMinutes,List<BossSession> stops,List<TaskAssessment> assessments,List<String> blockers,Reachability reachability,Confidence confidence,boolean optimal,long profileVersion,List<BossSession> bossSessions)
    {this(target,currentPoints,threshold,pointsRemaining,projectedPoints,estimatedMinutes,stops,assessments,blockers,reachability,confidence,optimal,profileVersion,bossSessions,RouteObjective.FASTEST_NEXT_TIER,"",60,reachability!=Reachability.UNKNOWN_PROFILE&&reachability!=Reachability.UNREACHABLE_NOW,Collections.emptyList());}
    private RoutePlan(AchievementTier target,int currentPoints,int threshold,int pointsRemaining,int projectedPoints,double estimatedMinutes,List<BossSession> stops,List<TaskAssessment> assessments,List<String> blockers,Reachability reachability,Confidence confidence,boolean optimal,long profileVersion,List<BossSession> bossSessions,RouteObjective objective,String activity,int budget,boolean complete,List<PreparationStep> preparation)
    {this.target=Objects.requireNonNull(target);this.currentPoints=currentPoints;this.threshold=threshold;this.pointsRemaining=pointsRemaining;this.projectedPoints=projectedPoints;this.estimatedMinutes=Immutable.nonNegative(estimatedMinutes,"estimatedMinutes");this.stops=Immutable.list(stops);this.assessments=Immutable.list(assessments);this.blockers=Immutable.list(blockers);this.reachability=Objects.requireNonNull(reachability);this.confidence=Objects.requireNonNull(confidence);this.optimal=optimal;this.profileVersion=profileVersion;this.bossSessions=Immutable.list(bossSessions);this.objective=Objects.requireNonNull(objective);this.selectedActivity=Objects.requireNonNull(activity);if(budget<1||budget>1440)throw new IllegalArgumentException("Time budget must be 1..1440 minutes");this.timeBudgetMinutes=budget;this.scopeComplete=complete;this.preparationSteps=Immutable.list(preparation);}
    private RoutePlan copy(List<BossSession> sessions,RouteObjective objective,String activity,int budget,boolean complete,List<PreparationStep> preparation)
    {return new RoutePlan(target,currentPoints,threshold,pointsRemaining,projectedPoints,estimatedMinutes,stops,assessments,blockers,reachability,confidence,optimal,profileVersion,sessions,objective,activity,budget,complete,preparation);}
    public RoutePlan withBossSessions(List<BossSession> sessions){return copy(sessions,objective,selectedActivity,timeBudgetMinutes,scopeComplete,preparationSteps);}
    public RoutePlan withScope(RouteObjective objective,String activity,int budget,boolean complete){return copy(bossSessions,objective,activity,budget,complete,preparationSteps);}
    public RoutePlan withPreparationSteps(List<PreparationStep> steps){return copy(bossSessions,objective,selectedActivity,timeBudgetMinutes,scopeComplete,steps);}
    public RouteObjective objective(){return objective;} public String selectedActivity(){return selectedActivity;} public int timeBudgetMinutes(){return timeBudgetMinutes;} public boolean scopeComplete(){return scopeComplete;} public List<PreparationStep> preparationSteps(){return preparationSteps;}
    public List<BossSession> bossSessions(){return bossSessions;}
    public AchievementTier target(){return target;} public int currentPoints(){return currentPoints;} public int threshold(){return threshold;} public int pointsRemaining(){return pointsRemaining;} public int projectedPoints(){return projectedPoints;} public double estimatedMinutes(){return estimatedMinutes;} public List<BossSession> stops(){return stops;} public List<TaskAssessment> assessments(){return assessments;} public List<String> blockers(){return blockers;} public Reachability reachability(){return reachability;} public Confidence confidence(){return confidence;} public boolean optimal(){return optimal;} public long profileVersion(){return profileVersion;}
}

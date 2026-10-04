package com.caroute.domain;

import java.util.*;

public final class TaskAssessment
{
    private final CombatAchievement task; private final Eligibility eligibility; private final double estimatedMinutes,planningCost; private final Map<String,Double> components; private final List<String> reasons; private final Confidence confidence;
    public TaskAssessment(CombatAchievement task,Eligibility eligibility,double estimatedMinutes,double planningCost,Map<String,Double> components,List<String> reasons,Confidence confidence){this.task=Objects.requireNonNull(task);this.eligibility=Objects.requireNonNull(eligibility);this.estimatedMinutes=Immutable.nonNegative(estimatedMinutes,"estimatedMinutes");this.planningCost=Immutable.nonNegative(planningCost,"planningCost");this.components=Immutable.map(components);this.reasons=Immutable.list(reasons);this.confidence=Objects.requireNonNull(confidence);}
    public CombatAchievement task(){return task;} public Eligibility eligibility(){return eligibility;} public double estimatedMinutes(){return estimatedMinutes;} public double planningCost(){return planningCost;} public Map<String,Double> components(){return components;} public List<String> reasons(){return reasons;} public Confidence confidence(){return confidence;}
}

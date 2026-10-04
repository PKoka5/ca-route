package com.caroute.domain;

import java.util.*;

public final class BossSession
{
    private final String activity; private final List<TaskAssessment> tasks; private final int points; private final double estimatedMinutes,planningCost; private final List<String> reasons; private final Confidence confidence; private final boolean conditional;
    public BossSession(String activity,List<TaskAssessment> tasks,int points,double estimatedMinutes,double planningCost,List<String> reasons,Confidence confidence,boolean conditional){this.activity=Objects.requireNonNull(activity);this.tasks=Immutable.list(tasks);this.points=points;this.estimatedMinutes=Immutable.nonNegative(estimatedMinutes,"estimatedMinutes");this.planningCost=Immutable.nonNegative(planningCost,"planningCost");this.reasons=Immutable.list(reasons);this.confidence=Objects.requireNonNull(confidence);this.conditional=conditional;}
    public String activity(){return activity;} public List<TaskAssessment> tasks(){return tasks;} public int points(){return points;} public double estimatedMinutes(){return estimatedMinutes;} public double planningCost(){return planningCost;} public List<String> reasons(){return reasons;} public Confidence confidence(){return confidence;} public boolean conditional(){return conditional;}
}

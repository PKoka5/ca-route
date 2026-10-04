package com.caroute.ui;

import com.caroute.domain.RouteObjective;

/** UI commands; mutable account state remains on the client thread. */
public interface ProfileActions
{
    default void objective(RouteObjective objective) {}
    default void selectedActivity(String activity) {}
    default void timeBudget(int minutes) {}
    default void currentActivity(String activity) {}
    default void killCount(String activity, Integer count) {}
    default void preparationEstimate(String capability, Double minutes) {}
    default void exportRoute() {}
}

package com.caroute.ui;

/** Explicit guide commands; rendering and expanding UI never invoke these actions. */
public interface GuideActions
{
    default void startTask(int taskId) {}
    default void startActivity(String activity) {}
    default void stop() {}
    default void focusMap() {}
}

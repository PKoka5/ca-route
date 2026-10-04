package com.caroute.ui;

/** Immutable presentation snapshot supplied by the guide controller. */
public final class GuideView
{
    private final String title, activity, description, destination, status, instructions;
    private final Integer taskId;
    private final boolean canFocus;

    public GuideView(String title, String activity, Integer taskId, String description,
        String destination, String status, boolean canFocus)
    { this(title, activity, taskId, description, destination, status, canFocus, ""); }

    public GuideView(String title, String activity, Integer taskId, String description,
        String destination, String status, boolean canFocus, String instructions)
    {
        this.title = value(title); this.activity = value(activity); this.taskId = taskId;
        this.description = value(description); this.destination = value(destination);
        this.status = value(status); this.canFocus = canFocus; this.instructions = value(instructions);
    }

    public static GuideView inactive() { return new GuideView("", "", null, "", "", "", false); }
    public boolean active() { return !title.isEmpty(); }
    public String title() { return title; }
    public String activity() { return activity; }
    public Integer taskId() { return taskId; }
    public String description() { return description; }
    public String destination() { return destination; }
    public String status() { return status; }
    public String instructions() { return instructions; }
    public boolean canFocus() { return canFocus; }
    private static String value(String value) { return value == null ? "" : value; }
}

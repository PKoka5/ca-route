package com.caroute.bridge;

import java.util.*;
import java.util.regex.*;

/** Parses local game messages only; unobserved history is unknown, not zero. */
public final class LocalKillCountReader
{
    private static final Pattern COUNT = Pattern.compile("Your (.+?) (?:kill|completion|chest) count is: ([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private final Map<String,String> activities = new HashMap<>();
    public LocalKillCountReader(Collection<String> names) { for (String name : names) activities.put(name.toLowerCase(Locale.ROOT), name); }
    public boolean accept(String message, Map<String,Integer> counts)
    {
        Matcher m = COUNT.matcher(message.replaceAll("<[^>]*>", "")); if (!m.find()) return false;
        String activity = activities.get(m.group(1).trim().toLowerCase(Locale.ROOT)); if (activity == null) return false;
        try { int count = Integer.parseInt(m.group(2).replace(",", "")); int previous = counts.getOrDefault(activity, -1);
            if (count <= previous) return false; counts.put(activity, count); return true;
        } catch (NumberFormatException ex) { return false; }
    }
}

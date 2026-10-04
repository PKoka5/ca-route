package com.caroute.ui;

import java.util.HashMap;
import java.util.Map;

/** UI-only state; replace this object at logout/account changes, never persist account facts here. */
final class DisclosureState
{
    private final Map<String, Boolean> values = new HashMap<>();
    boolean expanded(String key) { return values.getOrDefault(key, false); }
    boolean expanded(String key, boolean fallback) { return values.getOrDefault(key, fallback); }
    void setExpanded(String key, boolean expanded) { values.put(key, expanded); }
}

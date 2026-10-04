package com.caroute.ui;

import com.caroute.domain.CombatAchievement;
import com.caroute.domain.TaskAssessment;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Canonical Wiki article names; no requests are made until the user opens a link. */
final class WikiLinks
{
    private static final String BASE = "https://oldschool.runescape.wiki/w/";
    // Raid modes use article/subpage names, rather than the catalogue's display labels.
    private static final Map<String, String> ACTIVITIES = Map.of(
        "Chambers of Xeric: Challenge Mode", "Chambers of Xeric/Challenge Mode",
        "Theatre of Blood: Entry Mode", "Theatre of Blood/Entry Mode",
        "Theatre of Blood: Hard Mode", "Theatre of Blood/Hard Mode",
        "Tombs of Amascut: Entry Mode", "Tombs of Amascut",
        "Tombs of Amascut: Expert Mode", "Tombs of Amascut");

    private WikiLinks() {}

    static String task(CombatAchievement task)
    {
        // The imported wikiUrl is commonly the activity page. CA task articles use
        // the exact achievement name (e.g. Demonbane_Weaponry_II), independently.
        return supported(task.wikiUrl()) ? article(task.name()) : "";
    }

    static String activity(String activity, List<TaskAssessment> tasks)
    {
        boolean sourced = tasks.stream().anyMatch(task -> supported(task.task().wikiUrl()));
        if (!sourced || activity.isEmpty()) return "";
        if (ACTIVITIES.containsKey(activity)) return article(ACTIVITIES.get(activity));
        String expected = activity.replace(' ', '_');
        for (TaskAssessment assessment : tasks)
        {
            String source = assessment.task().wikiUrl();
            if (supported(source) && ("/w/" + expected).equals(URI.create(source).getPath())) return article(activity);
        }
        if (activity.startsWith("Other: ") && tasks.size() == 1) return task(tasks.get(0).task());
        // Some recent imported activities supply only task links. Use an explicit
        // Wiki search instead of inventing a boss article or opening a random task.
        return BASE + "Special:Search?search=" + URLEncoder.encode(activity, StandardCharsets.UTF_8);
    }

    static boolean search(String url) { return url.contains("/Special:Search?"); }

    private static String article(String title)
    { return BASE + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("%2F", "/"); }

    private static boolean supported(String url)
    {
        try
        {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "oldschool.runescape.wiki".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/w/");
        }
        catch (IllegalArgumentException invalid) { return false; }
    }
}

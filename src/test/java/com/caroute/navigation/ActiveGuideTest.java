package com.caroute.navigation;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActiveGuideTest
{
    private static ActiveGuide guide(String activity)
    { return new ActiveGuide(null, activity, null, "", null, ""); }

    @Test public void encounterLabelsRecognizeTheirVerifiedNpcNamesOnly()
    {
        Map<String, Set<String>> encounters = Map.of(
            "Grotesque Guardians", Set.of("Dawn", "Dusk"),
            "Royal Titans", Set.of("Eldric the Ice King", "Branda the Fire Queen"),
            "Moons of Peril", Set.of("Blue Moon", "Blood Moon", "Eclipse Moon"),
            "Barrows", Set.of("Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
                "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"),
            "Leviathan", Set.of("The Leviathan"),
            "Whisperer", Set.of("The Whisperer"));
        encounters.forEach((activity, names) -> {
            ActiveGuide selected = guide(activity);
            for (String name : names)
            {
                assertTrue(activity + " should identify " + name, selected.matchesNpc(name));
                assertTrue(activity + " should ignore case and surrounding space",
                    selected.matchesNpc(" " + name.toUpperCase(java.util.Locale.ROOT) + " "));
                assertFalse("An echo or similarly named NPC is not the selected encounter",
                    selected.matchesNpc("Echo " + name));
                assertFalse("No broad substring matching", selected.matchesNpc(name + " spawn"));
            }
            assertFalse(activity + " must not highlight unrelated creatures", selected.matchesNpc("Rat"));
            assertFalse(activity + " must not accept unavailable names", selected.matchesNpc(null));
        });
        assertFalse(guide("Moons of Peril").matchesNpc("Moonlight antelope"));
        assertFalse(guide("Barrows").matchesNpc("Crypt rat"));
        assertFalse(guide("Royal Titans").matchesNpc("Ice Queen"));
        assertFalse(guide("Grotesque Guardians").matchesNpc("Gargoyle"));
        assertFalse(guide("Leviathan").matchesNpc("The Leviathan?"));
    }

    @Test public void existingExactMatchesStaySpecificAndTravelActivitiesAreNotBroadened()
    {
        assertTrue(guide("Wyrm").matchesNpc("Wyrm"));
        assertFalse(guide("Wyrm").matchesNpc("Wyrmling"));
        assertTrue(guide("K'ril Tsutsaroth").matchesNpc("K\u2019ril Tsutsaroth"));
        assertFalse(guide("K'ril Tsutsaroth").matchesNpc("Tstanon Karlak"));
        assertTrue(guide("Leviathan").matchesNpc("Leviathan"));
        assertFalse(guide("Giants").matchesNpc("Hill Giant"));
        assertFalse(guide("Giants").matchesNpc("Fire Giant"));
        assertFalse(guide("Chambers of Xeric").matchesNpc("Great Olm"));
        assertFalse(guide("Fortis Colosseum").matchesNpc("Sol Heredit"));
    }
}

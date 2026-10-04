/*
 * RuneLite World Map and Quest Helper coordinate data:
 * Copyright (c) 2020, Arman S <https://github.com/Rman887>
 * Copyright (c) 2018, Magic fTail
 * Copyright (c) 2018, Morgan Lewis <https://github.com/MESLewis>
 * Copyright (c) 2019, Kyle Sergio <https://github.com/ksergio39>
 * Copyright (c) 2019, Bryce Altomare <https://github.com/Twinkiel0ver>
 * Copyright (c) 2019, Kyle Stead <http://github.com/kyle1elyk>
 * Copyright (c) 2020, 2023, 2025, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2021, 2023, Obasill <https://github.com/Obasill>
 * Copyright (c) 2026, adamsbytes <https://github.com/adamsbytes>
 * Copyright (c) 2026, pajlada <rasmus.karlsson@pajlada.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.caroute.navigation;

import com.caroute.domain.CombatAchievement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

/**
 * Reviewed public entrances and staging locations, keyed by exact CA activity names.
 * A point identifies where to start the final approach; it does not prove access,
 * offer a teleport, or identify the tile of an instanced encounter.
 * Wilderness staging locations retain an explicit PvP warning in their instructions.
 */
public final class ActivityDestinations
{
    private static final String SOURCE = "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/"
        + "runelite-client/src/main/java/net/runelite/client/plugins/worldmap/";
    private static final String DUNGEONS = SOURCE + "DungeonLocation.java";
    private static final String MINIGAMES = SOURCE + "MinigameLocation.java";
    private static final String TELEPORTS = SOURCE + "TeleportLocationData.java";
    private static final String TRANSPORT = SOURCE + "TransportationPointLocation.java";
    private static final Map<String, Destination> ENTRIES = create();
    private static final Destination GIANTS_DEN = destination(1419, 3588, "Giants' Den surface entrance",
        "Enter the Giants' Den cave in Shayzien manually. Inside this cave, kill a Hill Giant, Moss Giant and Fire Giant for the selected task.",
        DUNGEONS + "#L102");
    private static final Destination THRALL_PRACTICE = destination(3236, 3458, "Varrock Sewers: suggested thrall practice",
        "This task has no required location. The Varrock Sewers are a suggested place to find suitable creatures: enter the manhole and deal the required damage using undead thralls. Check your spellbook and summoning requirements; another suitable combat area is also valid.",
        DUNGEONS + "#L246");

    private ActivityDestinations() {}

    /** Exact lookup: unsupported activities and task titles have no guessed fallback. */
    public static Optional<Destination> forActivity(String activity)
    { return Optional.ofNullable(ENTRIES.get(activity)); }

    /** A location-specific task can override its broad activity without guessing for other tasks. */
    public static Optional<Destination> forTask(CombatAchievement task)
    {
        if (task == null) return Optional.empty();
        if (task.id() == 398 && task.name().equals("Into the Den of Giants") && task.activity().equals("Giants"))
            return Optional.of(GIANTS_DEN);
        if (task.id() == 399 && task.name().equals("Sit Back and Relax") && task.activity().equals("Other: Sit Back and Relax"))
            return Optional.of(THRALL_PRACTICE);
        return forActivity(task.activity());
    }

    /** Includes every supported canonical raid/boss activity alias. */
    public static Map<String, Destination> entries()
    { return ENTRIES; }

    private static Map<String, Destination> create()
    {
        Map<String, Destination> destinations = new LinkedHashMap<>();
        register(destinations, destination(2917, 3747, "God Wars Dungeon surface entrance",
            "Enter the dungeon manually, then continue to the selected faction. Check access, protection and entry conditions first.",
            DUNGEONS + "#L105"), "K'ril Tsutsaroth", "General Graardor", "Commander Zilyana", "Kree'arra", "Nex");

        // TWILIGHT_TEMPLE_S is also Quest Helper's TAPOYAUIK_TEMPLE_ENTRANCE / enterRuins:
        // https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/
        // src/main/java/com/questhelper/helpers/quests/theheartofdarkness/TheHeartOfDarkness.java#L783
        register(destinations, destination(1693, 3231, "Ruins of Tapoyauik entrance",
            "Use the stairs south-east of the Twilight Temple, then continue through the ruins to Amoxliatl. This marks the surface entrance.",
            DUNGEONS + "#L237"), "Amoxliatl");

        // These bosses' Varrock Sewers location is also identified by Jagex's polls:
        // https://oldschool.runescape.com/polls/2023/1681 (Scurrius)
        // https://oldschool.runescape.com/polls/2018/1505 (Bryophyta)
        register(destinations, destination(3236, 3458, "Varrock Sewers surface entrance",
            "Enter the manhole east of Varrock Palace and continue through the sewers to the selected boss. Choose the encounter entry manually.",
            DUNGEONS + "#L246"), "Scurrius", "Bryophyta");
        register(destinations, destination(3096, 3469, "Edgeville Dungeon surface entrance",
            "Enter the dungeon and continue to the hill giants and Obor's entrance. This marks the public surface approach, not the boss room.",
            DUNGEONS + "#L84"), "Obor");
        register(destinations, destination(2277, 3611, "Kraken Cove entrance",
            "Enter the cove manually and continue to the Kraken chamber. Check your Slayer task and access before entering.",
            DUNGEONS + "#L136"), "Kraken");
        register(destinations, destination(1231, 3729, "Hespori cave entrance",
            "Enter the cave at the Farming Guild manually. Check your own Hespori patch before starting the fight.",
            DUNGEONS + "#L112"), "Hespori");
        register(destinations, destination(3145, 2843, "Ruins of Unkah ferry landing",
            "This is the public Unkah ferry landing. Continue to the Spirit Anglers' participation ship and board manually for Tempoross.",
            TRANSPORT + "#L152"), "Tempoross");
        register(destinations, destination(3231, 3936, "Scorpia cave south entrance",
            "Public cave entrance in deep Wilderness: PvP risk. Enter the cave manually when prepared.",
            DUNGEONS + "#L193"), "Scorpia");
        register(destinations, destination(3565, 3315, "Barrows surface approach",
            "This is the public surface landing near the Barrows mounds. Walk to the mound you want to enter and dig manually; no teleport is performed.",
            TELEPORTS + "#L72"), "Barrows");

        register(destinations, destination(1232, 3573, "Chambers of Xeric entrance",
            "Meet at the public raid entrance. Form your party and choose normal or Challenge Mode manually.",
            DUNGEONS + "#L61"), "Chambers of Xeric", "Chambers of Xeric: Challenge Mode");
        register(destinations, destination(3676, 3219, "Theatre of Blood entrance",
            "Meet at the public entrance in Ver Sinhaza. Select the party and entry, normal or hard mode manually.",
            DUNGEONS + "#L219"), "Theatre of Blood", "Theatre of Blood: Entry Mode", "Theatre of Blood: Hard Mode");
        register(destinations, destination(3357, 2711, "Tombs of Amascut surface entrance",
            "Enter the public lobby manually. Choose your party and invocations there; the marker is outside the raid instance.",
            DUNGEONS + "#L227"), "Tombs of Amascut", "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Expert Mode");

        register(destinations, destination(2985, 3387, "Falador Park mole hole",
            "Public dig point in Falador Park. Dig manually and continue into the Giant Mole tunnels.",
            DUNGEONS + "#L93"), "Giant Mole");
        register(destinations, destination(1701, 3574, "Forthos Dungeon east entrance",
            "Enter Forthos Dungeon manually and continue to Sarachnis' lair.",
            DUNGEONS + "#L97"), "Sarachnis");
        register(destinations, destination(2520, 3740, "Waterbirth Dungeon entrance",
            "Public Waterbirth Island cave entrance. Continue through the dungeon to the Dagannoth Kings; check supplies and the access route first.",
            DUNGEONS + "#L253"), "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
        register(destinations, destination(3226, 3108, "Kalphite Lair surface entrance",
            "Enter the lair manually and continue to the Kalphite Queen. Check ropes and other entry conditions before the approach.",
            DUNGEONS + "#L127"), "Kalphite Queen");
        register(destinations, destination(1308, 3807, "Karuulm Slayer Dungeon entrance",
            "Enter the dungeon manually and continue to Alchemical Hydra. Check Slayer access and the dungeon's floor protection first.",
            DUNGEONS + "#L129"), "Alchemical Hydra");
        register(destinations, destination(2411, 3061, "Smoke Devil Dungeon entrance",
            "Enter the dungeon manually, then continue to the Thermonuclear Smoke Devil chamber. Check Slayer access first.",
            DUNGEONS + "#L206"), "Thermonuclear Smoke Devil");
        register(destinations, destination(3656, 3409, "Morytania Spider Nest entrance",
            "Enter the spider nest manually and continue to Araxxor. Check your Slayer task and access first.",
            DUNGEONS + "#L153"), "Araxxor");
        register(destinations, destination(1794, 3107, "Fortis Colosseum surface entrance",
            "Public surface entrance. Enter the Colosseum and choose to start a run manually.",
            DUNGEONS + "#L99"), "Fortis Colosseum");
        register(destinations, destination(3227, 6116, "Gauntlet entrance in Prifddinas",
            "Enter the public Gauntlet lobby manually and choose normal or corrupted mode. This is not a generated encounter room.",
            DUNGEONS + "#L101"), "Crystalline Hunllef", "Corrupted Hunllef");
        register(destinations, destination(3280, 6059, "Zalcano's Prison entrance",
            "Enter the public prison from Prifddinas manually when ready for Zalcano.",
            DUNGEONS + "#L278"), "Zalcano");
        register(destinations, destination(2437, 5168, "TzHaar Fight Cave entrance",
            "This marks the public TzHaar entrance, outside the wave instance. Start the Fight Cave manually when prepared.",
            MINIGAMES + "#L88"), "TzTok-Jad");
        register(destinations, destination(2495, 5118, "Inferno entrance in Mor Ul Rek",
            "Public entrance outside the Inferno instance. Check access and start the challenge manually.",
            MINIGAMES + "#L53"), "TzKal-Zuk");
        register(destinations, destination(2544, 5111, "TzHaar-Ket-Rak challenge entrance",
            "Meet TzHaar-Ket-Rak in the public city and select a challenge manually. This is not an instanced arena tile.",
            MINIGAMES + "#L90"), "TzHaar-Ket-Rak's Challenges");
        register(destinations, destination(1435, 3131, "Cam Torum entrance",
            "Enter Cam Torum manually, then continue through the settlement to Neypotzli for Moons of Peril. This marker is the town entrance.",
            DUNGEONS + "#L54"), "Moons of Peril");

        register(destinations, destination(3292, 3850, "Callisto's Den surface entrance",
            "Public entrance in Wilderness: PvP risk. Enter Callisto's Den manually when prepared.",
            DUNGEONS + "#L53"), "Callisto");
        register(destinations, destination(3320, 3798, "Silk Chasm surface entrance",
            "Public entrance in Wilderness: PvP risk. Enter the Silk Chasm manually for Venenatis.",
            DUNGEONS + "#L200"), "Venenatis");
        register(destinations, destination(3221, 3788, "Vet'ion's Rest surface entrance",
            "Public entrance in Wilderness: PvP risk. Enter Vet'ion's Rest manually when prepared.",
            DUNGEONS + "#L248"), "Vet'ion");
        register(destinations, destination(3202, 3681, "Corporeal Beast cave surface entrance",
            "This surface approach is in Wilderness: PvP risk. Enter the public cave manually and prepare before going into the boss room.",
            DUNGEONS + "#L66"), "Corporeal Beast");
        register(destinations, destination(3016, 3849, "King Black Dragon surface approach",
            "Public entrance approach in deep Wilderness: PvP risk. Continue through the dungeon to the King Black Dragon's lair manually.",
            DUNGEONS + "#L131"), "King Black Dragon");
        register(destinations, destination(1432, 3670, "Chasm of Fire surface entrance",
            "Enter the public Chasm of Fire and continue to Yama's entry. Check access and select the encounter manually.",
            DUNGEONS + "#L64"), "Yama");
        register(destinations, destination(3175, 2478, "Shellbane Gryphon cave entrance",
            "Public cave entrance. Check your own access and enter the Shellbane Gryphon encounter manually.",
            DUNGEONS + "#L198"), "Shellbane gryphon");

        register(destinations, destination(2638, 3698, "Rellekka boat to Ungael",
            "Public boat departure in Rellekka. Board manually for Ungael, then continue to Vorkath; check your quest access before travelling.",
            TRANSPORT + "#L79"), "Vorkath");
        register(destinations, destination(2197, 3056, "Zul-Andra public approach",
            "This is the public Zul-Andra landing area. Continue to the boat and start Zulrah manually; no teleport is performed.",
            TELEPORTS + "#L218"), "Zulrah");
        register(destinations, destination(1624, 3938, "Wintertodt Camp public approach",
            "This is the public camp outside the encounter. Continue to the Wintertodt entry manually; no teleport is performed.",
            TELEPORTS + "#L80"), "Wintertodt");
        registerRemainingActivities(destinations);
        return Collections.unmodifiableMap(destinations);
    }

    /** Reviewed approaches for every remaining canonical boss and Slayer activity. */
    private static void registerRemainingActivities(Map<String, Destination> destinations)
    {
        register(destinations, destination(3423, 3536, "Slayer Tower public approach",
            "Enter the Slayer Tower's ground-floor entrance and climb to the first floor for Aberrant Spectres. Bring a nose peg or Slayer helmet and check your Slayer level; the task-only basement is not the destination.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L103"), "Aberrant Spectre");
        register(destinations, destination(3036, 4761, "Abyssal Nexus fairy ring approach",
            "Use fairy ring DIP to reach the public Abyssal Nexus, then continue to a Sire chamber. Check your Slayer level and an eligible abyssal demon or boss task. This point is outside the combat chamber.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/FairyRingLocation.java#L76"), "Abyssal Sire");
        register(destinations, destination(2464, 4012, "Jormungand's Prison surface entrance",
            "Travel by boat from Rellekka to the Isle of Stone, then enter Jormungand's Prison. Continue to Basilisk Knights manually after The Fremennik Exiles; bring a mirror shield or V's shield.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L125"), "Basilisk Knight");
        register(destinations, destination(2883, 3397, "Taverley Dungeon surface entrance",
            "Climb down the entrance ladder and continue to the ordinary adult black dragons deep in Taverley Dungeon. Check the dusty-key route or available Agility shortcuts and dragonfire protection. The upper floor near the entrance is Slayer-task-only.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L215"), "Black Dragon");
        register(destinations, destination(3423, 3536, "Slayer Tower public approach",
            "Enter the Slayer Tower's ground floor and climb to the first floor for Bloodveld. Check your Slayer level and Morytania access before travelling; use the ordinary upper tower rather than the task-only basement.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L103"), "Bloodveld");
        register(destinations, destination(1636, 3673, "Catacombs of Kourend main entrance",
            "Enter the Catacombs beneath the statue in Kourend Castle's courtyard, then continue to the brutal black dragons in the north-west Dragon's Den. Check your Slayer level and dragonfire protection. This marks the public main entrance.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L60"), "Brutal Black Dragon");
        register(destinations, destination(3262, 3294, "Brutus bull pen gate",
            "Use the bull pen gate in the north-east Lumbridge cow field. Check The Ides of Milk access and choose the encounter manually.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/theidesofmilk/TheIdesOfMilk.java#L182"), "Brutus");
        register(destinations, destination(2883, 3397, "Taverley Dungeon surface entrance",
            "Enter Taverley Dungeon, continue to the hellhound area and use the passage to Cerberus. Check your Slayer level, eligible task and the dusty-key route or available Agility shortcuts.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L215"), "Cerberus");
        register(destinations, destination(3263, 3918, "Chaos Elemental public Wilderness area",
            "Public approach west of the Rogues Castle in deep Wilderness: PvP risk. Prepare before approaching the roaming Chaos Elemental.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessHard.java#L213"), "Chaos Elemental");
        register(destinations, destination(3028, 3840, "Lava Maze south-west public approach",
            "This is a public staging point in deep Wilderness: PvP risk. Continue west towards the Chaos Fanatic, west of the Lava Maze. No burning-amulet teleport is performed by CA Route.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L120"), "Chaos Fanatic");
        register(destinations, destination(2969, 3695, "Forgotten Ruins public approach",
            "Public approach in the Wilderness: PvP risk. Continue into the nearby Forgotten Ruins to find the Crazy Archaeologist. No Dareeyak teleport is performed by CA Route.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L51"), "Crazy Archaeologist");
        register(destinations, destination(2435, 3520, "Gnome Stronghold north fence breach",
            "Use the fence breach north-west of the Grand Tree, then enter the Crash Site Cavern to the north-east and continue to Demonic Gorillas. Check Monkey Madness II completion. The marker is at the public fence approach, outside the cavern encounter.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/monkeymadnessii/MonkeyMadnessII.java#L579"), "Demonic Gorilla");
        register(destinations, destination(3676, 3755, "Fossil Island Tar Swamp mushtree approach",
            "Travel to the Tar Swamp on Fossil Island, then continue south to the Deranged Archaeologist. Check Bone Voyage access and bring an axe for the swamp route. This is the public mushtree approach, not the Wilderness boss.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TransportationPointLocation.java#L240"), "Deranged Archaeologist");
        register(destinations, destination(1310, 3103, "Crypt of Tonali surface lift",
            "Use the unlocked surface lift in the Tlati Rainforest and continue north through the Crypt of Tonali to the Doom entrance. Check The Final Dawn completion and choose the delve manually.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/thefinaldawn/TheFinalDawn.java#L1026"), "Doom of Mokhaiotl");
        register(destinations, destination(2867, 3940, "Weiss basalt mine stairs",
            "Descend the Weiss stairs and use the southern cave into Ghorrock Dungeon, then continue through the prison to Duke Sucellus. Check Desert Treasure II completion. This is the public surface approach, not the quest prison instance.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/deserttreasureii/SucellusSteps.java#L315"), "Duke Sucellus");
        register(destinations, destination(2743, 3154, "Brimhaven Dungeon north entrance",
            "Enter Brimhaven Dungeon and continue to the fire giants deep in the dungeon. Bring an axe for vines and the entry fee unless permanent access is unlocked. This is the public northern entrance; select and fight the giant manually.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L50"), "Fire Giant");
        register(destinations, destination(3264, 6065, "Prifddinas Tower of Voices approach",
            "Continue to the Tower of Voices and climb to the top floor. Use the Memoriam Device in the southern room to replay the Fragment of Seren fight after Song of the Elves. Do not return to the original quest arena.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L165"), "Fragment of Seren");
        register(destinations, destination(2458, 2851, "Myths' Guild public approach",
            "Enter the Myths' Guild and use the Pool of Dreams on the ground floor to refight Galvek. Check Dragon Slayer II completion. The marker is in the public guild, not the quest ship instance.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L143"), "Galvek");
        register(destinations, destination(3423, 3536, "Slayer Tower public approach",
            "Enter the Slayer Tower and climb to the second floor for Gargoyles. Check your Slayer level and bring a rock hammer or another valid finishing tool. Use the ordinary tower floors rather than the task-only basement.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L103"), "Gargoyle");
        register(destinations, destination(2465, 3495, "Grand Tree public approach",
            "Continue to Zooknock's house north-west of the Gnome Ball field, climb upstairs and gaze into the Burning Brazier to refight Glough after Monkey Madness II. This marks the public Grand Tree approach, not the quest fight instance.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L146"), "Glough");
        register(destinations, destination(1636, 3673, "Catacombs of Kourend main entrance",
            "Enter the Catacombs beneath the statue in Kourend Castle's courtyard, then continue to Greater Demons in the north-east Demon's Run area. This is a non-Wilderness approach; finish with a demonbane weapon when the selected task requires it.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L60"), "Greater Demon");
        register(destinations, destination(3423, 3536, "Slayer Tower public approach",
            "Enter the Slayer Tower, climb to the gargoyle floor and use the rooftop stairs. Check your brittle-key unlock, eligible Slayer task and finishing tool; enter the rooftop fight manually.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L103"), "Grotesque Guardians");
        register(destinations, destination(1636, 3673, "Catacombs of Kourend main entrance",
            "Enter the Catacombs beneath the statue in Kourend Castle's courtyard, then continue to Hellhounds in the north-west Dragon's Den area. This marks the main public entrance and avoids the Wilderness.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L60"), "Hellhound");
        register(destinations, destination(2796, 3615, "Fremennik Slayer Dungeon surface entrance",
            "Enter the Fremennik Slayer Dungeon and continue through the chambers to the ordinary Kurask area at the far end. Check your Slayer level and use leaf-bladed weapons, broad ammunition or Magic Dart. The upstairs Kurask area is Slayer-task-only.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L100"), "Kurask");
        register(destinations, destination(3103, 3162, "Wizards' Tower basement entrance",
            "Descend into the Wizards Tower basement, use the portal to the Temple of the Eye and travel through the rift to the Scar, then continue to the Leviathan. Check Desert Treasure II completion. Choose all transport and encounter entries manually.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L268"), "Leviathan");
        register(destinations, destination(1311, 3686, "Molch Lizardman Temple north entrance",
            "Enter the Lizardman Temple in Molch, then continue to the shaman chambers. Check Shayzien protection and supplies. Shayzien Protector must be completed here in Molch, without the shaman damaging anyone except with its spawns.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L142"), "Lizardman Shaman");
        register(destinations, destination(2540, 2213, "Ardeaglais / Wyrmscraig Cavern central entrance",
            "Travel to Wyrmscraig and continue to Ardeaglais at the central cavern entrance. Check Fallen From Grace completion and enter the repeatable Mad Angel encounter manually. Island travel depends on your unlocked routes.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L271"), "Mad Angel");
        register(destinations, destination(3703, 3185, "Icyene Graveyard portal to Vampyrium",
            "Use the Icyene Graveyard portal after The Blood Moon Rises, then continue through Vampyrium to the Maggot King lair east of Sangvesti. Enter the solo fight manually. This marker is at the public portal approach.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/thebloodmoonrises/TheBloodMoonRises.java#L1296"), "Maggot King");
        register(destinations, destination(2867, 3940, "Weiss basalt mine stairs",
            "Descend the stairs in Weiss, enter the cave to the south and continue to the Phantom Muspah entrance. Check Secrets of the North completion. The marker is at the public surface approach.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/secretsofthenorth/SecretsOfTheNorth.java#L496"), "Phantom Muspah");
        register(destinations, destination(3727, 3300, "Slepe Sisterhood Sanctuary entrance",
            "Descend beneath Slepe's church into the Sisterhood Sanctuary, then choose Phosani's Nightmare manually. Check access and supplies; the marker is outside the encounter instance.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L201"), "Phosani's Nightmare");
        register(destinations, destination(3007, 3150, "Asgarnian Ice Dungeon surface entrance",
            "Enter the dungeon south of Port Sarim, then continue north past the pirates and west to the Royal Titans tunnel. Use an unlocked shortcut if suitable, and choose solo or duo entry manually.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L47"), "Royal Titans");
        register(destinations, destination(3007, 3150, "Asgarnian Ice Dungeon surface entrance",
            "Climb down the ladder south of Port Sarim, continue to the back of the Asgarnian Ice Dungeon and enter the Icy Cavern for Skeletal Wyverns. Check your Slayer level and bring valid wyvern-breath protection.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L47"), "Skeletal Wyvern");
        register(destinations, destination(1636, 3673, "Catacombs of Kourend main entrance",
            "Enter the Catacombs beneath the statue in Kourend Castle courtyard. Bring a complete dark totem and use it at the central altar to enter Skotizo manually.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L60"), "Skotizo");
        register(destinations, destination(1490, 3284, "Darkfrost base-camp approach",
            "Continue east from this public Darkfrost approach to the base camp. Speak to Taala when required, then use the chain or choose a private group at the meeting flag manually. Check Varlamore access.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L122"), "The Hueycoatl");
        register(destinations, destination(1645, 3579, "Watson's house public approach",
            "Enter Watson's house and go upstairs to the Strange Casket. Bring an actual Mimic casket to start the fight; merely enabling the Mimic option does not provide a fight.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java#L221"), "The Mimic");
        register(destinations, destination(3727, 3300, "Slepe Sisterhood Sanctuary entrance",
            "Descend beneath Slepe's church into the Sisterhood Sanctuary, continue to the Nightmare and choose to disturb it manually. The marker is outside the encounter instance.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L201"), "The Nightmare");
        register(destinations, destination(3168, 3172, "Lumbridge Swamp Caves surface entrance",
            "Enter the Lumbridge Swamp Caves with a rope and safe light source, then continue south-east to Tears of Guthix. Use a lit sapphire lantern on a light creature to travel into the chasm and continue to the Ancient Guthixian Temple after While Guthix Sleeps.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L146"), "Tormented Demon");
        register(destinations, destination(1118, 3428, "Stranglewood ritual-site entrance",
            "Approach the ritual-site entrance in the west of the Stranglewood. Check Desert Treasure II completion and enter the normal or Awakened Vardorvis encounter manually.",
            "https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/deserttreasureii/VardorvisSteps.java#L325"), "Vardorvis");
        register(destinations, destination(2998, 3493, "Ruins of Camdozaal surface entrance",
            "Enter Camdozaal west of Ice Mountain, descend to Lassar Undercity and continue to the Sunken Cathedral for the Whisperer. Check Desert Treasure II completion before choosing normal or Awakened entry.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L180"), "Whisperer");
        register(destinations, destination(1308, 3807, "Karuulm Slayer Dungeon surface entrance",
            "Enter the Karuulm Slayer Dungeon using the Mount Karuulm elevator, then step over into the Wyrm area on the lower floor. Check your Slayer level and wear boots of stone or another valid floor-protection option unless your account has the exemption.",
            "https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java#L129"), "Wyrm");
    }

    private static Destination destination(int x, int y, String label, String instructions, String sourceUrl)
    { return new Destination(new WorldPoint(x, y, 0), label, instructions, sourceUrl); }

    private static void register(Map<String, Destination> destinations, Destination destination, String... activities)
    {
        for (String activity : activities)
            if (activity.isEmpty() || destinations.putIfAbsent(activity, destination) != null)
                throw new IllegalStateException("Duplicate or empty navigation activity: " + activity);
    }

    public static final class Destination
    {
        private final WorldPoint point;
        private final String label, instructions, sourceUrl;

        private Destination(WorldPoint point, String label, String instructions, String sourceUrl)
        {
            this.point = Objects.requireNonNull(point);
            if (point.getX() <= 0 || point.getX() >= 16384 || point.getY() <= 0 || point.getY() >= 16384
                || point.getPlane() < 0 || point.getPlane() > 3)
                throw new IllegalArgumentException("Destination coordinates are outside the game world");
            this.label = nonBlank(label); this.instructions = nonBlank(instructions); this.sourceUrl = nonBlank(sourceUrl);
        }
        private static String nonBlank(String value)
        {
            if (Objects.requireNonNull(value).trim().isEmpty()) throw new IllegalArgumentException("Empty destination metadata");
            return value;
        }
        public WorldPoint point() { return point; }
        public String label() { return label; }
        public String instructions() { return instructions; }
        public String sourceUrl() { return sourceUrl; }
    }
}

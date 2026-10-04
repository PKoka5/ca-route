# Selected task and travel guide

Implemented on 3 October 2026. Select **Start task** in an expanded task, or
**Guide boss** on Route/Bosses. Selection is transient, account-bound and separate
from route planning, pins, Wiki links and game completion. A safely loaded
account with verified bundled task identities is required; already completed tasks
cannot start a guide. Full coverage of newly added live tasks is not required.
This corrects the first logged-in report where the planner rendered a safe known
subset but Start task silently required complete total coverage. The reader's
`loaded` flag continues to reject unsafe identities and inconsistent account state.

The active card uses RuneLite fonts, colors and square controls, with a Quest
Helper-style orange header and black selected-task title. The exact task condition
is also visible in the card body.
Travel details start collapsed, preserve disclosure state during same-account
refreshes and explain the public approach. **Map** centers an already opened world
map; it does not open a game interface or perform travel. **Stop** ends selection.
Completed-task updates, logout, hopping, account switches and plugin shutdown
also remove the guide and its own map marker.

## Visuals

| Visual | Evidence and behavior |
| --- | --- |
| World map | Original grey quest-star marker at the curated entrance; edge snapping and click-to-center |
| Task reminder | Compact movable native overlay: selected title once, exact condition, recorded KC when known and specific Wilderness/PvP caution; rewards and route/access status remain in the sidebar |
| Recorded KC | Existing local observed/manual count versus the selected KC threshold; no line when history is unknown |
| Entrance tile | Selected public approach when visible in the non-instanced scene |
| Minimap | Entrance dot within local minimap range, on the same plane and outside instances |
| Boss highlight | Currently loaded NPCs matching the selected activity or verified encounter members; normalized case/apostrophes, without attack-state or timing predictions |

The guide does not infer perfect-task success, count combat attacks, recommend
prayers, predict mechanics or mark combat positions. Group activity labels and raid
names do not imply identification of every enemy. The game supplies completion.
Each visual can be disabled in the **Active guide** config section. Shared client
hint arrows are not modified.

## Optional Shortest Path integration

CA Route uses the public
[Shortest Path PluginMessage handler](https://github.com/Skretzo/shortest-path/blob/master/src/main/java/shortestpath/ShortestPathPlugin.java):
namespace `shortestpath`, message `path`, data `target: WorldPoint`. A stop posts
`clear` only while the last CA Route request is still owned. No start-position or
travel configuration override is sent. Shortest Path remains optional and its
own configuration controls transport choices and path rendering.

This follows the public entrance-target pattern in
[Quest Helper's DetailedQuestStep](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/steps/DetailedQuestStep.java).
Neither plugin's implementation is compiled into CA Route; there is no reflection,
mandatory cross-plugin dependency or runtime account-data request.

`Target sent` means a request was posted, not that a path was found. If Shortest
Path is absent/disabled, the task and CA Route markers still work. After enabling
it, select again. Reaching within three tiles of the non-instanced approach or
being within twelve tiles of a matching loaded boss on the same plane ends CA
Route's travel request while retaining the reminder.
It does not prove that the task or its entry requirements are complete.

Shortest Path exposes no owner token/current-target query. The bridge tracks the
identity of its own synchronous event echoes. Foreign `path`/`clear` messages,
manual target/start/clear/find-closest actions and Shortest Path lifecycle changes
revoke the claim, including a foreign request for identical coordinates. Refreshes
and visual-setting changes never reclaim a revoked route. Explicit reselection or
toggling **Use Shortest Path** back on can request a new route.

An independently registered observer remains active through queued client-thread
shutdown cleanup. Off-thread foreign commands revoke ownership before Shortest
Path's default-priority handlers; queued callbacks from disposed observers are
discarded. Guide commands and publication also check the active account and
lifecycle. Each guide instance has a separate bridge, so old shutdown work cannot
clear a newly selected guide's route.

## Destination evidence

`ActivityDestinations` covers **all 655 bundled tasks across 91 activities**, with
**66 distinct public approach points, 89 activity keys and two exact task
overrides**. Raid variants share staging points. `Into the Den of Giants` (ID 398)
keeps the Giants' Den entrance at (1419, 3588, 0). `Sit Back and Relax` (ID 399)
has no required location: its suggested Varrock Sewers practice approach is labeled
as optional, not a new task requirement. Both overrides match ID, name and activity
together; broad or altered task identities do not inherit them.
Wyrm now uses the Mount Karuulm entrance at (1308, 3807, 0), with instructions for
the elevator, lower-floor Wyrm area and floor protection. Molch Shaman tasks use
the Molch temple, and Galvek/Glough/Seren use public replay approaches rather than
their original quest instances.
Entries come from pinned RuneLite `runelite-parent-1.13.1` World Map enums and
Quest Helper `75b623a6bc14237831fddc10b44765c0910a4eb0` location definitions;
every destination records its source URL/line and manual entry instructions.
See the [complete activity/location table](TASK_LOCATIONS.md) and the additional
research record in [navigation additions](../data/navigation-additions.json).
Source/data BSD notices are bundled separately. See
[third-party attribution](../THIRD_PARTY_NOTICES.md).

Examples: K'ril and the other GWD encounters use the surface GWD approach, Amoxliatl
uses the Ruins of Tapoyauik entrance, and Scurrius uses the Varrock sewer entrance.
Unknown activities never get guessed coordinates. Some approaches are in the
Wilderness even when the encounter itself is not; the selected destination's
instructions drive an explicit PvP caution, including Corporeal Beast's surface
approach. Shortest Path transport settings may offer other travel methods.

## Validation and remaining client check

Automated checks cover destination identities/bounds/immutability, path protocol
and ownership, real EventBus ordering, cleanup after owner unregistration, stale
queued events, account/completion resets, arrival, independent map markers,
unknown KC and 225/280-pixel native UI renders. The reminder overlay is rendered
with real RuneLite components. The final build record is in
[release verification](RELEASE_VERIFICATION.md).
The full-catalogue checks require a destination for every bundled task and exercise
all 655 task selections through the controller, real map markers and Shortest Path
messages. Two regressions cover Giants to Wyrm, replacement after arrival and
foreign routes surviving arrival/Stop. The per-task CSV is generated at
`build/navigation/task-destination-coverage.csv`.

For the logged-in smoke test, enable Shortest Path and select an unfinished
Scurrius/Amoxliatl task. Check the public map marker, path display using your
Shortest Path settings, Map with the world map open, nearby entrance visuals and
the task reminder. Choose a manual Shortest Path target, then Stop CA Route and
verify that target stays. Also test arrival, actual completion, logout/account
switch, plugin toggling and an unsupported destination.

User feedback confirms Start task, its reminder and the Giants travel path in a
logged-in client. The new destinations and remaining ownership/visual cases still
need client smoke checks. Static guide scope was checked against
[RuneLite's rejected-feature guidance](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
and [Jagex's client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
on 3 October 2026; local verification does not represent Plugin Hub approval.

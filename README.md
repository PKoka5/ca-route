# CA Route

CA Route is a local RuneLite plugin for planning Old School RuneScape Combat
Achievement progression. **1.0.0** has six
route modes, boss task bundles, account-aware preparation and local route export.
An explicitly selected task or boss also has travel guidance and visual reminders.
It has not been published on Plugin Hub.
Source and support: [GitHub](https://github.com/PKoka5/ca-route) and
[report an issue](https://github.com/PKoka5/ca-route/issues).

Open the grey quest-star sidebar icon. **Tasks** opens first with the **Recommended**
filter selected; the tabs are ordered
Tasks, Bosses, Route, Profile, Prep. **Route** has compact Goal and Ordering
selectors, then the next boss and a single icon row for tasks, points and estimated
time. Task rows start with only their title and a right disclosure arrow;
click a row to see its description, status, estimate, actions and Wiki guide.
Every bundled task also has an independently collapsed **Quick guide**, with
two to four short, numbered instructions for preparing and completing that task.
The instructions are available in Tasks, Route and Bosses. Opening them preserves
your selected task; **Start task** remains the explicit travel-guidance action.
See [task guide content and maintenance](docs/TASK_GUIDES.md).
The small external-link icon beside a boss or task opens its own OSRS Wiki page
directly, including while a task is collapsed.
Later stops and requirements are also collapsed. An incomplete
route keeps a visible **Partial route** indicator.

**Requirements & gear** shows the task's required levels and quests alongside
your account's current progress, plus practical equipment and access reminders.
Suggested levels and gear are labeled separately. For Wyrm this includes 62 Slayer
and heat-protective boots, with the elite Kourend & Kebos Diary exception.
Ownership does not imply that an item is equipped. Planning diagnostics are kept
out of this player checklist.

**Bosses** compares expandable sessions, **Tasks** has search, visible primary
filters and a collapsed task-type filter, **Prep** groups expandable preparation
steps, and **Profile** holds
goals and collapsible account inputs. Expanded sections stay open during a route
refresh for the same account and reset on logout/account change. The compact
task browser also retains its search, filters, ordering and page during the
same account's route updates; changed results clamp the page as needed. The
sidebar follows Quest Helper's flat title lists, labeled dropdowns, dark section
bars and orange active-step header, using RuneLite's colors, fonts and native
controls. Tasks keeps Filters, Difficulty and Ordering visible above search, with
the task-type filter collapsed under More filters. Long list titles use an ellipsis
and retain their full tooltip/accessibility text; see the
[UI refresh and validation](docs/UI_REFRESH.md).

## Guide a task or boss

Expand a task and click **Start task**, or choose **Guide boss** on a boss session.
The Route tab shows an active task card with its title in orange, Map, Stop and
collapsed Travel details. Opening a task or its Wiki page alone does not select it.

- A grey marker identifies the destination on the world map. Open the world map,
  then click **Map** to center it on the destination.
- With **Shortest Path** installed and enabled, CA Route sends that plugin the
  selected public entrance. Its own travel and path-display settings remain in use.
  If it was enabled after selection, select the task/boss again to request a route.
- A compact movable RuneLite overlay shows the selected task title and its exact
  condition. Recorded KC appears for KC tasks only when known. Rewards, access
  details and route status stay in the sidebar; specific Wilderness cautions remain
  visible in the overlay.
- The entrance gets a nearby minimap marker and a visible entrance-tile highlight.
  Currently visible NPCs matching the selected activity name get a boss highlight.

All **655 bundled tasks across 91 activities have a destination**, using **66
distinct public approach points**. Wyrm uses the Mount Karuulm dungeon entrance;
Into the Den of Giants keeps its exact Shayzien entrance. Instanced activities
use public approaches, and quest bosses use their replay approaches. Travel
details explain the final manual steps and access or Wilderness considerations.
Sit Back and Relax has no required location, so its destination is clearly labeled
as a suggested place to practise with thralls. Unknown future activities retain
an explicit unmapped status. See the [complete location overview](docs/TASK_LOCATIONS.md).
These destinations do not prove account access or that Shortest Path found a path.

Reaching the approach ends our travel request and keeps the task reminder. **Stop**,
real completion of the selected task, logout, hopping and account changes clear
the guide. A subsequent manual Shortest Path target or another plugin's request is
left alone. All visuals and the optional integration can be switched off separately
under **Active guide** in CA Route's settings. See [guide details](docs/ACTIVE_GUIDE.md).

## Choose a route mode

Select **Ordering** on Route, **Route mode** in Profile or the plugin settings. **Easy to hard** is the
default for new settings. Existing account choices are retained; the Route tab's
**Easy to hard** button switches explicitly from another mode.

| Mode | What it plans |
| --- | --- |
| Fastest to target tier | Covers the selected tier using estimated encounter/preparation time and displayed readiness/preferences costs. Next tier advances automatically. |
| Most points in time budget | Maximizes planned points within 1–1440 active minutes. This is a session plan, not a promise to reach a tier. |
| Easy to hard | First chooses the lowest account-adjusted difficulty range that can supply the needed points, then optimizes planning cost within it. Selected stops and tasks progress from easier to harder. |
| Complete one boss | Includes remaining tasks at the selected activity and reports blocked, excluded or deferred tasks that prevent complete coverage. |
| Minimum friction | Gives extra weight to setups, coordination and inconvenient conditions while pursuing the tier. |
| Ironman preparation | Gives extra weight to missing/unconfirmed gear, preparation and RNG without assuming purchases or inventing grind durations. |

NORMAL / PREFER_NOT / AVOID preferences cover speed, perfection, Wilderness,
groups, raids, long KC and RNG. Prefer not adds planning cost; Avoid excludes
tagged tasks. Pins, exclusions, 24-hour snoozes, skips for this login and restoration
are per account. Pins cannot bypass access, exclusion or readiness checks.

Boss mechanics and individual task challenge are separate from CA reward tiers.
Easy to hard uses hand-rated estimates plus recorded stats, missing gear and known
KC. Unknown skills/KC are not filled with invented values. A difficult pinned task
can raise the route's difficulty range; if easier remaining tasks cannot close
the gap, harder tasks can still be needed. Ironman preparation emphasizes gear
and acquisition costs and does not promise an easy first boss. See
[route policies](docs/ROUTE_POLICIES.md).

Physical ETA is separate from policy costs. Shared boss setup, nested KC and
explicitly matching stamina conditions reduce repeated work. A reported gear
preparation is counted once per capability across the route. Larger activity groups
use bounded candidate bundles; optimality applies to the supported cost model and
candidate construction, not every possible gameplay strategy.

## Prep and account inputs

Prep separates known hard unlocks, unknown facts to verify and soft readiness
advice. Shared skill/quest/gear needs are grouped with related tasks and points.
Related points are not guaranteed rewards for completing one step. Several steps
may refer to the same tasks, so their point totals must not be added together.
Requirement alternatives are preserved rather than demanding every combat style.

This is direct prerequisite advice. There is no complete quest dependency graph,
automatic quest order or calibrated training plan. Actual game completion drives
progress; sidebar actions do not award CA points or complete quests.

Profile provides these manual inputs:

- **Kill counts:** expand this section, select an activity and use Set KC for historical progress
  the client has not observed. Auto removes the override and uses local game
  message observations. The zero shown in an empty entry field is not evidence of
  zero KC; unobserved history stays unknown.
- **Current activity:** an explicit choice for this login removes that
  activity's extra setup cost. Select (No activity) after leaving.
- **Gear & preparation:** expand a capability. Auto uses observed equipment/inventory; Owned confirms
  unseen ownership; Missing reports absence for that capability. Observed positives
  take priority over a conflicting Missing choice.
- **Your active preparation estimate (min):** enter your own active minutes for a
  recommended capability or alternative kit. Set saves it; Auto removes it.
  The estimate can apply to missing or unconfirmed ownership and is counted once
  for that capability across the route. It remains a manual estimate, not measured
  gameplay, a calendar wait or a GP calculation. Confirmed ownership removes that
  preparation need. Reset manual gear and prep clears gear choices and estimates.

The gear overlay contains **29 capabilities and 83 task recommendations across
14 activities**, including Barrows, Vorkath, Kraken, Sarachnis, Dagannoth Kings,
Fight Caves, GWD, Scurrius and selected Zulrah speed tasks. Broad weapon alternatives
accept budget equipment; compound kits preserve ALL/ANY relationships. Recognition
proves supported ownership, not charges, ammunition, selected spell/style,
wielding levels or a full viable setup. See [gear curation](data/GEAR_CURATION.md).

Bank observation is off by default. Enable **Observe open bank** in Gear & preparation, then open
the bank normally. Only positive-quantity item observations are retained for this
login; placeholders and unseen items never prove absence. Reopen the bank to
refresh after changes. Disabling observation, logging out, hopping or switching
accounts clears the snapshot. Bank contents are not persisted or sent elsewhere.

## Export a route

Use **Export route** on Route to save a local Tasks Tracker JSON file. It preserves
boss/task order, static task notes and route-scoped preparation reminders. COMBAT
task IDs come from verified live completion-index-to-struct mappings. Missing or
duplicate mappings, stale account results and empty plans are refused.

Import the saved JSON manually in Tasks Tracker if you use it. CA Route works
without that plugin and does not send it messages or change its settings. The
schema is checked against revision
`dbdfdaddbdc09592e54f90056a5397df63f4e88a` with deterministic DTO round-trip tests.
The actual Tasks Tracker GUI import has not been verified in game.

## Run locally

Use Java 11 or a newer JDK capable of compiling Java 11. The checksum-pinned
Gradle wrapper downloads its distribution and dependencies on first use.

```powershell
.\gradlew.bat test jar
.\gradlew.bat run
```

On Linux/macOS, use `bash gradlew test jar` and `bash gradlew run`. The run task
launches the RuneLite development client with CA Route registered. The artifact is
`build/libs/ca-route-1.0.0.jar`; it is a plugin JAR, not a standalone client.
Restart a running development client to load changed classes. For Jagex-account
development login, follow the [official Hub development guide](https://github.com/runelite/plugin-hub).

The API baseline is RuneLite 1.13.1. Use `-PruneLiteVersion=latest.release` for an
explicit compatibility build. Tests produce scenarios under `build/scenarios/`
and synthetic sidebar previews. The new release-candidate features still require
a logged-in smoke test, including real CA completion/rerouting, all modes, manual
inputs, account switches and Tracker import. User feedback confirms that Start
task and its reminder work in a logged-in client; the new layout and remaining
flows still need client checks. See [validation status](docs/MVP_STATUS.md) and the
[external release checklist](docs/RELEASE_CHECKLIST.md).

The local verification passed **223 tests**, reproducible metadata and the
official standard-template build. See the [verification record](docs/RELEASE_VERIFICATION.md)
for the artifact hash and remaining in-client checks.

## Data, safety and limitations

The catalogue contains **655 tasks from the attributed 2026-06-28 CA Helper/Wiki
snapshot**. Bundled index/name/tier identities, weights, numerical thresholds and
completion points are checked against live client state. Added tasks permit a
verified known subset with an incompleteness warning; changed/reused identities
pause unsafe routing. Synchronization gaps wait or use a labeled threshold
fallback. Grandmaster and complete-boss claims require complete verified coverage.
See [live catalogue checks](docs/LIVE_CATALOGUE.md).

Imported access metadata is non-exhaustive. Encounter, setup and retry estimates
are heuristic; unobserved historic KC, missing gear evidence and unestimated
preparation stay explicit. A time-budget plan constrains modeled active ETA;
unknown preparation and calendar waits may add work. There is no automatic DPS
calculation, acquisition economy or general execution-overlap solver. Pairwise
travel is not optimized. Severe recommended-stat shortfalls produce preparation
advice unless stretch planning is explicitly enabled.

Account reads run on the client thread; calculation runs in the background, with
account/version checks before UI publication. Completion, skills, quests, items and
preferences trigger rerouting. Switching accounts/logout clears session details.
The active guide uses static task reminders, public entrance markers and visible
NPC highlights. There are no automatic hiscore/account network requests,
prayer/position predictions or game-action automation.

Customizations, observed KC and manual inputs are stored locally in RuneLite's
managed CA Route directory, separate from cloud-synchronized config. Existing
`caroute/` account files are migrated by RuneLite. Java code is
BSD-2-Clause. Wiki-derived metadata remains **CC BY-NC-SA 3.0** with separate
attribution, noncommercial and share-alike terms. See
[third-party notices](THIRD_PARTY_NOTICES.md), [metadata maintenance](data/README.md),
the [original audit](docs/CA_HELPER_AUDIT.md) and the
[implementation record](docs/IMPLEMENTATION_PLAN.md).

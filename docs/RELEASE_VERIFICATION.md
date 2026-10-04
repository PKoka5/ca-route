# 1.0.0 local verification

Verified on 4 October 2026 with Java 11 and Gradle 8.10.

- `test jar` with `-PruneLiteVersion=latest.release`: **223 tests, zero failures/errors/skips**.
  The resolved RuneLite release was 1.13.1, also confirmed from the public
  [RuneLite Maven metadata](https://repo.runelite.net/net/runelite/client/maven-metadata.xml).
- The final review rechecked the live Plugin Hub `runelite.version` and online
  dependency resolution: both select 1.13.1. Build/test now use the client's
  transitive Gson 2.8.5 rather than a separate version pin; the export round-trip
  test uses the matching parser API. Compatibility CI runs on every push/PR.
- Same-account route updates retain task search, filters, ordering and page;
  changed results clamp the page and account/logout boundaries reset it. Three
  panel integration regressions cover these cases.
- The sidebar opens on Tasks with Recommended selected and orders its tabs
  Tasks, Bosses, Route, Profile, Prep. Existing layout/control checks use tab
  titles when selecting Route or Tasks, so previews still exercise their intended pages.
  Filter checks cover Recommended's initial results, keeping a manually chosen
  Remaining filter during refreshes, and resetting to Recommended after logout
  or an account change. The 225-pixel Tasks preview was visually inspected.
- Account files use RuneLite's managed Filepath directory with migration from
  the previous `caroute` directory. File reads/writes run off the client thread;
  captured write snapshots are ordered across logout and plugin restarts.
  Asynchronous loads reject obsolete account/lifecycle results and preserve
  unreadable files. The explicit route-export chooser also uses Filepath.
  Three controlled-queue regressions cover restart write ordering, stale-load
  rejection before reads/publication and corrupt-file preservation.
- Static visible-NPC matching now recognizes verified encounter members for
  Guardians, Royal Titans, Moons, Barrows and the Leviathan/Whisperer names.
  Positive/negative name tests and a Dawn-arrival integration case verify this
  without broad substring, unrelated creature or completion inference.
- All 655 bundled tasks have standalone quick guides, totaling 1,704 numbered
  steps, with two to four steps per guide. Conversion validates full identity
  coverage, short text bounds and exact task-source attribution; generated
  metadata records guide revision, per-file hashes and per-task source URLs.
  The free-form upstream strategy text is not automatically imported. Incorrect
  tactics were rewritten, including unreachable Dawn melee hits and outdated
  Duke speed timing. Guide content remains static and separate from access facts.
  Repository tests cover loaded guide coverage and immutability; two UI checks
  cover independent disclosure, no actions/navigation, account reset and narrow
  word wrapping. The real bundled Wyrm view was rendered and inspected at
  225/280 pixels in `build/ui-task-quick-guide-*.png`.
  That visual review exposed clipping of the final line after a narrow width
  allocation. Wrapped text now measures its actual content width/last line and
  invalidates cached ancestor measurements when width changes. A regression
  checks the final visible character's bounds; all 51 UI tests passed afterward.
- After removing redundant boss/bundle explanations and the engine-reason
  tooltip, all 46 existing UI tests were rerun successfully. The narrower
  Wyrm view was visually inspected and both build variants were repackaged.
- `python data/convert.py --check`: metadata reproducibility passed.
  JSON source line endings are normalized to LF and enforced in Git attributes
  for Windows/Linux reproducibility. Only input provenance hashes changed;
  task content and other generated metadata were compared and preserved.
- Player preparation uses the full typed requirement tree rather than engine
  reasons. Required levels/quests/gear retain met, unmet and unknown states;
  nested alternatives stay alternatives and recommendations are separate.
  Wyrm 287 shows 62 Slayer, protective footwear and the elite Kourend & Kebos
  Diary exemption in Route, Bosses and Tasks. That exact account-aware view was
  rendered and inspected at 225 and 280 pixels. The sourced overlay contains
  41 equipment/access reminders for 20 activities; all 12 Alchemical Hydra
  tasks now use the verified 95 Slayer requirement. These facts remain separate
  from unchecked assignment/diary/ownership state.
- The final `src/main` compiled and packaged with the official
  [standard build](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle)
  and [settings](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-settings.gradle),
  supplying the client version externally as 1.13.1. This also checked production
  compilation against the client's transitive Gson 2.8.5.
- Both final JARs contain the achievement/gear resources, Wiki/difficulty and
  active-guide classes, the grayscale sidebar icon and all reviewed source/data notices,
  including RuneLite World Map and Quest Helper coordinate-data licenses.
  Neither contains test classes. Resources match their source files, the packaged
  icon matches the root Plugin Hub icon, and the standard-build fixture contains
  byte-identical final `src/main` files.
- The compact Swing Route overview and an expanded task were rendered with the
  actual `RuneLiteLAF` and visually inspected at 225, 235, 260 and 280 pixels.
  All 51 UI tests passed with the native look and feel, preserving the
  tab/control bounds checks. All five tabs fit, the 42-minute
  session metric remains visible with a scrollbar, and expanded details have
  usable controls and allocated height. Profile/Prep previews cover collapsed
  sections at 260 pixels and opened/nested controls at 235/280 pixels. The Wiki
  buttons and Easy to hard shortcut were also rendered at narrow sidebar widths.
  Bosses/Tasks previews at 225 pixels cover independent Wiki actions, native
  search and compact pagination. Preparation previews include native progress
  text; the 235-pixel gear estimate field visibly fits its `12.5` example alongside
  Set/Auto controls. The test JVM isolates RuneLite's font directory under
  `build/test-runelite`.
  The grayscale quest-style icon was inspected at native 18/32-pixel sizes.
  These previews use synthetic account facts, not a logged-in client.
  Headless control tests cover disclosure persistence/reset,
  filtering/pagination, manual inputs and removed controls/open menus after a new
  account render. Wiki tests verify separate boss/task targets, punctuation,
  all 655 canonical task titles, independent disclosure/link actions, stale-link
  guards and explicit mode switching. Queued export tests discard changed
  versions, logins and shutdowns.
  The subsequent Quest Helper layout pass replaces tall/wrapped title cards with
  flat single-line list rows, right chevrons and dark section bars. Full titles
  remain accessible through tooltips and accessibility names. Primary task filters
  were checked at 194/225/280 pixels, including compact display labels with full
  original values. Route's Goal/Ordering selectors make only explicit current-view
  changes; rerender, logout and account-switch guards reject detached controls.
  Active task headers use orange with black text and keep their exact condition
  visible. The native overlay preview now shows the selected instruction and known
  KC without duplicate branding or sidebar route-status text.
- Active guide checks cover all 655 bundled tasks across 91 activities, using
  66 distinct public points, 89 activity keys and two exact task overrides,
  immutable lookup and unsupported destinations; exact task selection, real map
  points, unknown/known KC, account/completion reset, arrival at public approaches
  and nearby matching bosses on the same plane. Loaded bosses at a distance or
  on another plane cannot end travel. No test awards game completion.
  Shortest Path tests exercise the real public path/clear message contract,
  unavailable plugins, own event echoes, identical-coordinate foreign messages,
  manual targets and lifecycle changes. Real EventBus tests verify ordering before
  default-priority foreign handlers, observation after owner unregistration,
  disposed queued events and rapid guide replacement without clearing foreign paths.
  Active guide/start/map/stop controls and collapsed Travel details were rendered
  and inspected at 225/280 pixels; detached controls remain inert after replacement
  or account reset. The task overlay was rendered with real RuneLite components
  into `build/ui-active-task-overlay.png`. These tests do not launch Shortest Path's
  pathfinder or establish that a real game route can be found.
  A reader-to-guide regression reproduces the reported Start task failure with
  an added live task: a safely loaded profile has incomplete total coverage, yet
  canonical task 398 activates its reminder/marker and sends the Giants' Den
  entrance to Shortest Path. A reread retains the guide. Changed live identities
  still produce an unloaded profile, stop the guide and refuse fresh selection.
  Every bundled task is selected through the real controller/bridge adapter and
  produces its own marker plus a path request for its recorded target. The two
  Giants-to-Wyrm regressions cover direct replacement, selection after arrival and
  foreign targets surviving arrival/Stop. Coverage is exported to
  `build/navigation/task-destination-coverage.csv`; the 40 added activity points
  were also checked against their pinned primary-source lines. These checks
  establish destination coverage and message delivery, not in-game pathfinding.
- Full-catalogue scenarios cover all six objectives, deterministic permutations,
  unique rewards, completion removal and the fresh-account guard. Independent
  small-subset oracles check covering/budget costs and shared gear preparation.
  Easy to hard tests additionally cover account readiness, distinct boss/task
  ratings, known versus unknown gear/KC, difficulty order, pins and a canonical
  partial-completion fixture with 79 completed tasks worth 118 points. In that
  synthetic fixture all K'ril tasks remain eligible and lower-difficulty tasks
  can cover the 51-point Medium gap; Easy to hard selects the easier route while
  Ironman preparation retains its preparation-oriented choice. The detailed
  output is in `build/scenarios/easy-to-hard-partial-progress.txt`.

Artifact: `build/libs/ca-route-1.0.0.jar`, 467967 bytes.

SHA-256:

```text
08e6c072c4c1d72065989da5a4bbacebce29ba09a20b598b8e37ad8842484199
```

These checks establish the local build and synthetic/integration test results.
User feedback confirms Start task, its reminder and the Giants travel path in a
logged-in client. The new destinations, remaining gameplay flows and actual Tasks Tracker GUI import still
need client checks. This is not a Plugin Hub packager/API-check CI result or
maintainer approval. Source and support are hosted at
[PKoka5/ca-route](https://github.com/PKoka5/ca-route); the Plugin Hub pull request
records submission, CI and review status. Follow the
[release checklist](RELEASE_CHECKLIST.md) for those external steps and
[status](MVP_STATUS.md) for data/model boundaries.

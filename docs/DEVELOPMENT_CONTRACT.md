# MVP implementation contract

Package: `com.caroute`. Java 11, JUnit 4, no Lombok. This original contract coordinated the initial implementation; 0.2.0 extends gear ownership, observations and live validation as documented in [current status](MVP_STATUS.md). Public domain values are immutable; constructors/builders defensively copy collections. The domain and engine import no RuneLite classes.

## Domain API (owner: engine agent)

Enums: `AchievementTier {EASY, MEDIUM, HARD, ELITE, MASTER, GRANDMASTER}` (`points()`, `displayName()`); `AccountType {MAIN, IRONMAN, HARDCORE_IRONMAN, ULTIMATE_IRONMAN}`; `QuestStatus {NOT_STARTED, IN_PROGRESS, COMPLETED}`; `Preference {NORMAL, PREFER_NOT, AVOID}`; `TaskFlag {SPEED, PERFECTION, WILDERNESS, GROUP, RAID, LONG_KC, RNG}`; `EligibilityState` as audited; `Confidence {HIGH, MEDIUM, LOW}`; `Reachability {REACHED, REACHABLE_NOW, CONDITIONAL, UNREACHABLE_NOW, UNKNOWN_PROFILE}`; `RouteObjective` includes six audited modes (MVP config exposes FASTEST_NEXT_TIER only until other policies have meaningful tests).

`CombatAchievement.builder(int id, String name, String activity, AchievementTier tier)` with builder methods `description(String)`, `type(String)`, `wikiUrl(String)`, `requiredSkills(Map<String,Integer>)`, `recommendedSkills(Map<String,Integer>)`, `quests(Map<String,QuestStatus>)`, `minimumGear(Set<String>)`, `recommendedGear(Set<String>)`, `flags(Set<TaskFlag>)`, `kcThreshold(int)`, `completionCount(int)`, `secondsPerKill(double)`, `baseAttempts(double)`, `difficulty(int)`, `setupMinutes(double)`, `requirementsKnown(boolean)`, `timingKnown(boolean)`, `overlapGroup(String)`, `provenance(String)`, `build()`. Accessors use corresponding method names without arguments; `points()` derives from tier. Explicit completionCount defaults to 1, kcThreshold to 0, unknown flags/evidence must retain diagnostics. Requirements metadata absence remains unknown, even when maps are empty.

`GearProfile.of(Set<Integer> equipped, Set<Integer> owned, Set<String> capabilities, boolean ownershipComplete)`, `unknown()`, accessors `capabilities()`, `ownershipComplete()`, `equipped()`, `owned()`.

`PlayerProfile.builder()` methods: `accountKey(String)`, `version(long)`, `loaded(boolean)`, `accountType(AccountType)`, `completedTaskIds(Set<Integer>)`, `currentPoints(int)`, `thresholds(Map<AchievementTier,Integer>)`, `skills(Map<String,Integer>)`, `quests(Map<String,QuestStatus>)`, `killCounts(Map<String,Integer>)`, `gear(GearProfile)`, `catalogueComplete(boolean)`, `build()`. Corresponding accessors. Missing entries are unknown. Default account type MAIN is only for synthetic fixtures; bridge maps unknown live type conservatively with a diagnostic.

`RoutingPreferences.builder()` methods `target(AchievementTier)` (null means next tier), `objective(RouteObjective)`, `preference(TaskFlag, Preference)`, `excludedTaskIds(Set<Integer>)`, `pinnedTaskIds(Set<Integer>)`, `deferredTaskIds(Set<Integer>)`, `allowStretch(boolean)`, `currentActivity(String)`, `build()`. Accessors, plus `preference(TaskFlag)`.

`Eligibility` accessors `state()`, `reasons()`, `confidence()`, `recommendedNow()` and `conditional()`; severe readiness omission remains AVAILABLE with a preparation reason. `TaskAssessment` accessors `task()`, `eligibility()`, `estimatedMinutes()`, `planningCost()`, `components()` (Map<String,Double>), `reasons()`, `confidence()`.

`BossSession` accessors `activity()`, `tasks()` (List<TaskAssessment>), `points()`, `estimatedMinutes()`, `planningCost()`, `reasons()`, `confidence()`, `conditional()`.

`RoutePlan` accessors `target()`, `currentPoints()`, `threshold()`, `pointsRemaining()`, `projectedPoints()` (gain), `estimatedMinutes()`, `stops()` (List<BossSession>), `assessments()` (List<TaskAssessment> all canonical tasks), `blockers()` (List<String>), `reachability()`, `confidence()`, `optimal()` (exact within declared model), `profileVersion()`.

## Engine API

`com.caroute.engine.RouteEngine` with `new RouteEngine()` and `RoutePlan plan(List<CombatAchievement>, PlayerProfile, RoutingPreferences)`. Optional constructor accepting `AcquisitionCostProvider` for account-specific tests. Canonical sorting, nested KC closure, per-boss options, pin setup sharing, separate physical/adjusted costs, GM completeness and unknown/profile states are required. One selected option per activity; unique disjoint activity rewards. General overlap unsupported means conservative independent costs and a documented limitation. All conditional stops remain explicitly conditional; blocked/preparation-first/excluded tasks cannot appear as definitely doable-now.

## Data API (owner: data/build agent)

`com.caroute.data.AchievementRepository.loadBundled()`, `tasks()`, `version()`, `diagnostics()`, `catalogueComplete()` (static snapshot identity completeness, not live certainty). Canonical JSON schema generated from upstream snapshot into resources, plus checked-in reproducible Python converter and source inputs under data/. Preserve BSD/CC licenses and attribution separately; never call Wiki data BSD. Add field provenance/knownness and explicit count units. Flags and gear uncertainty need diagnostics. No runtime network.

## RuneLite/UI (owner: bridge/UI agent)

`com.caroute.CaRoutePlugin`, `CaRouteConfig`, `bridge.RuneLiteProfileReader`, `ui.CaRoutePanel`; test entry `CaRoutePluginTest`. RuneLite 1.13.1 currently cached. Live numerical threshold varbits; 21 completion varps; real skills/quests; local observed/persisted KC; optional equipment capabilities with unknown ownership. Account-scoped pins/exclusions/snoozes/preferences; client-thread reads, background solver and account/version guard, EDT render. Default Route with next boss/tasks, physical ETA, points, confidence, explanation and following stops; Bosses/Tasks/Profile tabs. Do not initiate external hiscore or other account network calls. Initialize mid-login; clear on logout; handle skill, quest/completion variable, item and config changes. The user's later active-guide request authorizes static task reminders, public entrance/map/minimap markers, visible matching NPC highlights and optional Shortest Path messages; see [active guide](ACTIVE_GUIDE.md). Combat predictions, prayer/position recommendations and game-action automation remain outside scope.

Bridge startup creates repository and RouteEngine. UI receives RoutePlan and action callbacks; task commands cannot award game completion. Gear/profile overrides and exclusions are labeled as manual. Config exposes target/preferences/allowStretch; custom state is serialized locally per account. Derived tier thresholds are fallback only, lowering confidence. Catalogue mismatch/current game task detection must lower completeness; GM cannot claim complete when drift is unresolved.

Coordinate any API change with the other owners immediately; do not overwrite another owner's files.

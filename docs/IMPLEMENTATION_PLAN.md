# CA Route implementation record and remaining work

This dependency-ordered record follows the [Phase 0 audit](CA_HELPER_AUDIT.md).
**1.0.0-rc3** implements the functional local planner, six modes, direct shared
preparation, gear evidence/manual estimates and local Tracker export. The original
research plan remains broader than this release. See [current status](MVP_STATUS.md)
for delivered behavior and verification scope. The compact sidebar refresh is
described in [UI refresh](UI_REFRESH.md). Easy to hard now prioritizes the lowest
feasible activity/task difficulty range before planning efficiency, with direct
boss/task Wiki actions and the grey quest-star logo.
The subsequent [active-guide implementation](ACTIVE_GUIDE.md) adds explicit
selection, optional Shortest Path travel and static visual reminders.

## 1. Build, attribution and canonical data

Delivered: Java 11-compatible RuneLite scaffold, checksum-pinned wrapper,
pure-core tests, developer entry point and standard Hub metadata. The 655-task
snapshot has reproducible conversion, input hashes, field provenance/knownness and
canonical identity/value checks. BSD code notices and Wiki-derived CC BY-NC-SA 3.0
attribution remain separate.

Local release verification passed 217 tests, reproducible data, latest-release
compilation and the official standard-template build; see the
[verification record](RELEASE_VERIFICATION.md). Remaining external release work:
public repository/support URL, reviewed release commit and maintainer submission.
Local tests and this version label do not imply Hub approval.

## 2. Immutable profiles and eligibility

Delivered: client-independent tasks/profiles/gear/preferences/results; ALL/ANY
requirements with satisfied/unmet/unknown facts; authoritative completion; soft
recommended stats/gear; beginner-safe preparation and explicit stretch planning.
Exclusions, preferences, pins and deferred tasks preserve distinct meanings and
cannot bypass blockers. Missing metadata stays unknown.

Remaining curation: exhaustive area/diary/item/spell gates and validated minimum
setups. An empty supplied requirement map does not prove complete game access.

## 3. Costs, sessions and six policies

Delivered: physical active ETA separated from policy penalties, explicit
completion units, KC subtraction/competence, retries, shared setup/nested KC and
matching stamina sharing. User-reported preparation is shared per gear capability
across the selected route. Sorting/ties and unique rewards support reproducibility.

Fastest to target tier, Easy to hard, Minimum friction and Ironman
preparation pursue the selected tier with different explicit priorities. Most
points in time budget maximizes modeled points within active minutes. Complete
one boss scopes remaining coverage to the selected activity. Grandmaster and boss
completion preserve blocked tasks and require full verified live coverage.

Remaining modeling: general execution compatibility/overlap, cross-activity
implied rewards, pairwise travel and calibrated attempts/partial-failure durations.
Large groups use bounded candidate options; optimality is limited to the supported
model/candidates. Automatic acquisition economics and DPS calculation are absent.

## 4. RuneLite state and account-safe publication

Delivered: local skills/quests, account type, completion bits/points, thresholds,
observed KC and items. Live identity/weight/threshold checks distinguish an
added-task subset from changed/reused identities; unsafe mappings pause routing.
Client-thread reads, coalesced events, background solving and account/request
guards prevent stale EDT publication. Logout/account switches reset session data.

Remaining acceptance: exercise the full real completion-to-next-route loop and
synchronization/reset behavior in the new logged-in client. SDK adapter tests and
synthetic renders do not replace that smoke test.

## 5. Sidebar actions and gear preparation

Delivered: Tasks/Bosses/Route/Profile/Prep (Tasks selected initially); task filtering; per-account
goals/preferences/pins/exclusions/snoozes/restoration; session skips; manual
historical KC; session-only current-activity setup choice. The gear overlay has
29 capabilities and 83 soft recommendations across 14 activities, including budget
alternatives/compound recipes. Auto/Owned/Missing preserve positive, missing and
unknown ownership. Optional bank observation is positive-only and session-local.
Manual active preparation minutes are labeled, local and removable.

Remaining curation: broader loadouts, charges/ammunition/skill/spell readiness and
calibrated account-specific acquisition. Unobserved banks never prove absence;
manual minutes are not a verified grind or calendar forecast.

## 6. Direct shared prerequisite advice

Delivered: Prepare groups supplied skill/quest/gear/access requirements and soft
readiness across included remaining tasks. Shared skill targets/quest statuses are
combined; satisfied alternatives avoid false training/gear advice, while unknown
branches remain verification. Steps list related tasks/points and distinguish hard
unlocks from recommendations.

Remaining scope: complete quest dependency graph, optimal prerequisite order,
calibrated training/resources/calendar economics. Rewards can overlap between steps
and are not guaranteed by doing one action. Direct advice is not a complete
unlock-route optimizer.

## 7. Local Tasks Tracker export

Delivered: explicit local JSON save using the pinned COMBAT schema, verified live
struct IDs, route/boss/task order, static notes and route-scoped preparation
reminders. Stable IDs and DTO round-trip tests preserve the supported format.
Missing/aliased mappings, duplicates, empty plans and stale results are refused.
There is no hard dependency or automatic message/import into another plugin.

Remaining acceptance: import an exported file with the actual Tasks Tracker GUI
and record its installed revision/results. Schema tests do not claim that GUI
integration ran successfully. Goal Planner sharing stays outside this release.

## 8. Verification and release exit

Test contracts cover requirements/readiness, all six scopes, independent exhaustive
covering/budget oracles, shared work, permutations/unique rewards, canonical golden
routes, SDK/cache adapters, migration/storage, stale results, gear rules/IDs, bank
evidence, sidebar controls/rendering and export DTOs. The
[local release verification](RELEASE_VERIFICATION.md) records the successful
release-candidate build/test result.

Release exit still needs the logged-in new-feature smoke test, Tracker GUI import,
actual Hub CI checks and external maintainer review. Preserve local privacy,
static progression/travel guidance and explicit uncertainty. The
[release checklist](RELEASE_CHECKLIST.md) names the external steps and guidance.

## 9. Later research

Broader evidence-backed access/gear curation, complete prerequisite graphs,
calibrated account-aware acquisition/training, overlap/travel and optional local
attempt/feedback calibration remain separate improvements. Population signals
and personalized estimates need provenance/validation. Opaque ML and automatic
purchases/grinds are not part of 1.0.0-rc3.

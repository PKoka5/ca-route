# CA Route 1.0.0 status

The 1.0.0 submission includes the local progression planner,
six policies, direct shared preparation, curated gear evidence/manual estimates
and local Tasks Tracker JSON export, plus explicit task/boss travel guidance and
visual reminders. Source and support are hosted at
[PKoka5/ca-route](https://github.com/PKoka5/ca-route). Plugin Hub publication
requires CI and maintainer approval; remaining gameplay-flow verification and
actual Tracker GUI import are outstanding.

| Area | Delivered behavior |
| --- | --- |
| Build | Java 11-compatible source, checksum-pinned Gradle 8.10, RuneLite 1.13.1 baseline, developer entry point and standard Hub metadata |
| Domain | Immutable RuneLite-free tasks/profiles/preferences/results, three-valued hard requirements and direct preparation steps |
| Eligibility | Game completion/access states, soft stat/gear readiness, unknown facts, beginner-safe preparation and explicit stretch choice |
| Modes | Fastest to target tier, Most points in time budget, Easy to hard, Complete one boss, Minimum friction and Ironman preparation; new settings default to Easy to hard |
| Costs | Physical active ETA separated from planning penalties; KC, setup/retries, user-reported preparation and unknown acquisition evidence |
| Sessions | Shared setup/KC, explicit matching stamina conditions, unique rewards and capability preparation shared across the selected route |
| Solver | Deterministic covering/budget policies, independent small-instance checks, bounded large-group options and honest scope/optimality |
| Prepare | Direct skill/quest/gear/access gaps grouped across included remaining tasks; hard unlocks, verification and soft advice stay separate; related rewards are not promised instant unlocks |
| Data | 655-task snapshot, input hashes, field evidence/diagnostics and deterministic converter |
| Task guides | All 655 bundled tasks have standalone, sourced quick guides of two to four steps; independently collapsed in Tasks, Route and Bosses, without action or travel side effects |
| Live catalogue | Bit-index/name/tier, weights, thresholds and completion-point checks; safe added-task subset, synchronization retry and unsafe-identity pause |
| Gear | 29 capabilities, 83 identity-checked soft overlays across 14 activities, budget substitutes/compound recipes, positive ownership and missing versus unknown |
| Controls | Per-account goal/mode/budget, selected boss, pins/exclusions/snoozes, gear/preparation estimates and observed/manual KC; session-only skips/current-activity choice |
| Bank | Optional positive public open-bank observations; session/account reset; no absence inference or persistence |
| Runtime | Client-thread snapshots, coalesced events/unchanged-input suppression, background solver and guarded EDT publication |
| Sidebar | Quest Helper-style flat title lists with right arrows, labeled primary filters, dark section bars and orange active-task header; Tasks/Bosses/Route/Profile/Prep with Tasks selected initially, independent Wiki actions, grey quest-star icon, compact icon metrics, visible partial-route status, disclosures and guarded manual controls |
| Active guide | Explicit task/boss selection; all 655 bundled tasks/91 activities have destinations at 66 distinct public approaches, including two exact task overrides; optional Shortest Path messages, own world-map marker, static task/known-KC reminder, nearby entrance/minimap markers and visible matching NPC highlight |
| Export | Ordered COMBAT sections, verified live struct identities, scoped preparation/static reminders, deterministic IDs and unsafe-input refusal |
| Privacy/scope | Local processing/storage; no plugin account-data requests, automatic hiscores, combat predictions or game-action automation |

Verification coverage includes requirement alternatives/unknowns, readiness,
session/KC/preparation sharing, policy differences, exhaustive covering/budget
oracles, canonical permutations/unique rewards, real-catalogue golden scenarios,
SDK/cache adapters, stale results, account persistence/migration, gear recipes/IDs,
bank opt-in and sidebar controls/rendering. Export tests mirror the pinned public
Tasks Tracker JSON fields and round-trip those DTOs; they do not execute its GUI
importer. The local verification passed **223 tests with zero failures**,
metadata reproducibility and the official standard-template compile/JAR check.
See [release verification](RELEASE_VERIFICATION.md) for evidence and the artifact hash.

The logged-in smoke test must still exercise cache/account synchronization,
actual CA completion and subsequent recommendations, all six modes,
gear/preparation/KC inputs, bank observation, account changes, restart and
export/import and active-guide/Shortest Path ownership and visuals. User feedback
confirms Start task, its reminder and the Giants travel path in a logged-in client.
The new destinations and remaining flows still need client checks. No Hub CI/reviewer approval or published
release is claimed.

Known data/model boundaries:

- Access metadata is non-exhaustive. Encounter/setup/retry estimates are heuristic;
  unknown history/ownership is not verified zero or absence.
- Prepare covers supplied direct requirements, not a complete quest dependency
  graph, optimal prerequisite order or calibrated skill-training plan. Related
  point counts can overlap between steps.
- Gear coverage is a subset. Recognition does not verify ammunition, charges,
  spellbooks, wielding requirements, selected styles or a complete setup.
- Manual active preparation minutes are user estimates and can cover missing or
  unconfirmed gear. Other acquisition, training, resource and calendar-wait costs
  remain unestimated; budget coverage concerns the supported active-time model.
- Unsupported general execution overlap, cross-activity rewards and pairwise
  travel are not optimized. Large groups use bounded options with declared limits.
- New live tasks need metadata updates. Grandmaster/complete-boss claims require
  coherent full identities, numerical data and account progress.
- Guide destinations are public approaches, not instanced arena coordinates or
  proof of access. Unknown future activities keep reminders without map targets.
  No dynamic combat-condition detection or combat-position advice is implemented.

Repeat `gradlew test jar`, `python data/convert.py --check` and the explicit
`-PruneLiteVersion=latest.release` check after further changes. Use `gradlew run`
for the remaining client smoke test. See the [README](../README.md),
[gear curation](../data/GEAR_CURATION.md), [live catalogue checks](LIVE_CATALOGUE.md)
and [external release checklist](RELEASE_CHECKLIST.md).

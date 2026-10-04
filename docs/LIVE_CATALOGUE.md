# Live CA catalogue validation

`LiveCatalogueValidator` reads game-cache definitions through the public RuneLite API, on the client thread after login. It sends no requests and uses no game action, packet API or reflection. The public adapter API was inspected and compiled against the pinned RuneLite **1.13.1** SDK. The numeric cache IDs below come from the inspected tracker source references available on **3 October 2026**; they have **not been empirically checked in a logged-in game client** during this implementation. They are revision-specific definitions, not promised stable API constants. Runtime validation is designed to detect incompatible mappings before authorizing completion reads.

| Identity or value | Cache definition | Validation |
| --- | --- | --- |
| Task structs by tier | Enums 3981–3986, Easy through Grandmaster | Read every integer enum value as a struct ID; enum keys are not completion indices. Reject unreadable/empty enums and duplicate structs. |
| Completion index | Struct integer param 1306 | Compare every bundled index, reject missing/reused indices and indexes outside the supported packed-word capacity. |
| Task name | Struct string param 1308 | Compare exact wording/case/punctuation after stripping game formatting tags and normalizing whitespace/NBSP. No fuzzy matching or aliases. |
| Task tier | Struct integer param 1310 and the enclosing tier enum | Both must agree with the bundled task tier. |
| Points per tier | Enum 4846 | Require exactly the six supported weights, Easy = 1 through Grandmaster = 6. A known weight change suppresses routing. |
| Numerical tier thresholds | Named `VarbitID.CA_THRESHOLD_*` varbits | Read positive values, require strict increase and cumulative live task-tier weights. Grandmaster must equal the total weight of all live tasks. Missing values remain absent. |

The public API calls are `Client.getEnum(int)`, `EnumComposition.size()/getKeys()/getIntVals()`, `Client.getStructComposition(int)`, `StructComposition.getId()`, inherited `ParamHolder.getIntValue(int)/getStringValue(int)`, and `Client.getVarbitValue(int)`. The adapter checks enum-array shapes and reads each struct's own completion index, so struct IDs, enum keys and completion indices remain separate.

`Result.snapshotSafe()` authorizes reading the bundled completion bits only after **every bundled task's identity has matched**. New live tasks can leave the known subset safe, but make full coverage incomplete. A missing/renamed/re-tiered/reused bundled ID, ambiguous struct, unreadable identity, or changed tier weight suppresses routing until resolved. An unavailable cache is a retryable `UNAVAILABLE` result, not a task requirement lock.

`Result.identityComplete()` requires full task coverage in both directions. `Result.catalogueComplete()` additionally requires verified tier weights and matching live numerical thresholds. Missing optional numerical values keep known identities safe, but prevent a Grandmaster completion claim. Positive live thresholds are exposed separately so the caller can use a clearly labelled snapshot fallback where values have not synchronized.

The account's current point total is not an input to identity validation. Equal totals cannot prove that an ID still refers to the same task. Per-account completion/point consistency remains a separate bridge check after safe identity validation.

The bridge caches a settled result for the current login and invalidates it on account/login/world changes. `Result.retryable()` records any unavailable source fact independently of the displayed status: a transient drift observation together with an unreadable enum or threshold still retries after synchronization. The validator itself retains no client or account state. A successful headless fixture demonstrates the contract, not live-game compatibility: the six enum identities, cache initialization timing and numerical values still need observation in the running client after updates.

Limits: identity validation covers completion index/name/tier and points, not every task description/access/gear/timing field. Bundled estimates and access metadata retain their own uncertainty even when the live catalogue is verified. New tasks require a reviewed metadata update before route recommendations can include them. A future change of the six-tier representation, param semantics or completion-word storage fails conservatively and requires an adapter update.

Evidence:

- [Pinned RuneLite 1.13.1 Client API source](https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-api/src/main/java/net/runelite/api/Client.java), [EnumComposition API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/EnumComposition.html), [StructComposition API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/StructComposition.html).
- [Combat Achievements Tracker loader at the audited revision](https://github.com/ehubbartt/combat-achievements-tracker/blob/4328d91bbc61e217e499be6ec5fd8fb394ebebc4/src/main/java/com/catracker/util/CombatAchievementsDataLoader.java): tier enums 3981–3986 and struct params 1306/1308; inspected from the pinned local audit checkout.
- [Tasks Tracker's own task-type definitions](https://github.com/osrs-reldo/task-json-store/blob/main/task-types.json): `COMBAT` param definitions, tier-point enum 4846, and the distinction between completion index and struct identity. Its static point thresholds and completion-word list are not used; the caller reads current named RuneLite definitions instead.
- [Tasks Tracker task identity at its audited revision](https://github.com/osrs-reldo/tasks-tracker-plugin/blob/dbdfdaddbdc09592e54f90056a5397df63f4e88a/src/main/java/net/reldo/taskstracker/data/task/TaskFromStruct.java): `getTaskId()` is the struct ID, `getVarpIndex()` is the separate ID param.
- [RuneLite's pinned named threshold varbits](https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java).

Tests cover all 655 bundled identities with shuffled live order; distinct struct/bit IDs; additions despite unchanged account points; reused/missing/renamed/re-tiered IDs; duplicate indices; cache read failures; unknown or mismatching numerical thresholds; altered tier weights; unsupported future completion indices; strict name normalization; client-thread and logged-in gating; and the SDK-facing adapter's actual param/enum calls.

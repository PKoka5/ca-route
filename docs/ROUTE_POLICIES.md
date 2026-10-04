# Route objectives

The route solver keeps physical active-time ETA separate from named planning-cost
units. User Avoid/exclusion, completion and access facts are applied before each
objective. Prefer-not costs remain in the baseline. Pins are mandatory when
incomplete and eligible; unavailable, excluded, missing or out-of-scope pins are
reported instead of being silently treated as fulfilled.

Four objectives cover the selected CA tier. Next tier advances from live points.
Grandmaster still requires every remaining task and verified catalogue completeness,
regardless of those four priorities. Difficulty, readiness and preference weights
are versioned heuristics, never verified DPS, purchase prices or elapsed training.

| Objective | Per-session policy |
| --- | --- |
| Fastest next tier | Existing baseline: physical encounter/setup/known active preparation plus stat, gear, learning, coordination, preference and uncertainty costs. |
| Easy to hard | First minimize the highest account-adjusted activity/task stage needed to cover the tier gap and eligible pins. Within that range use baseline penalties plus one quarter of physical minutes, four times the squared hardest stage, twice retry work and twice stat-readiness cost. Display selected stops/tasks from easier to harder. |
| Minimum friction | Baseline plus 12 units for each activity trip, three times setup minutes and twice group coordination. This favors fewer trips and less organization. |
| Ironman efficient | Baseline plus twice known active preparation, 24 units for each distinct explicitly missing recommended capability, 16 for unknown ownership and 10 per RNG task. Ownership and account-aware acquisition evidence matter; unknown calendar time/resource cost stays unknown. |

Easy to hard keeps the persisted enum key `EASIEST_PROGRESSION`. The stage starts
with the higher of the hand-rated boss mechanics baseline and task challenge;
these are separate from the game's CA reward tier. Known recommended-stat gaps
and explicitly missing recommended gear increase the stage. Unreadable recommended
stats add uncertainty without inventing skill levels. Unknown gear is not declared
missing, and unknown KC earns no experience discount. Recorded KC gives a bounded
learning discount; it does not bypass access/readiness eligibility.

The lowest range is determined from unique eligible remaining rewards. An eligible
harder pin raises the range; excluded or inaccessible pins remain conflicts.
If all eligible points still cannot reach the target, the useful partial route
retains its blockers. Grandmaster must still cover every remaining canonical task,
so it orders that mandatory work by difficulty without filtering out harder tasks.
The activity/task ratings come from the attributed upstream curation and remain
heuristics, not official difficulty ratings or a measured success probability.

Most points in time budget (`MAXIMUM_POINTS_PER_HOUR`) maximizes projected CA points
within the configured 1–1440 active-minute horizon, default 60. It does not stop at
the selected tier's gap and does not claim an ETA to that tier. Ties choose shorter
physical ETA, then lower planning cost, then stable canonical task order. Available
points remain indivisible task rewards; an arbitrary task is not partially credited.
An incomplete pinned bundle must fit the budget. If no combination of the mandatory
bundles fits, the result reports the conflict and does not return a misleading
un-pinned substitute. Time is compared directly; no hidden time-bucket rounding is
used. Unknown preparation can exceed the horizon and remains a confidence warning.

Boss completion covers every remaining task at the explicitly selected activity.
Excluded, locked, deferred and unsuitable tasks stay as blockers. An incomplete pin
from another activity is a scope conflict. Activity completion requires a verified
catalogue; a completed activity can be REACHED while the selected tier remains far
away. This scope never claims whole-tier or whole-catalogue completion.

`RoutePlan.scopeComplete()` means that the requested route scope is fully represented
and feasible under its known facts. For a tier route this means enough points and
no pin conflict; for the budget mode it means a horizon with at least one projected
point satisfying pins, or an already completed verified catalogue;
for boss completion it means every remaining selected-activity task is represented.
CONDITIONAL access remains conditional. Only REACHED means the corresponding goal
is already completed in the observed game state. A zero-point budget with remaining
tasks is UNREACHABLE_NOW and reports whether tasks are unavailable or the budget is
too short. A missing pin prevents a Grandmaster REACHED claim even if every mapped
task is already completed.

Options are grouped by activity with shared setup, KC progression, explicit overlap
and acquisition evidence. Each exact point-total retains its best session according
to the active policy. The budget mode retains the shortest physical-time session,
then policy cost, because that is its actual optimization order. Ordinary covering
DP retains the least policy cost. With at most ten optional tasks per activity,
all subsets are evaluated; larger groups use deterministic single-task and ordered
prefix candidates and mark the route non-optimal. Exactness claims apply only to
this supported independent-activity cost model. Boss rankings use the same actual
preferences, including current activity and setup savings, as selected routes.

User-reported ownership acquisition is paid once per capability across the whole
selected route, not once per activity. For example, two one-minute activities
sharing a ten-minute upgrade occupy twelve active minutes together. Acquisition
sharing also applies to pinned bundles, Grandmaster, and the Ironman/easiest policy
weights. Each displayed stop includes only the acquisition that has not already
been assigned to an earlier displayed stop; stop ETAs therefore sum to route ETA.
Standalone boss rankings still show their own preparation because they are
alternative candidate trips, not already selected stops.

When positive acquisition estimates exist, session candidates retain a separate
choice for each exact point total and acquisition signature. The route DP then
tracks points and the acquired capability set. It only prunes a label against the
same point total and preparation signature, using policy cost for tier routes or
physical time then policy cost for budget routes. A cheaper standalone option
cannot erase another option's later shared-preparation benefit. No positive
acquisition estimates use the original additive DP, retaining the default route
fixtures and normal account performance.

The shared-signature search is bounded to 5,000 labels after an activity and 500,000
transitions during one activity. Exceeding a bound deterministically retains
feasible candidates, reports the limit, and sets `optimal=false`. Larger activity
option groups also retain their existing non-optimal flag. Shared acquisition does
not invent time for an unestimated upgrade, combine distinct capability estimates,
or infer ownership from a partial bank. Gear/stat risk penalties remain per activity;
reported acquisition time is the reusable resource that is shared between trips.

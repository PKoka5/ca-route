# Metadata maintenance for 1.0.0

`upstream/` is the attributed CA Helper snapshot at commit
`25ebeeb09069ef544f7c10a5219bc1a321b88cf7`, canonical version 2026-06-28 with
655 tasks. Code and data have separate licensing; Wiki-derived metadata remains
CC BY-NC-SA 3.0. See [third-party notices](../THIRD_PARTY_NOTICES.md).

Run `python data/convert.py` to regenerate
`src/main/resources/com/caroute/achievements.json`; use
`python data/convert.py --check` for reproducibility. The converter records input
hashes/revisions. Review completion indices, exact names, tiers and struct
identities against the live cache before updating. A completion index is not a
Tracker struct ID. Runtime loading stays offline.
JSON source files use LF line endings, enforced by `.gitattributes`, so provenance
hashes and generated metadata remain identical on Windows and Linux CI.

Imported requirements are non-exhaustive: area, diary, item, spell and other access
facts remain incomplete. Supplied known skill/quest gates are evaluated; unresolved
access remains conditional verification. Encounter/setup/retry timing is heuristic.

`player-preparation.json` supplies reviewed, concise player-facing equipment and
access reminders, with a Wiki source for each note. The converter includes them
as `playerPreparation` and retains their sources, version and input hash. These
reminders are advisory and do not add hard gear locks or claim that a diary,
assignment or worn item has been checked. The sidebar also displays the complete
typed requirement tree, including already satisfied levels and quest progress;
engine cost/coverage diagnostics are not used as task preparation text.
The overlay currently covers 20 activities with 41 practical notes. Its sourced
`requiredSkills` correction raises every Alchemical Hydra task to 95 Slayer;
unrelated imported levels and task identities are preserved.
Missing historic KC and unseen ownership stay unknown rather than zero/absence
or an invented lock. Never promote inferred/missing fields to verified metadata.

The converter also imports `boss_difficulty.json` as `activityDifficulty` while
preserving `task_difficulty.json` as the separate task challenge. These are
attributed hand-rated mechanics estimates on a 1..10 scale, not CA reward tiers,
official game ratings or measured success chances. Missing activity ratings use
the task rating as an explicitly recorded fallback. Easy to hard uses both plus
known account readiness/experience to choose its lowest feasible difficulty range.

KC thresholds use Kill Count wording with the reviewed list in `count-review.txt`.
Execution numerals for adds, seconds, team size and phases do not multiply full
encounter counts. Explicit repeats of the named activity retain their count;
identical repeated conditions can share work. Other repetitions can retain an
unvalidated one-encounter baseline and need curation. General simultaneous
execution/KC overlap is not inferred from prose. Soft combat styles retain
alternatives instead of requiring every listed style.

`gear-capabilities.json` is the editable ownership/recommendation overlay. Keep
the resource copy under `src/main/resources/com/caroute/` byte-identical. Tests
check equality, exact task ID/name/activity, references/cycles and official SDK
item IDs. Version 2 covers **29 capabilities, 83 task recommendations and
14 activities**. Primitive IDs come from the pinned RuneLite SDK; style/kit
groupings are explicit soft heuristics. See [gear curation](GEAR_CURATION.md)
for sources/alternatives and deliberate omissions. The overlay adds no hard gear
gates, DPS guarantees or automatic BiS grinds.

Equipment/inventory and optional positive open-bank observations provide ownership
evidence. Manual Owned/Missing choices apply per account/capability; unseen bank
items stay unknown. User-supplied active preparation minutes and KC are profile
inputs, not source-data validation or measured game timing. Reported preparation
is counted once per capability across the selected route, including unconfirmed
ownership when an estimate is supplied. Charges, ammunition, wielding/spell/style
readiness, waiting/resource costs and acquisition economy remain incomplete.

Prepare groups direct requirements/readiness and preserves alternatives. It is
not a complete quest dependency graph, quest-order optimizer or training-time
catalogue. Related task points can appear on several steps and are not guaranteed
unlock rewards. Unknown quest/training/acquisition/calendar duration remains
unestimated.

Tracker export uses verified live index-to-struct mappings and the pinned JSON
schema, exporting selected tasks and scoped preparation reminders. It performs
no automatic import. Schema/DTO round trips do not replace testing the actual
third-party GUI importer.

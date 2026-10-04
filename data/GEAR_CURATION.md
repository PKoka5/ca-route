# Gear capability overlay v2

`gear-capabilities.json` is the editable source. The identical file under
`src/main/resources/com/caroute/` ships in the JAR; a test checks the byte equality.
It supplements the imported CA metadata without changing `convert.py`'s output.

Item numbers were checked against the pinned RuneLite 1.13.1 `ItemID` constants.
[RuneLite's generated item catalogue](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/ItemID.java)
is the primary mapping source, pinned to tag `runelite-parent-1.13.1`. A regression
test checks every recognized item ID against that SDK's public `ItemID` constants.
The catalogue covers 29 capabilities and 83 task overlays across 14 activities;
it remains a subset of equipment. Recoloured active Bowfa/crystal IDs are included; Gauntlet, beta,
inactive and uncharged Bowfa/crystal appearances are excluded. A recognized item
proves ownership only: ammunition, remaining charges and wielding levels remain
separate, currently incomplete checks.

[Jagex's 2021 equipment update](https://store.steampowered.com/news/posts/?enddate=1625047625&feed=steam_community_announcements)
documents Bowfa's synergy with full crystal armour. The supported accuracy kit is
Bowfa plus helm/body/legs, or a Twisted bow. Applying this kit as a soft preparation
recommendation for Zulrah's three speed tasks is a local planning heuristic, not
an official best-in-slot ranking or a guarantee that another method cannot work.
No inferred DPS improvement or new kill-time multiplier is applied.

[Jagex's Scurrius release](https://store.steampowered.com/news/posts/?enddate=1706093163&feed=steam_community_announcements)
documents the rat-specific benefit of the three ratbane weapons. The overlay
recommends any one of these weapons for Scurrius tasks. The rule adds soft readiness
cost; it does not introduce hard access locks. Even the canonical `Sit Rat` wording
remains available under the existing incomplete-requirements model until a broader
task-restriction curation pass covers weapon availability and wielding readiness.

The expansion reviews exact task IDs, names and activities against the bundled
655-task canonical catalogue. Weapon-style context comes from the attributed
[CA Helper setup snapshot](https://github.com/opaf-osrs/combat-achievement-helper/blob/25ebeeb09069ef544f7c10a5219bc1a321b88cf7/src/main/resources/com/pluginideahub/combatachievements/task_detail.json).
That snapshot remains Wiki-derived CC BY-NC-SA 3.0 data; opaf and OSRS Wiki
contributors are credited in the JSON attribution and project notices. Alternative
groupings and their application to tasks are local soft planning heuristics.
The [Barrows strategy reference](https://oldschool.runescape.wiki/w/Barrows/Strategies)
supports multiple styles and a salamander option; the
[Vorkath strategy reference](https://oldschool.runescape.wiki/w/Vorkath/strategies)
describes ranged and stab approaches. These are source context, not verified
account readiness, official item rankings or an exhaustive list of viable methods.

| Activity | Reviewed soft recommendation |
| --- | --- |
| Barrows | Any supported melee/ranged/magic/salamander for ordinary clears; distance or magic for the corresponding restrictions |
| Vorkath | Stab weapon, crossbow or blowpipe; melee-only uses common melee alternatives; fists-only is omitted |
| Kraken | Powered magic, Iban's staff or elemental casting staff; usable spells/runes/charges stay unverified |
| Sarachnis | Crush, ranged or magic alternatives; the crush restriction recommends a supported crush weapon |
| Dagannoth Prime / Rex / Supreme | Their respective ranged / magic / melee style; the three-king task receives an ALL kit containing each style |
| Fight Caves | Broad ranged alternatives including a rune crossbow; melee-only receives a separate melee recommendation |
| Kree'arra | General ranged alternatives; salamander-only receives a soft black/Tecu recommendation |
| General Graardor | Multiple weapon styles; freeze/access/no-damage conditions remain unverified |
| Commander Zilyana / K'ril | Melee or ranged alternatives; melee-only receives a specific melee recommendation |
| Scurrius / Zulrah speed tasks | Existing ratbane / Bowfa-full-crystal-or-Twisted-bow recommendations are retained |

No new coverage is applied to the reviewed golden route's Tormented Demon and
Maggot King activities. Special-attack-only Barrows, the thrownaxe bounce,
indirect-damage Kree/Zilyana, fists-only Vorkath and K'ril demonbane-only tasks are
omitted from generic kit mapping to avoid contradictory preparation advice.
Omission leaves their gear evidence unknown. Salamander and elemental-staff
recognition is ownership evidence; it does not prove the selected style or spell.
The broad alternatives avoid requiring a dragon hunter lance, trident, Bowfa or
other expensive item solely because it appears in a common strategy.

Each task overlay checks its numeric ID, exact name and activity before loading.
Changing the canonical completion mapping cannot silently reuse a recommendation.
The loader also rejects duplicate definitions/tasks/item IDs, non-positive IDs,
unknown capability references, empty recipes and cyclic recipes.

Positive equipment, inventory or optional bank observations never establish that
another capability is missing. Manual absence is per capability. Observed positive
facts win over conflicting manual absence. An owned single-recipe compound kit
also confirms its component ownership; an owned alternative kit does not choose
an arbitrary branch. An unavailable component makes an ALL kit missing; an ANY kit
stays unknown while another alternative is unknown.

Missing or unconfirmed recommended gear adds the existing 8 planning-cost units,
which are not elapsed minutes. Unknown ownership lowers confidence. The default
provider has no independently verified acquisition durations. Profile can supply
a labeled, user-reported active preparation estimate for a recommended capability,
including when ownership is unknown. That active preparation is counted once per
capability across the selected route; confirmed ownership removes the need.
Unestimated preparation remains excluded from the physical ETA, and waiting time
and resource costs remain unknown. Manual minutes do not assert a measured grind,
purchase, GP cost or calendar duration. Same-stat accounts can get different
rankings from gear evidence and reported preparation without fabricated DPS.

The immutable UI API exposes `definition(id)`, `recommendedCapabilities()`,
`recommendedDefinitions()` and `recommendedDefinitionsFor(activity)`. Recommended
definitions contain validated labels and are sorted by label. `recommendations()`
also exposes task identity, source and rationale; `Definition.itemIds()` and
`Definition.alternatives()` expose the immutable recipes for explanation. Each
inner recipe is ALL, with ANY alternatives across recipes. These APIs do not
create player facts or manual ownership decisions.

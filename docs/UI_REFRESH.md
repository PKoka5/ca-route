# Quest Helper-style sidebar — 1.0.0-rc3

The sidebar has a white CA Route title and compact native tabs. Route exposes
Goal and Ordering as labeled dropdown rows, followed by progress and the next
activity. A single icon row shows task count, projected points and estimated
active time. Dark section bars divide the content rather than large boxed cards.
Task and boss disclosures use flat, regular-font title rows about 25 pixels high,
with a square arrow on the right. List titles stay on one line with an ellipsis;
the full title remains in the tooltip and accessible name. Clicking a title
reveals that task's tier/points, status, exact description, estimate, actions
and Wiki guide. Detailed requirements remain behind a second disclosure.

Requirements & gear presents the full typed skill/quest/equipment list with the
current account level or progress and colored status markers. Alternative
requirements stay grouped as alternatives. Equipment & access adds concise,
sourced player reminders such as Wyrm heat protection and the elite Kourend &
Kebos Diary exemption; these reminders do not claim an item is worn or an
assignment/diary is verified. Recommended levels and gear are shown separately.
Task cards no longer put route-cost diagnostics, unknown KC assumptions or
timing heuristics into the player's preparation list.

Upcoming stops, route totals and requirement lists start closed. Redundant
boss/bundle selection explanations and the engine-reason tooltip have been
removed; the overview shows the activity, task list, rewards and time estimate.
Conditional access keeps a visible check indicator, and incomplete coverage
keeps a visible Partial route indicator. Hiding explanations does not turn an
unknown requirement into verified access or an incomplete route into a complete one.

Tasks keeps Filters, Difficulty and Ordering visible above the native search
field and initially selects Recommended. More filters contains the optional task
type and starts closed. Compact
display labels retain their full selected values and tooltips. Filtering, sorting
and 50-task pagination keep their existing semantics. Completed titles are green;
excluded/deferred titles are muted; ordinary available titles are white. Exact
readiness/access status remains in the expanded task and its tooltip.

Search, filter selections, ordering and pagination survive inventory/equipment
updates and other same-account route refreshes. A changed result set clamps the
page; a removed task type falls back to All. Account changes and logout reset
these transient presentation choices to Recommended, with fresh guarded action callbacks.

Boss sessions, profile inputs, individual gear capabilities and preparation steps
use the same right-arrow disclosure pattern. Only the inputs for the
selected route mode are shown. Preparation skill progress is visualized when
the current level is known; unknown levels are not presented as zero.
All five tabs have compact labels and an orange selected-tab underline.

Open sections survive route refreshes and task filtering for the same account.
Logout and account changes reset them. Each view has separate task disclosure
keys; opening a Tasks entry does not open its Route copy. This is transient UI
state and is not stored as an account fact. Existing action controls retain
their render-generation guards, including controls removed by a new render.

The inspiration is Quest Helper's labeled filter form, flat selectable lists and
orange active-step treatment:
[QuestHelperPanel](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/panel/QuestHelperPanel.java),
[QuestStepPanel](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/panel/queststepsection/QuestStepPanel.java)
and [QuestSelectPanel](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/panel/QuestSelectPanel.java).
The disclosure components, vector icons and styling here are original project
code; no Quest Helper source or assets were copied.

The subsequent 1.0.0-rc3 update adds independent Wiki-link icons beside task
headings and boss names. Opening a link leaves the disclosure unchanged. Task
links use the exact achievement article; boss links use the activity article or
a clearly labeled Wiki search when the source supplies no boss page. Links use
RuneLite's browser helper on explicit user clicks and retain stale-render guards.
The grey/black quest-star toolbar logo is original project code, with matching
`icon.png` assets for the resource bundle and future Hub listing. An explicit
Easy to hard shortcut switches the existing account's route mode.

The style uses the client's own `ColorScheme` and `FontManager`,
with native 16px RuneScape fonts instead of resized sans-serif text. Outer panels
use dark gray (`#282828`), section bars and expanded bodies use darker gray (`#1e1e1e`), borders use
`#171717`, hover surfaces use `#3c3c3c`, and selection/progress uses RuneLite
orange (`#dc8a00`). Primary text is white and supporting text is gray. Only the
active guide uses an orange title strip with black native bold text. The exact
task condition stays visible beneath it, while travel details remain collapsed.
Disclosure arrows and Wiki actions are separate square click targets. Buttons, inputs,
menus and scrollbars inherit the client's look and feel; the plugin does not
install a global theme or change other plugins.

The in-game reminder now follows Quest Helper's concise instruction presentation:
selected task title once, the exact condition and recorded KC only when known.
The duplicated CA Route heading, reward and route-ownership status are removed
from that overlay; those details remain in the sidebar. Specific Wilderness/PvP
cautions remain visible. Boss-only selection can show its travel destination.
This is static presentation, with no new combat prediction or completion inference.

Several primary-source references informed the common visual treatment:

| Reference | Applied pattern |
| --- | --- |
| [Loot Tracker](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerBox.java) | Flat expandable rows, small icons and compact padding. |
| [XP Tracker](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/xptracker/XpInfoBox.java) | Compact metrics, neutral panels and simple progress bars. |
| [Info](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/info/InfoPanel.java) | Dark click surfaces with a lighter hover state. |
| [Config](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/config/ConfigPanel.java) | Bold section headings, thin dividers and compact disclosure arrows. |
| [Quest Helper](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/panel/queststepsection/QuestStepPanel.java) | Title-first expandable task details and orange active accents. |

The shared colors and font sizes come from RuneLite's
[ColorScheme](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/ColorScheme.java),
[FontManager](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/FontManager.java)
and [look and feel](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/resources/net/runelite/client/ui/laf/RuneLiteLAF.properties).

The UI tests install the actual `RuneLiteLAF` on the Swing event thread before
constructing controls, so previews include native dark controls and scrollbars.
The test JVM stores its isolated RuneLite font directory under `build/test-runelite`.
They render the real Swing components with synthetic account/route
facts and canonical Amoxliatl task metadata. The preview has eight selected
tasks, 23 projected points and a synthetic 42-minute session; it does not read
the user's account. Preview files are written under `build/ui-compact-*.png`.
Tests check initially hidden details, usable expanded controls, sidebar widths,
filtering, readable primary fields at 194/225/280 pixels, single-line list density,
mode-specific fields and account disclosure resets. Route's new goal/ordering
controls cause no writes on display and retain the same detached-control guards
after rerender, logout and account changes. See the
[release verification](RELEASE_VERIFICATION.md) for final build evidence.

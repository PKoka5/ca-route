# External release checklist

Reviewed official guidance on 4 October 2026 for the 1.0.0 submission. The
public source/support repository is [PKoka5/ca-route](https://github.com/PKoka5/ca-route).
Plugin Hub distribution requires its CI checks and maintainer approval. The
[local verification](RELEASE_VERIFICATION.md) covers tests, reproducible
metadata, latest-release compilation and the standard-template build.
The latest final review repaired browser-choice resets and verified encounter
NPC aliases. The prepared submission description is in [PLUGIN_HUB_PR.md](PLUGIN_HUB_PR.md).
All 655 bundled tasks also have short, sourced quick guides; the subsequent review
fixed clipped final text lines at the narrow sidebar width.
The submission uses the `plugins/ca-route` marker, with the public repository URL
and exact reviewed source commit. Current submission/review status belongs to
the Plugin Hub pull request, rather than this local checklist.

1. Complete the logged-in smoke test: cache identity/points synchronization,
   actual CA completion removal, rerouting, account switches, optional positive-only
   bank observation, manual gear choices, logout reset and restart persistence.
   Include selected-task travel, public entrance visuals, actual completion cleanup
   and manual/foreign Shortest Path targets surviving CA Route Stop/shutdown.
2. After any additional changes, run `gradlew test jar` and `python data/convert.py --check`, then verify
   `-PruneLiteVersion=latest.release` and the Hub's replaced standard build.
   Local success is not a Plugin Hub CI result.
3. Review source/data attribution and bundled notices. Java is BSD-2-Clause;
   Wiki-derived data remains CC BY-NC-SA 3.0. Confirm the intended noncommercial
   distribution and preserve notices in the submitted resources.
4. Commit the final reviewed version and push it to the public project repository.
   Its metadata links support to https://github.com/PKoka5/ca-route/issues.
5. Submit a marker containing `repository=` and the full reviewed `commit=` to
   `runelite/plugin-hub/plugins`; wait for CI and maintainer review, then address
   requested changes in that PR. The Hub documents Java 11, metadata, standard
   builds and additional-dependency verification in its
   [submission guide](https://github.com/runelite/plugin-hub/blob/master/README.md).

CA Route's intended scope is a local planner with explicit travel selection,
static reminders and ordinary visible-NPC highlights. Audit the final submitted version against
[Jagex's client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
and RuneLite's
[rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features):
combat prediction, automatic prayer/position advice, server actions and prohibited
menu/interface changes must stay absent. This scope review does not represent
Jagex or Plugin Hub approval; the maintainers review the submitted source.

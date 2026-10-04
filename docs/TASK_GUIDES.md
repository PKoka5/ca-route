# Task quick guides

Expand a task in **Tasks**, **Route** or **Bosses**, then open **Quick guide**.
Each of the 655 bundled tasks has two to four short, numbered steps, covering
preparation, execution and the task's specific failure conditions. **Requirements
& gear** remains a separate account-aware checklist. The existing **Wiki** button
opens the full task page for deeper explanations and alternative methods.

Guides start collapsed and retain their disclosure state during same-account
refreshes. Opening or closing instructions never starts a task, requests a path,
opens a browser, changes account settings or marks an achievement complete.
Only explicit **Start task** selection enables the existing travel guidance.
These instructions are static reading material, without live combat signals,
step progression, prayer switching indicators or predicted attacks.

## Content and attribution

`data/guides/000-219.json`, `220-439.json` and `440-654.json` contain the
maintainable source text. Each entry retains its canonical task ID, exact task
name, activity, short instructions and OSRS Wiki source URLs. Wiki-derived
content remains **CC BY-NC-SA 3.0**, credited to OSRS Wiki contributors; the
upstream CA Helper curation remains attributed in the third-party notices.

The upstream `task_detail.json` was considered during review but its free-form
strategy/setup text is not automatically imported. Errors such as unreachable
melee targets, unsuitable protection and invalid equipment effects are excluded
from these guides. Instructions are standalone: a task never sends the reader
to an earlier task's guide or presents best-in-slot equipment as an access gate.
Guide text does not make the separate access or timing metadata exhaustive.

## Updating a guide

Check the exact task condition and a relevant Wiki method, then edit the entry
in its range file. Preserve any required instance, solo/team, weapon, prayer,
location, count or time conditions. Avoid unsupported damage/tick values and
prefer a practical sequence over repeating the task description.

Keep two to four steps, each at most 350 characters and the whole guide at most
100 words. Preserve the task's Wiki URL in `sources`; add a method page when it
supports additional tactics. All range files share one `datasetVersion`.

Run `python data/convert.py`, then `python data/convert.py --check`. Conversion
rejects missing/duplicate/unknown IDs, mismatched names or activities, empty or
oversized guides and missing task-source attribution. The generated resource
records the guide version, hashes of every source file and per-task evidence.
Repository tests check full loaded coverage and immutability; UI checks exercise
disclosure behavior, lack of action side effects and wrapping at 225/280 pixels,
including the last visible character staying within the allocated guide height.

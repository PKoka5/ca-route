Adds [CA Route 1.0.0](https://github.com/PKoka5/ca-route), a local Combat Achievement planner with account-aware routes,
task requirements and equipment reminders, boss task bundles, and completion
tracking from the client. All 655 bundled tasks have short, static quick guides
inside compact expandable details, plus explicit Wiki links. Players can choose
easy-to-hard progression or one of the other
five planning policies.

Starting a task or guiding a boss selects a sourced public approach on the world
map, shows a static task reminder, and optionally sends a destination through
Shortest Path's public PluginMessage protocol. The integration has no direct
dependency on Shortest Path. NPC indicators match ordinary visible encounter
NPCs; there is no combat prediction or game-action automation.

Account inputs are processed and stored locally. The plugin makes no account
data or hiscore network requests. Metadata is loaded from JAR resource streams.
The submission uses `build=standard`, Java 11 and the client's dependencies.
Account storage and the explicit export chooser use RuneLite's Filepath API.
Wiki-derived metadata has separate CC BY-NC-SA 3.0 attribution; Java source is
BSD-2-Clause. RuneLite/Quest Helper location-data notices are included in the JAR.

Validation: local full test suite, reproducible metadata, compilation against
the current RuneLite release, and the official standard build template. The
verification record is in [docs/RELEASE_VERIFICATION.md](https://github.com/PKoka5/ca-route/blob/main/docs/RELEASE_VERIFICATION.md). Logged-in user feedback
confirms task selection/reminders, the Giants travel path, and the sidebar.
Plugin Hub CI and maintainer review are still required.

Generated-by: OpenAI Codex

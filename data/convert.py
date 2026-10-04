"""Reproducibly normalize the attributed CA Helper snapshot; no runtime network.

All inferred fields are explicitly identified in fieldEvidence and diagnostics.
Numbers in execution-task descriptions NEVER become boss completion counts.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
INPUT = ROOT / "data" / "upstream"
OUTPUT = ROOT / "src/main/resources/com/caroute/achievements.json"
PREPARATION = ROOT / "data/player-preparation.json"
GUIDES = ROOT / "data/guides"
COMMIT = "25ebeeb09069ef544f7c10a5219bc1a321b88cf7"
WILDERNESS = {"Artio", "Callisto", "Calvar'ion", "Chaos Elemental", "Chaos Fanatic", "Crazy Archaeologist", "King Black Dragon", "Scorpia", "Spindel", "Venenatis", "Vet'ion"}
RAIDS = {"Chambers of Xeric", "Chambers of Xeric: Challenge Mode", "Theatre of Blood", "Theatre of Blood: Entry Mode", "Theatre of Blood: Hard Mode", "Tombs of Amascut", "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Expert Mode"}


def load(name):
    return json.loads((INPUT / (name + ".json")).read_text(encoding="utf-8"))


def convert():
    canonical = load("combat_achievements")
    effort, stats = load("task_effort")["tasks"], load("rec_stats")["tasks"]
    difficulty, timing = load("task_difficulty")["tasks"], load("boss_timing")["monsters"]
    activity_difficulty = load("boss_difficulty")["bosses"]
    preparation = json.loads(PREPARATION.read_text(encoding="utf-8"))
    # Identity-locked, standalone player instructions, reviewed separately from
    # the upstream free-form setup/strategy text (which is not imported).
    guide_files = sorted(GUIDES.glob("*.json"))
    guides = {}
    guide_versions = set()
    canonical_by_id = {task["id"]: task for task in canonical["tasks"]}
    for path in guide_files:
        part = json.loads(path.read_text(encoding="utf-8"))
        if part["schemaVersion"] != 1:
            raise ValueError("Unsupported quick guide schema: " + path.name)
        guide_versions.add(part["datasetVersion"])
        for guide in part["tasks"]:
            task_id = guide["id"]
            task = canonical_by_id.get(task_id)
            if task is None or task_id in guides:
                raise ValueError("Unknown or duplicate quick guide ID: " + str(task_id))
            monster = task.get("monster") or "None"
            activity = "Other: " + task["name"] if monster == "None" else monster
            if guide["name"] != task["name"] or guide["activity"] != activity:
                raise ValueError("Quick guide identity mismatch: " + str(task_id))
            steps = guide["steps"]
            if not 2 <= len(steps) <= 4 or any(not isinstance(step, str) or not step.strip() or len(step) > 350 for step in steps):
                raise ValueError("Quick guide needs 2-4 short nonempty steps: " + str(task_id))
            if sum(len(step.split()) for step in steps) > 100:
                raise ValueError("Quick guide exceeds 100 words: " + str(task_id))
            if len(set(steps)) != len(steps):
                raise ValueError("Duplicate quick guide step: " + str(task_id))
            sources = guide["sources"]
            task_source = "https://oldschool.runescape.wiki/w/" + quote(task["name"].replace(" ", "_"), safe="/")
            if not sources or task_source not in sources or task["wikiUrl"] not in sources or any(not source.startswith("https://oldschool.runescape.wiki/w/") for source in sources):
                raise ValueError("Quick guide needs its task Wiki attribution: " + str(task_id))
            guides[task_id] = guide
    if set(guides) != set(canonical_by_id) or len(guide_versions) != 1:
        raise ValueError("Quick guides must cover every canonical task with one review version")
    if preparation["schemaVersion"] != 1:
        raise ValueError("Unsupported player preparation schema")
    preparation_by_activity = {}
    reviewed_required_skills = {}
    activities = {task.get("monster") for task in canonical["tasks"]}
    for row in preparation["activities"]:
        activity = row["activity"]
        if activity not in activities or activity in preparation_by_activity:
            raise ValueError("Unknown or duplicate preparation activity: " + activity)
        if not row["notes"]:
            raise ValueError("Empty player preparation: " + activity)
        for note in row["notes"]:
            if not note["text"].strip() or not note["source"].startswith("https://oldschool.runescape.wiki/w/"):
                raise ValueError("Player preparation needs text and an attributed Wiki source")
        if row.get("requiredSkills"):
            if not row.get("requiredSkillsSource", "").startswith("https://oldschool.runescape.wiki/w/"):
                raise ValueError("Reviewed required skills need an attributed source")
            for skill, level in row["requiredSkills"].items():
                if not skill.strip() or not isinstance(level, int) or isinstance(level, bool) or not 1 <= level <= 99:
                    raise ValueError("Invalid reviewed skill requirement")
            reviewed_required_skills[activity] = row
        preparation_by_activity[activity] = row["notes"]
    result = []
    for task in canonical["tasks"]:
        key, activity = str(task["id"]), task.get("monster") or "None"
        if activity == "None":
            activity = "Other: " + task["name"]  # Avoid combining unrelated general achievements.
        e, s, d, t = effort.get(key, {}), stats.get(key, {}), difficulty.get(key, {}), timing.get(task.get("monster"), {})
        player_preparation = preparation_by_activity.get(activity, [])
        diagnostics = []
        required, recommended, alternatives = dict(e.get("levelReqs", {})), {}, []
        reviewed_skills = reviewed_required_skills.get(activity, {})
        for skill, level in reviewed_skills.get("requiredSkills", {}).items():
            required[skill] = max(required.get(skill, 1), level)
        for row in s.get("hard", []):
            children = [{"kind": "SKILL", "key": skill, "level": row["level"]} for skill in row["skills"]]
            if row.get("mode") == "any":
                alternatives.append({"kind": "ANY", "children": children})
            else:
                for skill in row["skills"]:
                    required[skill] = max(required.get(skill, 1), row["level"])
        soft_alternatives = []
        for row in s.get("soft", []):
            if row.get("mode") in ("any", "primary"):
                soft_alternatives.append(row)
            else:
                for skill in row["skills"]:
                    recommended[skill] = max(recommended.get(skill, 1), row["level"])
        flags = []
        if task["type"] == "Speed": flags.append("SPEED")
        if task["type"] == "Perfection": flags.append("PERFECTION")
        if task.get("monster") in WILDERNESS: flags.append("WILDERNESS")
        if task.get("monster") in RAIDS: flags.append("RAID")
        if e.get("soloable") is False or "team" in task["description"].lower() or task.get("monster", "").startswith("Theatre of Blood"):
            flags.append("GROUP")
        if e.get("rng") in ("med", "high"): flags.append("RNG")
        threshold = 0
        completion_count = 1
        overlap_group = ""
        repeat = re.match(r"(?:kill|defeat)\s+(?:the\s+)?" + re.escape(task.get("monster") or "") + r"\s+(\d+)\s+times\b(.*)", task["description"], re.I)
        if task["type"] != "Kill Count" and repeat:
            completion_count = int(repeat.group(1))
            # Only identical explicitly repeated conditions are eligible to share work.
            overlap_group = "repeat:" + activity + ":" + repeat.group(2).strip().lower()
        if task["type"] == "Kill Count":
            description = task["description"]
            match = re.search(r"\b(\d+)\s*(?:times|kills?|completions?)\b", description, re.I)
            if match:
                threshold = int(match[1])
            elif re.search(r"^(?:kill|defeat|complete|finish)\b", description, re.I):
                threshold = 1
            else:
                diagnostics.append("KC wording requires review; completionCount remains one")
        if max(threshold, completion_count) >= 75: flags.append("LONG_KC")
        if key not in effort: diagnostics.append("Access metadata missing; no unverified hard lock invented")
        # Even populated upstream requirements are incomplete (area, diary, setup/spells).
        diagnostics.append("Imported access metadata is not independently exhaustive; verify task setup")
        if not t: diagnostics.append("Encounter timing absent; explicit 180-second heuristic fallback")
        seconds = max(1, t.get("ttkSeconds", 180) + t.get("respawnSeconds", 0))
        # All nested KC milestones share the boss attempt rate; no per-rung mismatch.
        attempts = max(1.0, t.get("attemptsPerKill", 1.0)) if threshold else max(1.0, d.get("attempts", t.get("attemptsPerKill", 1.0)))
        result.append({
            "id": task["id"], "name": task["name"], "activity": activity,
            "tier": task["tier"].upper(), "type": task["type"], "description": task["description"],
            "wikiUrl": task.get("wikiUrl", ""), "requiredSkills": required,
            "recommendedSkills": recommended, "recommendedAlternatives": soft_alternatives,
            "quests": {q["name"]: "IN_PROGRESS" if q.get("startedSuffices") else "COMPLETED" for q in e.get("questReqs", [])},
            "requirement": {"kind": "ALL", "children": alternatives},
            "minimumGear": [], "recommendedGear": [], "flags": sorted(set(flags)),
            "playerPreparation": [note["text"] for note in player_preparation],
            "playerPreparationSources": sorted({note["source"] for note in player_preparation}),
            "quickGuide": guides[task["id"]]["steps"],
            "quickGuideSources": sorted(set(guides[task["id"]]["sources"])),
            "reviewedRequiredSkillsSource": reviewed_skills.get("requiredSkillsSource", ""),
            "kcThreshold": threshold, "completionCount": completion_count, "completionUnit": "ACTIVITY_COMPLETION",
            "secondsPerKill": seconds, "baseAttempts": attempts,
            "difficulty": min(10, max(1, d.get("difficulty", 3))),
            "activityDifficulty": min(10, max(1, activity_difficulty.get(task.get("monster"), d.get("difficulty", 3)))),
            "setupMinutes": 6.0, "requirementsKnown": False,
            "timingKnown": False, "overlapGroup": overlap_group,
            "provenance": "CA Helper " + COMMIT + "; Wiki CC BY-NC-SA 3.0; timing/attempts are curated estimates",
            "fieldEvidence": {
                "identity": "Wiki canonical snapshot; validate against live game",
                "requirements": "Imported requirement subset plus sourced activity-level corrections where supplied; not exhaustive",
                "kcThreshold": "Description inference restricted to Kill Count type; reviewed in data/count-review.txt",
                "completionCount": "Explicit repeated named-activity times when matched; otherwise unvalidated one-encounter baseline (never add counts)",
                "overlapGroup": "Only exact same repeated activity and condition suffix; other overlaps remain independent",
                "timing": "Upstream curated estimates, not measured for this account",
                "difficulty": "Upstream hand-rated task challenge 1..10; not the CA reward tier or a measured success probability",
                "activityDifficulty": "Upstream hand-rated boss mechanics baseline 1..10; missing activities fall back to task difficulty; heuristic, not an official difficulty rating",
                "flags": "Game types plus named activity lists and group-wording heuristic",
                "gear": "Unknown; text setup retained in upstream/task_detail.json",
                "quickGuide": "Static task-specific player instructions in data/guides; identity-locked to the canonical catalogue, with per-task Wiki attribution; not a live combat assistant",
                "playerPreparation": "Reviewed player equipment/access reminders from data/player-preparation.json; advisory, not new hard locks",
                "setupMinutes": "Explicit six-minute heuristic, inherited planning baseline"
            }, "diagnostics": diagnostics
        })
    result.sort(key=lambda item: item["id"])
    if [row["id"] for row in result] != list(range(len(result))):
        raise ValueError("Canonical task IDs must be contiguous and unique")
    manifest = {"schemaVersion": 1, "datasetVersion": canonical["version"], "taskCount": len(result),
                "upstreamCommit": COMMIT, "catalogueComplete": True,
                "licence": "CC-BY-NC-SA-3.0 (Wiki-derived data); upstream curation separately attributed",
                "inputSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(INPUT.glob("*.json"))},
                "playerPreparationVersion": preparation["datasetVersion"],
                "playerPreparationSha256": hashlib.sha256(PREPARATION.read_bytes()).hexdigest(),
                "quickGuideVersion": next(iter(guide_versions)),
                "quickGuideSha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in guide_files},
                "diagnostics": ["Static catalogue completeness is NOT verification against the live game.",
                                "Uncurated gear, diary/area gates and execution overlaps remain unknown.",
                                "ETA uses declared heuristic encounter/attempt/setup values, not a guarantee."], "tasks": result}
    return json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    output = convert()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != output:
            raise SystemExit("Generated metadata differs; run python data/convert.py")
        print("Metadata reproducibility verified")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(output, encoding="utf-8", newline="\n")
        print("Generated " + str(OUTPUT))

#!/usr/bin/env python3
"""Migrate issues, labels and milestones from GitLab to GitHub, preserving issue numbers.

GitLab issue #N becomes GitHub issue #N for every N >= FIRST_PRESERVED_IID. GitHub numbers
below that are already taken by pull requests, so the GitLab issues with those numbers are
appended after the highest GitLab issue number. Numbers of deleted GitLab issues are filled
with closed placeholder issues so the numbering stays aligned.

Usage:
    export GITLAB_TOKEN=...   # GitLab PAT with `api` (or `read_api` for the migration step)
    export GITHUB_TOKEN=...   # GitHub PAT with Issues: read/write on the target repo
    scripts/migrate_gitlab_issues.py                 # dry run, prints what would happen
    scripts/migrate_gitlab_issues.py --execute       # migrate to GitHub (resumable)
    scripts/migrate_gitlab_issues.py --close-gitlab  # dry run of commenting/closing on GitLab
    scripts/migrate_gitlab_issues.py --close-gitlab --execute

Progress is stored in migration-map.json; rerunning --execute resumes where it stopped.
Only the Python standard library is used.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

GITLAB_API = "https://gitlab.com/api/v4"
GITLAB_PROJECT_ID = 6643214
GITLAB_WEB = "https://gitlab.com/go-3/go-3"
GITHUB_API = "https://api.github.com"
GITHUB_REPO = "lene/go-3"
FIRST_PRESERVED_IID = 3  # GitHub #1 and #2 are pull requests
MAP_FILE = Path("migration-map.json")
WRITE_DELAY_SECONDS = 1.0
MAX_BODY_LENGTH = 65000  # GitHub limit is 65536 characters

ISSUE_MARKER = "<!-- gitlab-issue:{} -->"
NOTE_MARKER = "<!-- gitlab-note:{} -->"
ISSUE_MARKER_RE = re.compile(r"<!-- gitlab-issue:(\d+) -->")


class ApiError(Exception):
    pass


def request(method, url, token_header, data=None, retries=6):
    body = json.dumps(data).encode() if data is not None else None
    for attempt in range(retries):
        req = urllib.request.Request(url, data=body, method=method)
        req.add_header(*token_header)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/vnd.github+json")
        try:
            with urllib.request.urlopen(req) as resp:
                payload = resp.read()
                return json.loads(payload) if payload else None, resp.headers
        except urllib.error.HTTPError as err:
            text = err.read().decode(errors="replace")
            rate_limited = err.code == 429 or (err.code == 403 and "rate limit" in text.lower())
            if (rate_limited or err.code >= 500) and attempt < retries - 1:
                wait = int(err.headers.get("Retry-After") or 0) or 2 ** (attempt + 2)
                print(f"  {err.code} from {url}, retrying in {wait}s", file=sys.stderr)
                time.sleep(wait)
                continue
            raise ApiError(f"{method} {url} -> {err.code}: {text}") from err
    raise ApiError(f"{method} {url}: retries exhausted")


class GitLab:
    def __init__(self, token):
        self.header = ("PRIVATE-TOKEN", token)
        self.base = f"{GITLAB_API}/projects/{GITLAB_PROJECT_ID}"

    def get_all(self, path, params=None):
        params = dict(params or {}, per_page=100)
        page, items = 1, []
        while page:
            query = urllib.parse.urlencode(dict(params, page=page))
            data, headers = request("GET", f"{self.base}/{path}?{query}", self.header)
            items.extend(data)
            page = int(headers.get("X-Next-Page") or 0)
        return items

    def post(self, path, data):
        return request("POST", f"{self.base}/{path}", self.header, data)[0]

    def put(self, path, data):
        return request("PUT", f"{self.base}/{path}", self.header, data)[0]


class GitHub:
    def __init__(self, token):
        self.header = ("Authorization", f"Bearer {token}")
        self.base = f"{GITHUB_API}/repos/{GITHUB_REPO}"

    def get(self, path):
        return request("GET", f"{self.base}/{path}", self.header)[0]

    def get_all(self, path):
        page, items = 1, []
        while True:
            sep = "&" if "?" in path else "?"
            data = self.get(f"{path}{sep}per_page=100&page={page}")
            items.extend(data)
            if len(data) < 100:
                return items
            page += 1

    def write(self, method, path, data):
        result = request(method, f"{self.base}/{path}", self.header, data)[0]
        time.sleep(WRITE_DELAY_SECONDS)
        return result

    def highest_number(self):
        """Highest issue or pull request number in the repository (0 if none)."""
        issues = self.get("issues?state=all&sort=created&direction=desc&per_page=1")
        pulls = self.get("pulls?state=all&sort=created&direction=desc&per_page=1")
        return max([i["number"] for i in issues + pulls] or [0])


def number_mapping(max_iid):
    """Map every GitLab iid (existing or not) to its GitHub issue number."""
    mapping = {iid: iid for iid in range(FIRST_PRESERVED_IID, max_iid + 1)}
    for offset, iid in enumerate(range(1, FIRST_PRESERVED_IID), start=1):
        mapping[iid] = max_iid + offset
    return mapping


def neutralize_mentions(text):
    # Avoid pinging unrelated GitHub users who happen to share a GitLab username.
    return re.sub(r"(?<![\w`/.@])@([A-Za-z0-9][\w.-]*)", r"@&#8203;\1", text)


def rewrite_references(text, mapping):
    text = re.sub(r"\]\((/uploads/[^)\s]+)\)", rf"]({GITLAB_WEB}\1)", text)
    text = re.sub(r"(?<![\w/&!])!(\d+)\b", rf"[!\1]({GITLAB_WEB}/-/merge_requests/\1)", text)

    def issue_ref(match):
        iid = int(match.group(1))
        return f"#{mapping[iid]}" if iid in mapping and mapping[iid] != iid else match.group(0)

    text = re.sub(r"(?<![\w/&#])#(\d+)\b", issue_ref, text)
    return neutralize_mentions(text)


def truncate(text, url):
    if len(text) <= MAX_BODY_LENGTH:
        return text
    return text[:MAX_BODY_LENGTH] + f"\n\n*(truncated, see full text at {url})*"


def issue_body(issue, mapping):
    header = (
        f"{ISSUE_MARKER.format(issue['iid'])}\n"
        f"*Migrated from GitLab [#{issue['iid']}]({issue['web_url']}), opened by "
        f"`@{issue['author']['username']}` on {issue['created_at'][:10]}"
    )
    if issue.get("closed_at"):
        header += f", closed on {issue['closed_at'][:10]}"
    header += ".*\n\n---\n\n"
    return truncate(header + rewrite_references(issue.get("description") or "", mapping),
                    issue["web_url"])


def note_body(note, issue, mapping):
    header = (
        f"{NOTE_MARKER.format(note['id'])}\n"
        f"*`@{note['author']['username']}` commented on GitLab on {note['created_at'][:10]}:*"
        "\n\n"
    )
    return truncate(header + rewrite_references(note["body"], mapping), issue["web_url"])


def load_map():
    if MAP_FILE.exists():
        return json.loads(MAP_FILE.read_text())
    return {"labels": [], "milestones": {}, "issues": {}}


def save_map(state):
    MAP_FILE.write_text(json.dumps(state, indent=2, sort_keys=True))


def migrate_labels(gitlab, github, state, execute):
    existing = {label["name"] for label in github.get_all("labels")} if execute else set()
    for label in gitlab.get_all("labels"):
        name = label["name"]
        if name in existing or name in state["labels"]:
            continue
        print(f"label: {name}")
        if execute:
            github.write("POST", "labels", {
                "name": name,
                "color": label["color"].lstrip("#")[:6],
                "description": (label.get("description") or "")[:100],
            })
            state["labels"].append(name)
            save_map(state)


def migrate_milestones(gitlab, github, state, execute):
    existing = {m["title"]: m["number"] for m in github.get_all("milestones?state=all")} \
        if execute else {}
    for milestone in gitlab.get_all("milestones"):
        title = milestone["title"]
        if title in state["milestones"]:
            continue
        print(f"milestone: {title} ({milestone['state']})")
        if not execute:
            continue
        if title in existing:
            state["milestones"][title] = existing[title]
        else:
            data = {
                "title": title,
                "state": "closed" if milestone["state"] == "closed" else "open",
                "description": milestone.get("description") or "",
            }
            if milestone.get("due_date"):
                data["due_on"] = f"{milestone['due_date']}T00:00:00Z"
            state["milestones"][title] = github.write("POST", "milestones", data)["number"]
        save_map(state)


def reconcile_unrecorded(github, state):
    """Record issues that were created on GitHub but not saved to the map before a crash."""
    recorded = {entry["github"] for entry in state["issues"].values()}
    last = github.highest_number()
    for number in range(FIRST_PRESERVED_IID, last + 1):
        if number in recorded:
            continue
        issue = github.get(f"issues/{number}")
        match = ISSUE_MARKER_RE.search(issue.get("body") or "")
        if match:
            print(f"  recovered GitHub #{number} as GitLab #{match.group(1)}")
            state["issues"][match.group(1)] = {"github": number, "complete": False}
        elif "(deleted)" in issue["title"] and issue["title"].startswith("GitLab #"):
            iid = issue["title"].split()[1].lstrip("#")
            state["issues"][iid] = {"github": number, "complete": False}
        else:
            raise ApiError(f"GitHub #{number} was not created by this migration; aborting")
    save_map(state)


def finish_issue(github, number, issue, notes, mapping, state, execute):
    """Post missing comments, then set labels, milestone and state."""
    posted = set()
    if execute:
        for comment in github.get_all(f"issues/{number}/comments"):
            posted.update(re.findall(r"<!-- gitlab-note:(\d+) -->", comment["body"] or ""))
    for note in notes:
        if str(note["id"]) in posted:
            continue
        if execute:
            github.write("POST", f"issues/{number}/comments",
                         {"body": note_body(note, issue, mapping)})
    update = {"labels": issue["labels"]}
    milestone = (issue.get("milestone") or {}).get("title")
    if milestone:
        update["milestone"] = state["milestones"].get(milestone)
    if issue["state"] == "closed":
        update.update(state="closed", state_reason="completed")
    if execute:
        github.write("PATCH", f"issues/{number}", update)
        state["issues"][str(issue["iid"])]["complete"] = True
        save_map(state)


def migrate_issues(gitlab, github, state, execute):
    issues = {i["iid"]: i for i in gitlab.get_all("issues", {"state": "all", "scope": "all"})}
    max_iid = max(issues)
    mapping = number_mapping(max_iid)
    order = list(range(FIRST_PRESERVED_IID, max_iid + 1)) + list(range(1, FIRST_PRESERVED_IID))
    missing = [iid for iid in order if iid not in issues]
    print(f"{len(issues)} GitLab issues, highest #{max_iid}, placeholders for {missing}")
    print(f"GitLab #1..#{FIRST_PRESERVED_IID - 1} become GitHub "
          f"{[mapping[i] for i in range(1, FIRST_PRESERVED_IID)]}")
    if execute:
        reconcile_unrecorded(github, state)

    for iid in order:
        entry = state["issues"].get(str(iid))
        if entry and entry["complete"]:
            continue
        expected = mapping[iid]
        issue = issues.get(iid)
        if entry is None:
            if execute:
                actual_next = github.highest_number() + 1
                if actual_next != expected:
                    raise ApiError(f"next GitHub number is #{actual_next}, expected #{expected} "
                                   f"for GitLab #{iid}; aborting to keep numbering intact")
            if issue is None:
                data = {"title": f"GitLab #{iid} (deleted)",
                        "body": f"Placeholder keeping issue numbers aligned with GitLab; "
                                f"GitLab issue #{iid} no longer exists."}
            else:
                data = {"title": issue["title"], "body": issue_body(issue, mapping)}
            print(f"GitLab #{iid} -> GitHub #{expected}: {data['title']}")
            if not execute:
                continue
            created = github.write("POST", "issues", data)
            if created["number"] != expected:
                raise ApiError(f"created GitHub #{created['number']}, expected #{expected}")
            entry = state["issues"][str(iid)] = {"github": expected, "complete": False}
            save_map(state)
        if issue is None:
            if execute:
                github.write("PATCH", f"issues/{expected}",
                             {"state": "closed", "state_reason": "not_planned"})
                entry["complete"] = True
                save_map(state)
            continue
        notes = [n for n in gitlab.get_all(f"issues/{iid}/notes", {"sort": "asc"})
                 if not n.get("system")]
        finish_issue(github, expected, issue, notes, mapping, state, execute)


def close_gitlab(gitlab, state, execute):
    for issue in gitlab.get_all("issues", {"state": "all", "scope": "all"}):
        entry = state["issues"].get(str(issue["iid"]))
        if not entry or not entry["complete"]:
            print(f"GitLab #{issue['iid']} not migrated yet, skipping")
            continue
        url = f"https://github.com/{GITHUB_REPO}/issues/{entry['github']}"
        print(f"GitLab #{issue['iid']}: moved to {url}"
              f"{' (closing)' if issue['state'] == 'opened' else ''}")
        if not execute or entry.get("gitlab_closed"):
            continue
        gitlab.post(f"issues/{issue['iid']}/notes",
                    {"body": f"This project has moved to GitHub. This issue is now {url}"})
        if issue["state"] == "opened":
            gitlab.put(f"issues/{issue['iid']}", {"state_event": "close"})
        entry["gitlab_closed"] = True
        save_map(state)
        time.sleep(WRITE_DELAY_SECONDS)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--execute", action="store_true", help="perform writes (default: dry run)")
    parser.add_argument("--close-gitlab", action="store_true",
                        help="comment on and close GitLab issues instead of migrating")
    args = parser.parse_args()

    gitlab_token = os.environ.get("GITLAB_TOKEN")
    github_token = os.environ.get("GITHUB_TOKEN")
    if not gitlab_token or (not github_token and not args.close_gitlab):
        sys.exit("GITLAB_TOKEN and GITHUB_TOKEN must be set")
    gitlab = GitLab(gitlab_token)
    state = load_map()

    if not args.execute:
        print("DRY RUN - nothing will be written\n")
    try:
        if args.close_gitlab:
            close_gitlab(gitlab, state, args.execute)
        else:
            github = GitHub(github_token)
            migrate_labels(gitlab, github, state, args.execute)
            migrate_milestones(gitlab, github, state, args.execute)
            migrate_issues(gitlab, github, state, args.execute)
    except ApiError as err:
        sys.exit(f"ERROR: {err}")


if __name__ == "__main__":
    main()

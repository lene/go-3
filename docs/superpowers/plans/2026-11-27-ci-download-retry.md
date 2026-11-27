# CI download retry and cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CI jobs survive Maven Central rate limits and download flakes by retrying them after a
delay, use a dependency cache that a PR's first run can restore, and run two container images
instead of four.

**Architecture:**
- One bash wrapper, `ci/sbt-retry.sh`, runs sbt and retries only when the output shows a
  download failure.
- Every `sbt` step in the workflows and the Dockerfile calls the wrapper.
- `coursier/cache-action` replaces `setup-java`'s exact-key sbt cache.

**Tech Stack:** bash, GitHub Actions, `coursier/cache-action` v9.0.1
(`ee0650f98295f27a505e4bfd284c9bfaa913a465`), shellcheck, actionlint.

**Spec:** `docs/superpowers/specs/2026-11-27-ci-download-retry-design.md`

## Global Constraints
- The worktree is `/home/lepr/workspace/go-3-ci`, branch `ci/download-retry`, from
  github/master.
- The PR title and commit message contain "CI Fix"; no version bump.
- Pin every new action by full commit SHA with a `# vX.Y.Z` comment, as the workflows already do.
- Waits between attempts default to 120, 300 and 600 seconds, overridable by `SBT_RETRY_DELAYS`
  (space-separated seconds).
- The download-failure pattern (extended regex):
  `HTTP 429|could not retrieve|Error downloading|shasum mismatch|CantDownloadModule|download error`.
- A failure without a download-failure match is never retried.
- Lines are at most 100 characters (the repo convention).
- Shell: do not chain with `&&` in your own commands; use `;`. No `$(…)` in commands you run
  through the tool; inside scripts it is fine.

## Review Focus
- **A retried `warnings` step:** `compile.log` (`tee`) then holds the failed attempt's output too.
  The failed attempt stopped at resolution and compiled nothing, so the warning count is
  unchanged. Task 2 checks that `ci/warning_summary.sh` counts compiler warning lines only.
- **sbt's exit status through the pipe:** the wrapper must return sbt's own status, not `tee`'s,
  which is always 0. Task 1's test-failure case pins this with exit status 3.
- **The coverage step's `|| true`:** it must still swallow test failures (coverage is checked
  later), while a download failure is retried inside the wrapper first. Task 2 keeps the
  `|| true` after the wrapper.
- **The Docker build:** `ci/` must be in the build context (`.dockerignore` excludes only
  `training/`, `models/`, `selfplay/`) and the script executable (git mode 100755). Task 2 checks
  both, and the pre-push `docker build` runs it.
- **The container jobs:** the default shell there is `sh`; the wrapper runs through its
  `#!/usr/bin/env bash` shebang, and both images have bash. Checked by CI in Task 4.

---

### Task 1: `ci/sbt-retry.sh` with its self-test

**Files:**
- Create: `ci/sbt-retry.sh` (mode 755)
- Test: `ci/test-sbt-retry.sh` (mode 755)

**Interfaces:**
- Produces: `ci/sbt-retry.sh <sbt args…>`. It runs `sbt "$@"`, streams its output and returns
  sbt's exit status. Env `SBT_RETRY_DELAYS` (default `"120 300 600"`) sets the number of
  retries and the wait before each.

- [ ] **Step 1: Write the self-test `ci/test-sbt-retry.sh`.** It runs under `bash`, with
  `set -euo pipefail`.
  - It creates a temp dir holding a fake `sbt` on the front of `PATH`. Each call appends a line
    to `$DIR/calls`, then acts on `FAKE_SBT_MODE`:
    - `429-once`: the first call prints
      `retryable HTTP error: https://repo1.maven.org/x.pom (HTTP 429)` and exits 1; later calls
      print `[success]` and exit 0.
    - `test-failure`: prints `[error] Failed: Total 3, Failed 1` and exits 3.
    - `429-always`: prints `[error] [launcher] could not retrieve sbt 1.13.1` and exits 1.
  - It sets `SBT_RETRY_DELAYS="0 0 0"` and asserts:
    - `429-once`: exit 0, 2 calls;
    - `test-failure`: exit 3, 1 call;
    - `429-always`: exit 1, 4 calls (the first try and 3 retries).
  - It also asserts that the fake's output reaches the wrapper's stdout (`grep -q` the
    `[success]` line in the `429-once` run).
  - It prints `ok: <case>` per case, and on any mismatch it prints
    `FAIL: <case> expected … got …` and exits 1.
- [ ] **Step 2: Run it.** `bash ci/test-sbt-retry.sh`. Expected: it fails because
  `ci/sbt-retry.sh` does not exist.
- [ ] **Step 3: Implement `ci/sbt-retry.sh`.**
  - `#!/usr/bin/env bash`, `set -uo pipefail`.
  - Each attempt runs `sbt "$@" 2>&1 | tee "$log"` and takes the status from `PIPESTATUS[0]`.
  - On status 0 it exits 0.
  - On a non-zero status with `grep -Eq "$PATTERN" "$log"`, it sleeps for the next delay after
    echoing `sbt-retry: download failure, retrying in Ns (attempt i of n)`.
  - Otherwise, and after the last delay, it exits with the status.
  - `trap` removes the log file.
  - A header comment explains why: there is no `Retry-After`, and lm-coursier 2.1.13 has no 429
    setting.
- [ ] **Step 4: Run the self-test and shellcheck.**
  `bash ci/test-sbt-retry.sh; shellcheck ci/sbt-retry.sh ci/test-sbt-retry.sh`. Expected: three
  `ok:` lines, and no shellcheck output.
- [ ] **Step 5: Commit.**
  `git add ci/sbt-retry.sh ci/test-sbt-retry.sh` with the message
  `CI Fix: retry sbt on download failures`.

### Task 2: every sbt step goes through the wrapper, and CI runs the self-test

**Files:**
- Modify: `.github/workflows/ci.yml`:
  - the sbt steps: `test-debian` 58, `test` 83, `coverage` 127 and 129, `warnings` 173,
    `benchmark` 195, `check-runtime` 203 and 207, `format` 223, `build-deployable` 292;
  - a new job `ci-scripts`.
- Modify: `.github/workflows/staging-gameplay.yml:43`
- Modify: `.github/workflows/terraform.yml` (the `sbt assembly` run, about line 78)
- Modify: `Dockerfile:9`

**Interfaces:**
- Consumes: `ci/sbt-retry.sh <sbt args…>` (Task 1).

- [ ] **Step 1: Replace the leading `sbt` with `ci/sbt-retry.sh` in each listed `run:`.**
  - Arguments and quoting stay the same.
  - Keep `|| true` in the coverage line and `| tee compile.log` in the warnings line.
  - Find every place with `grep -n -E "(^|[ :])sbt " .github/workflows/*.yml Dockerfile`. After
    the change, the only sbt invocations left that aren't wrapped are the `apt-get install sbt`
    line and `scalacenter/sbt-dependency-submission`.
- [ ] **Step 2: Change the Dockerfile.** `RUN sbt "Universal / packageBin"` becomes
  `RUN ci/sbt-retry.sh "Universal / packageBin"`.
- [ ] **Step 3: Add the job `ci-scripts`** to `ci.yml`, named `CI scripts`, on
  `ubuntu-latest`, with the steps:
  - checkout (the same pinned SHA as the other jobs);
  - `shellcheck ci/*.sh`;
  - `bash ci/test-sbt-retry.sh`.
- [ ] **Step 4: Check the special cases.**
  - `ci/warning_summary.sh` counts only compiler warning lines (read it and confirm in one
    line).
  - `git ls-files -s ci/sbt-retry.sh` shows mode 100755.
- [ ] **Step 5: Lint.** Run `actionlint`. Expected: no output.
- [ ] **Step 6: Commit** the three workflows and the `Dockerfile` with the message
  `CI Fix: run every sbt step through sbt-retry.sh`.

### Task 3: a cache with fallback keys, and two container jobs

**Files:**
- Modify: `.github/actions/setup-jvm/action.yml`
- Modify: `.github/workflows/ci.yml:35-58` (`test-debian`)

**Interfaces:** none new.

- [ ] **Step 1: Change `setup-jvm`.** Remove `cache: sbt` from `actions/setup-java`, and add
  after `sbt/setup-sbt`:

  ```yaml
  - uses: coursier/cache-action@ee0650f98295f27a505e4bfd284c9bfaa913a465 # v9.0.1
  ```

  It caches `~/.cache/coursier/v1`, `~/.sbt` and `~/.ivy2/cache`, keyed on `*.sbt` and
  `project/**`, with restore keys.
- [ ] **Step 2: Change `test-debian`.**
  - The matrix becomes `image: [debian:stable-slim, ubuntu:latest]`.
  - Update the job comment: one image per JDK (21 and 25 from `default-jdk`).
  - Add `coursier/cache-action` (same pin) after checkout, with
    `extraKey: ${{ matrix.image }}`, because home is `/github/home` and the compression differs
    from the runner jobs.
- [ ] **Step 3: Lint.** Run `actionlint`. Expected: no output.
- [ ] **Step 4: Commit** with the message `CI Fix: cache dependencies with fallback keys, test
  on one image per JDK`.

### Task 4: verify, then the PR

- [ ] **Step 1: Docs.** Wherever `docs/development.md` or `AGENTS.md` names the four images or
  the sbt commands in CI, update it to one line each. Remove the "CI downloads" entry from
  `/home/lepr/workspace/go-3/LOCAL_CODE_IMPROVEMENTS.md`, which is never staged. Commit the
  docs together with the spec and this plan (`docs/superpowers/specs/…`, `docs/superpowers/plans/…`)
  as `CI Fix: document the CI download retry`.
- [ ] **Step 2: Push.** Run `git push -u github ci/download-retry`. The pre-push hook runs
  actionlint, `sbt test` and `docker build`, all of which must pass.
- [ ] **Step 3: Open the PR.**
  `gh pr create --repo lene/go-3 --base master --title "CI Fix: retry sbt on download
  failures, cache with fallback keys, two container images"`, with the body in the repo's
  format (Summary, Test plan, attribution).
- [ ] **Step 4: Watch CI.** It must be all green with the new `CI scripts` job. If a 429 occurs,
  the log shows `sbt-retry:` and the job still passes.
- [ ] **Step 5: Check the cache.** Rerun one sbt job (`gh run rerun <id> --job <job>`). In its
  log there must be no `getting org.scala-sbt sbt` line, and `coursier/cache-action` reports a
  cache hit. The fallback for a version-bumping PR can only show after the merge, on the next
  PR. Note that in the PR body.

# CI downloads: retry on rate limits, use the cache (design)

## Intent
A CI run fails only when the code is defective. A download refused by Maven Central (HTTP 429,
missing checksum, unreachable repository) must not fail a job or require a manual rerun, and no
job is skipped to avoid it. Seen in #208 (`debian:stable-slim`, launcher sha1 missing) and #209
(`Test`, HTTP 429 from Maven Central and `repo.scala-sbt.org` for 17 minutes).

## Findings
- Sonatype documents no `Retry-After` header. Blocks are temporary ("deliberately short" at
  first, up to 30 days for repeated overuse). GitHub runners share egress IPs, so other users'
  traffic can block us.
- The downloader inside sbt 1.13.1 (lm-coursier 2.1.13) has no setting for 429 handling, only
  `coursier.exception-retry` for connection errors. The retry therefore wraps the sbt command.
- `setup-java` `cache: sbt` is keyed on a hash of `build.sbt` and has no fallback key. Every PR
  bumps the version in `build.sbt`, so each PR's first run starts from an empty cache, and about
  eight jobs download sbt and all dependencies at the same moment.
- The four container jobs have no cache at all. Their `default-jdk` is 21 on `ubuntu:24.04` and
  `debian:stable-slim`, and 25 on `ubuntu:latest` (26.04) and `debian:testing-slim`.

## Goals / non-goals
- Goal: retry download failures after a delay; fail a real compile or test failure at once.
- Goal: a PR's first run restores master's dependency cache; a rerun downloads nothing.
- Goal: the container jobs use a cache, and there are two of them: one per JDK.
- Non-goal: a Maven mirror (`COURSIER_MIRRORS`); kept for later if 429s still get through.
- Anti-goal: retrying on any failure, which would hide defective code.

## Approaches (user picked 1, 2026-11-27)
1. **Retry download failures only (picked):** `ci/sbt-retry.sh` runs sbt, and if sbt fails and its
   output matches a download failure, it waits and retries.
2. Mirror Maven Central through Google's mirror: avoids the limit, but adds a host and needs
   separate configuration for the launcher. Deferred.
3. A generic retry action: rejected, because it retries test failures too.

## Design
- **`ci/sbt-retry.sh <sbt args…>`:**
  - runs `sbt "$@"` with its output both shown and kept;
  - on exit 0, it exits 0;
  - on a failure whose output matches `HTTP 429|could not retrieve|Error downloading|shasum
    mismatch|CantDownloadModule|download error`, it waits, then tries again;
  - the waits are 2, 5 and 10 minutes, overridable by `SBT_RETRY_DELAYS` (seconds, for the
    self-test);
  - any other failure, or the last attempt, exits with sbt's status.
- **Used by** every `run: sbt …` step:
  - `ci.yml`: test-debian, test, coverage (keeps its `|| true`), warnings (keeps `tee`),
    benchmark, check-runtime, format and build-deployable;
  - `staging-gameplay.yml`;
  - `terraform.yml` (`sbt assembly`);
  - the `Dockerfile` builder's `RUN sbt "Universal / packageBin"`.

  `scalacenter/sbt-dependency-submission` (master only) runs sbt itself and can't be wrapped. It
  still benefits from the cache.
- **Cache:** in `.github/actions/setup-jvm`, drop `cache: sbt` from `setup-java` and add
  `coursier/cache-action` (pinned by SHA). It caches the coursier, ivy and sbt boot directories,
  keyed on the build files, and falls back to the newest cache with the same prefix. PR branches
  can read master's caches.
- **Container jobs:**
  - the matrix becomes `debian:stable-slim` (JDK 21) and `ubuntu:latest` (JDK 25);
  - `ubuntu:24.04` and `debian:testing-slim` are dropped;
  - each job gets `coursier/cache-action` with the image as an extra key, because home
    (`/github/home`) and compression differ from the runner jobs.
- **Check:** `ci/test-sbt-retry.sh` puts a fake `sbt` on `PATH` and checks three cases:
  - a 429 once, then success: exit 0 after two runs;
  - a test failure: no retry, exit status kept;
  - a 429 every time: three retries, then failure.

  It runs with `SBT_RETRY_DELAYS="0 0 0"` in an existing lightweight CI job.
- **Release:** a "CI Fix" PR, so no version bump.

## Left to the builder
The exact job that runs the self-test; the `coursier/cache-action` version; whether the
container images need `zstd` (otherwise `actions/cache` falls back to gzip).

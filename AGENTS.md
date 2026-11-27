<!-- bmad:context -->
<!-- Verified 2026-11-27 against ef1e6e5. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## go-3

3D Go (Weiqi/Baduk) on a cubic lattice: game server, ASCII/libGDX/bot clients, and an AWS Lambda + DynamoDB + S3 deployment managed with Terraform. Scala 3 (versions in `build.sbt`, `project/build.properties`), http4s + Cats Effect, circe. An AlphaZero engine is being built: the sbt subproject `engine` (ONNX Runtime) plays and self-plays, Python under `training/` (uv, PyTorch) trains. Code structure: `docs/architecture.md`; commands, test-game recipes, engine and training: `docs/development.md`; record and model contract: `docs/model-contract.md`; AWS design and migration plan: `AWS.md`, `docs/aws-phase-6-evaluation-and-lambda-migration-plan.md`.

## Policy

- Ask the user before anything that deploys to or changes a cloud provider: `terraform apply`/`destroy`, workflow dispatches of `terraform.yml`/`terraform-destroy.yml`, AWS CLI writes. Read-only calls and `terraform plan` need no approval.
- The default branch is `master`. Never push to it directly; open a PR on GitHub (`lene/go-3`). Never merge a PR (`gh pr merge` or the web UI) until the user says so for that PR; until then, watch its CI and report.
- GitLab (`gitlab.com/go-3/go-3`) is a read-only mirror fed by `mirror-to-gitlab.yml`; never push to it or add build/test/release jobs to `.gitlab-ci.yml`.
- Bump the version together in `build.sbt` (`version :=`), `Dockerfile` (`ARG version=`, twice) and `run-test-game.sh` (`VERSION=`); the `version-consistent` CI job enforces it. If in doubt, bump the patch version.
- Put "CI Fix" in the commit message (or PR title) to skip the bump for CI/Docker/build-script-only changes; never for application code, dependency updates or bug fixes. Dependabot's uv, pip and Terraform PRs are dependency updates without "CI Fix": push the bump onto them before merging.
- Never commit model generations or self-play records: `models/` and `selfplay/` at the repo root are git-ignored and shared by sbt (run from the root) and the Python tools (run from `training/`, defaults `../models`, `../selfplay`). Only the small fixtures under `engine/src/test/resources/` are committed.
- Coverage may only go up: the 0.1pp `COVERAGE_TOLERANCE` in `ci.yml` absorbs measurement noise and is not an allowance. Compiler warnings may only go down: fix new ones or add a justified `@SuppressWarnings` (see `STATIC_ANALYSIS.md`).
- After every push, watch the CI run and fix failures before merging.
- Keep this block current: re-run `bmad-project-context` (refresh) after significant changes to build, CI or infra, and `record` the moment an agent repeats a mistake. Prefer a hook or CI check over a new line.

## Where things are

- Domain: `src/main/scala/go3d/Goban.scala`, `Game.scala`. HTTP routes: `server/http4s/GoHttpService.scala`. Lambda: `server/lambda/LambdaHandler.scala` on top of `server/service/GameService.scala`. Bot strategies: `client/SetStrategy.scala`.
- Adding an endpoint: route in `GoHttpService.goService`, handler extending `BaseHandler`/`MakeMoveTrait`, and the same route in `LambdaHandler`, which serves the same API.
- Engine: `engine/src/main/scala/go3d/engine/` (search, self-play, ONNX model), found by `BotClient` through the `MoveEngineProvider` service (`client/MoveEngine.scala`). Training: `training/go3d_training/`.
- Infra: `infra/terraform/`. CI: `.github/workflows/`, helper scripts in `ci/`.

## Running and verifying

- Run single suites while iterating (`sbt "testOnly go3d.TestGoban"`); `sbt test` before every push.
- CI runs coverage with `sbt "set Test / parallelExecution := false" clean coverage test`: the suites share global state, so never enable parallel test execution.
- The DynamoDB Local and S3Mock suites skip without `DYNAMODB_TEST_ENDPOINT`/`S3_TEST_ENDPOINT` and only run in CI.
- Run anything that loads a model through the engine project: `sbt "engine/runMain go3d.client.BotClient … --strategy model:<g>"`, `sbt "engine/runMain go3d.engine.SelfPlay …"`; a root `runMain` has no engine on the classpath.
- Python: `cd training` and use `uv run …` (`pytest`, `ruff check`, `ruff format --check`, `mypy`), never bare `pytest`, in the repo checkout (the tests read `engine/src/test/resources/selfplay`). Without CUDA, `uv sync --extra cpu` and then `uv run --no-sync …`, since plain `uv run` re-syncs the CUDA build of PyTorch.
- In CI workflows and the `Dockerfile`, call sbt as `ci/sbt-retry.sh <args>`, never bare `sbt`: it retries Maven Central download failures, and the `CI scripts` job fails a bare sbt step.
- `.git/hooks/pre-push` is a manual symlink to `.git_hooks/pre-push` (not set up by a fresh clone): it runs `actionlint`, `sbt test` and `docker build`. A latest commit subject starting with `WIP:` bypasses its failures; never use that to push failing code.
- Java 25 for CI (`.github/actions/setup-jvm`), the Docker builder and runtime image and the Lambda runtime (`lambda_runtime` in `infra/terraform/go3d-readonly/variables.tf`). The runtime image in the `Dockerfile` is pinned by digest; Dependabot updates it, never unpin it. Python 3.12 for `training/` (`requires-python` in `training/pyproject.toml`).
- Claude Code on the web: `.claude/hooks/session-start.sh` installs an `sbt` wrapper using Google's Maven mirror; compile and run the affected tests there before pushing.

## Conventions that differ from defaults

- Auth header is `Authentication: Bearer <token>`, not `Authorization`.
- `Wart.Throw` is a build error; the few library-interop throws carry `@SuppressWarnings(Array("org.wartremover.warts.Throw"))`.
- Never add ONNX Runtime or other engine dependencies to the root project: its assembly is the Lambda jar. Engine code goes in `engine/` (which depends on root) and root reaches it only through `MoveEngineProvider`.
- Keep logic out of the rendering-only classes in `go3d.client.gdx` (`GobanDisplay`, `GeometryBuilder`, `Go3DInputController`, `GDXResources`, `ParticleMarker`): they are excluded from coverage. Put it in testable classes such as `ReplayState`.
- Functional style, `@tailrec` for iteration; mutable collections only in performance-critical paths (`Goban.connectedStones`). Formatting is scalafmt (`.scalafmt.conf`, lines at most 100 characters): run `sbt scalafmtAll` before committing; the CI `format` job fails on unformatted code and on any longer line, which you must split yourself in strings and comments.
- Code quality must only improve, never degrade.

## Known pitfalls

- Never join two interpolated strings with `+` (and never add `return`): WartRemover counts each as a warning and the CI ratchet fails on any increase. Use one interpolation with local `val`s and shorten the text to fit 100 characters.
- `origin` is the GitLab mirror and `github` is the primary remote: push with `git push -u github <branch>`, never `origin`, which creates stray branches on the read-only mirror.

<!-- /bmad:context -->

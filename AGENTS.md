<!-- bmad:context -->
<!-- Verified 2026-11-20 against 63936e8. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## go-3

3D Go (Weiqi/Baduk) on a cubic lattice: game server, ASCII/libGDX/bot clients, and an AWS Lambda + DynamoDB + S3 deployment managed with Terraform. Scala 3.8.2, sbt 1.11.7, http4s + Cats Effect, circe. Architecture, commands and test-game recipes: `docs/development.md`; AWS design and migration plan: `AWS.md`, `docs/aws-phase-6-evaluation-and-lambda-migration-plan.md`.

## Policy

- Ask the user before anything that deploys to or changes a cloud provider: `terraform apply`/`destroy`, workflow dispatches of `terraform.yml`/`terraform-destroy.yml`, AWS CLI writes. Read-only calls and `terraform plan` need no approval.
- The default branch is `master`. Never push to it directly; open a PR on GitHub (`lene/go-3`).
- GitLab (`gitlab.com/go-3/go-3`) is a read-only mirror fed by `mirror-to-gitlab.yml`; never push to it or add build/test/release jobs to `.gitlab-ci.yml`.
- Bump the version together in `build.sbt` (`version :=`), `Dockerfile` (`ARG version=`, twice) and `run-test-game.sh` (`VERSION=`); the `version-consistent` CI job enforces it. If in doubt, bump the patch version.
- Put "CI Fix" in the commit message (or PR title) to skip the bump for CI/Docker/build-script-only changes; never for application code, dependency updates or bug fixes.
- Coverage may only go up: the 0.1pp `COVERAGE_TOLERANCE` in `ci.yml` absorbs measurement noise and is not an allowance. Compiler warnings may only go down: fix new ones or add a justified `@SuppressWarnings` (see `STATIC_ANALYSIS.md`).
- After every push, watch the CI run and fix failures before merging.
- Keep this block current: re-run `bmad-project-context` (refresh) after significant changes to build, CI or infra, and `record` the moment an agent repeats a mistake. Prefer a hook or CI check over a new line.

## Where things are

- Domain: `src/main/scala/go3d/Goban.scala`, `Game.scala`. HTTP routes: `server/http4s/GoHttpService.scala`. Lambda: `server/lambda/LambdaHandler.scala` on top of `server/service/GameService.scala`. Bot strategies: `client/SetStrategy.scala`.
- Adding an endpoint: route in `GoHttpService.goService`, handler extending `BaseHandler`/`MakeMoveTrait`, and the same route in `LambdaHandler`, which serves the same API.
- Infra: `infra/terraform/`. CI: `.github/workflows/`, helper scripts in `ci/`.

## Running and verifying

- Run single suites while iterating (`sbt "testOnly go3d.TestGoban"`); `sbt test` before every push.
- CI runs coverage with `sbt "set Test / parallelExecution := false" clean coverage test`: the suites share global state, so never enable parallel test execution.
- The DynamoDB Local and S3Mock suites skip without `DYNAMODB_TEST_ENDPOINT`/`S3_TEST_ENDPOINT` and only run in CI.
- `.git/hooks/pre-push` is a manual symlink to `.git_hooks/pre-push` (not set up by a fresh clone): it runs `actionlint`, `sbt test` and `docker build`. A latest commit subject starting with `WIP:` bypasses its failures; never use that to push failing code.
- CI builds with Java 17 (`JAVA_VERSION`), the Docker runtime image is `eclipse-temurin:21-jre`.
- Claude Code on the web: `.claude/hooks/session-start.sh` installs an `sbt` wrapper using Google's Maven mirror; compile and run the affected tests there before pushing.

## Conventions that differ from defaults

- Auth header is `Authentication: Bearer <token>`, not `Authorization`.
- `Wart.Throw` is a build error; the few library-interop throws carry `@SuppressWarnings(Array("org.wartremover.warts.Throw"))`.
- Keep logic out of the rendering-only classes in `go3d.client.gdx` (`GobanDisplay`, `GeometryBuilder`, `Go3DInputController`, `GDXResources`, `ParticleMarker`): they are excluded from coverage. Put it in testable classes such as `ReplayState`.
- Functional style, `@tailrec` for iteration; mutable collections only in performance-critical paths (`Goban.connectedStones`). Lines are at most 100 characters.
- Code quality must only improve, never degrade.

## Known pitfalls

- `origin` is the GitLab mirror and `github` is the primary remote: push with `git push -u github <branch>`, never `origin`, which creates stray branches on the read-only mirror.

<!-- /bmad:context -->

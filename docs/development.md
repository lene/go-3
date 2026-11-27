# Development reference

Commands and test-game recipes for go-3. Moved here from `CLAUDE.md`; the rules agents must
follow live in `AGENTS.md`, the structure of the code in [architecture.md](architecture.md).

## Project overview

This is a 3D Go (Weiqi/Baduk) game implementation where the traditional 2D board is extended into
three dimensions on a cubic lattice. The project consists of a server that manages game state and
multiple client types (ASCII, 3D graphical, bot) that connect to play games.

**Static analysis**: see [STATIC_ANALYSIS.md](../STATIC_ANALYSIS.md) for code quality findings from
WartRemover and Scalafix.

## Build system and commands

### Building
```bash
# Build the project (compiles and packages)
sbt universal:packageBin

# Compile only
sbt compile

# Clean build artifacts
sbt clean
```

In Claude Code on the web, the SessionStart hook `.claude/hooks/session-start.sh` installs an
`sbt` wrapper in `~/.local/bin` that resolves everything from Google's mirror of Maven Central
(the default sbt repository is not reachable there), and compiles once to warm the cache.

The `universal:packageBin` command creates `target/universal/go-3d-<VERSION>.zip` containing
executables in `bin/` and libraries in `lib/`.

### Testing
```bash
# Run all tests
sbt test

# Run a specific test class
sbt "testOnly go3d.TestGoban"

# Run with test coverage
sbt clean coverage test coverageReport
```

### Running components

#### Server
```bash
# From sbt
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves"

# Benchmark mode (runs random games for performance testing)
sbt "runMain go3d.server.GoServer --benchmark 5 --print-step-size 10"
```

#### Clients
```bash
# ASCII client (for playing)
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --size 7 --color b"
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --game-id XXXXX --color w"

# GDX client (3D visualization, watch-only)
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id XXXXX"

# GDX client replaying a game (Space/Backspace step forward/back); prints the current move on
# one stdout line, e.g. "[10/245] black @ 3 2 3" followed by a carriage return
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id XXXXX --replay"

# Bot client
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --size 7 --color b --strategy prioritiseCapture,closestToCenter"

# Any client against a server by URL instead of --server/--port, e.g. the Lambda API over HTTPS;
# --poll-interval-ms keeps bots below the API Gateway throttling limit
sbt "runMain go3d.client.BotClient --url https://abc.execute-api.eu-central-1.amazonaws.com --game-id XXXXXX --color w --poll-interval-ms 500"
```

#### Engine benchmark

`go3d.EngineBenchmark` (test scope) times what self-play calls on the rules engine:
`possibleMoves`, `makeMove`, `score` and `isOver` on a mid-game and a late-game position, and a
random playout from the start, per move. The positions come from random games with a fixed seed,
and each measurement is warmed up for as long as it runs (2 s), so a run of sizes 5, 7 and 9 takes
about 2 minutes.

```bash
sbt "Test/runMain go3d.EngineBenchmark 5 7 9"
```

`TestRulesAgainstReference` is the differential test of the rules: it plays seeded random games
on sizes 3 and 5 through `Game` and through `ReferenceRules`, a plain implementation written from
the rules, and compares the board, the captures, the legal moves, the reason each other point is
illegal, the end of the game and the score after every move. A faster implementation of the rules
has to pass it unchanged.

#### Rules probe

The rules probe (`src/test/scala/go3d/probe/`, story
`_bmad-output/specs/spec-alphazero-engine/stories/3-rules-probe.md`) plays the 8 `SetStrategy`
strategies against each other and themselves on sizes 5, 7 and 9 at komi 0.5. It scores every
game with area scoring and with area scoring plus captures. It runs in process, with no server.

```bash
# Smoke run: two games per pairing on size 5 (128 games)
sbt "Test/runMain go3d.probe.RulesProbe --sizes 5 --games 2 --seed 1 --out results/rules-probe/smoke"

# Full run with a budget per size
sbt "Test/runMain go3d.probe.RulesProbe --games 5=200,7=100,9=20 --out results/rules-probe"
```

Options: `--sizes 5,7,9`, `--games N` or `--games 5=N,7=N,9=N` (games per pairing), `--seed N`
(default 1), `--parallelism N` (default: the number of processors), `--out DIR` (default
`results/rules-probe`, git-ignored), `--strategies a,b,c` (default: all 8). Options that are not
valid run nothing and exit with code 2.

A size is played in rounds of one game per pairing. `--time-limit 5h` (or `90s`, `30m`) stops a
size after the round in which its time is up, so `--games` becomes the most games per pairing:

```bash
sbt "Test/runMain go3d.probe.RulesProbe --sizes 7,9 --games 200 --time-limit 5h --out results/rules-probe/run-5"
```

Every pairing has the same number of games, and a size that stops after n rounds has exactly the
games of `--games n`. The report gives the games per pairing that were played. The CSV and the
sample records are written after every round, so a run that is stopped keeps the rounds it played.

`--report-from DIR[,DIR...]` plays nothing. It writes `report.md` to `--out` from the
`games-<size>.csv` files of earlier runs, taking each of the `--sizes` from the last listed folder
that has its CSV. This combines runs, for example size 5 of one run with sizes 7 and 9 of another:

```bash
sbt "Test/runMain go3d.probe.RulesProbe --report-from results/rules-probe/run-3,results/rules-probe/run-4 --out results/rules-probe/combined"
```

The CSV files do not record the seed, the parallelism or the time per size, so the rebuilt report
leaves them out.

The output directory gets:

- `games-<size>.csv`: one row per game. The same options and seed give the same rows, whatever
  the parallelism.
- `report.md`: the numbers used, the stop rule and the metrics for each size: Black win rates,
  ratings with a first-move term, fitted komi, game length, territory, captures and flip rate.
- `archive/`: about three sample games per pairing. `go-server --save-dir <out>` serves them to the
  viewer.

The stop rule fires when the flip rate, pooled over a size's pairings, is at least 10% at any size.
The flip rate is the share of games whose winner changes when the captures are added. The report
only says whether the rule fired: whether to keep area scoring or run the size-5 rules lab is the
user's decision.

### Training (Python)

`training/` is a separate [uv](https://docs.astral.sh/uv/) project (Python 3.12, PyTorch, ONNX)
for the AlphaZero engine; the Poetry project at the root is unrelated. It defines the board
encoding, the action indexing and the policy-and-value network of
[model-contract.md](model-contract.md), exports the untrained generation 0 to ONNX and trains
the next generations from the self-play records. The CI job `Training (Python)` runs its lint,
type check and tests.

```bash
cd training
uv sync                    # once: creates training/.venv, with the CUDA build of PyTorch on Linux
uv run pytest              # all tests
uv run ruff check          # lint (rules and line length 100 in pyproject.toml)
uv run ruff format --check # formatting; `uv run ruff format` fixes it
uv run mypy                # type check of go3d_training

# Untrained generation 0 for size 5: writes ../models/5/0/model.onnx and model.pt
# (models/ is git-ignored)
uv run export-generation --size 5 --generation 0 --out ../models
```

Options: `--seed N` (default 0, the same seed gives the same network), `--blocks N` (default 6),
`--channels N` (default 64). An even size, a size below 3 or above 25, a blocks or channels count
below 1, a generation other than 0 (`train-generation` makes the others) or an existing
`<out>/<size>/0/` writes nothing and exits with code 2: with another seed or shape, generation 0
would no longer be the network that played its records.

Without a GPU, or to keep the download small, take the CPU build of PyTorch as CI does. `uv run`
syncs the environment back to the default build unless it is given the same extra:

```bash
uv sync --extra cpu
uv run --extra cpu pytest  # or uv run --no-sync pytest
```

A candidate for generation `g` is trained from the checkpoint of the promoted generation `g - 1`
on the self-play records of generations `g - window` to `g - 1`
([self-play-records.md](self-play-records.md)):

```bash
# after: sbt "engine/runMain go3d.engine.SelfPlay --size 5 --generation 0 --games 200"
cd training
uv run train-generation --size 5 --generation 1   # writes ../models/5/candidates/1/
```

Options: `--models DIR` (default `../models`), `--records DIR` (default `../selfplay`),
`--window N` (default 2), `--epochs N` (default 10), `--batch-size N` (default 256),
`--learning-rate X` (default 0.001, AdamW with weight decay 1e-4), `--seed N` (default 0) and
`--device cuda|cpu` (default cuda, the CPU when CUDA is not available). Each move of a record is
a sample, trained under a random one of the cube's 48 symmetries; the loss is the policy
cross-entropy against the search's visit shares plus the value's mean squared error. The same
seed and records give the same weights on the CPU. The candidate's komi is refitted from the
records of `g - 1`: the half-integer nearest the median Black-minus-White area margin, the one
above an integer median, within ±(size³ - 0.5). Every value target is the result at that komi,
which the candidate's metadata carries, not at the komi the game was played at. The losses of
each epoch are printed and kept in `training.json` (see [model-contract.md](model-contract.md)).

A missing checkpoint of `g - 1` (a generation 0 exported before 0.7.56 has none: re-export it with
the same `--seed`, `--blocks` and `--channels` to another `--out` and copy its `model.pt` next to
the `model.onnx`), a checkpoint that is not the network of the
`model.onnx` next to it (board size, blocks, channels or generation), no records in the window or
none of `g - 1`, a record that is not valid, or an existing candidate or promoted generation `g`
writes nothing and exits with code 2. The candidate only becomes
generation `g` in `models/5/g/` once it beats `g - 1` in a promotion match (see Promotion below).

### Engine

The sbt subproject `engine` (`engine/src/main/scala/go3d/engine/`) plays a model generation: it
loads `<store>/<size>/<generation>/model.onnx` with ONNX Runtime, refuses a model
whose contract (`contract_version`, `board_size`, `input_planes`, `actions`, `scoring_rule`) does
not fit the engine and the game, and chooses each move with a PUCT tree search on `Game`. A game
whose komi differs from the model's is played with a warning. The root project aggregates
`engine` for `compile`, `test` and the format check, but never depends on it: `sbt assembly` (the
Lambda jar) and `Universal / packageBin` are built from root alone, without ONNX Runtime.

`BotClient` plays a model with `--strategy model:<generation>` when it runs from the engine's
classpath; run from root, that strategy fails with a message. The model is chosen by the size of
the game:

```bash
# Generation 0 of size 5 (see Training above), against a heuristic bot
cd training; uv run export-generation --size 5 --generation 0 --out ../models; cd ..
sbt "engine/runMain go3d.client.BotClient --server localhost --port 6030 --size 5 --color b --strategy model:0 --simulations 100"
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --game-id XXXXX --color w --strategy closestToCenter"
```

Options: `--model-store DIR` (default `models`, relative to the directory sbt runs in) and
`--simulations N` (default 800 per move; a size-5 game then takes several minutes, 100 is
enough to check that a model plays). `--max-thinking-time-ms` and `--parallel` apply to the
heuristics only and are ignored for `model:`. A missing model, a model for another size or
another contract, and a strategy that combines `model:` with heuristics play nothing and exit
with code 1. The engine tests use a fake evaluator for the search and a small committed
fixture, `engine/src/test/resources/models/5/0/model.onnx` (`export-generation --size 5
--generation 0 --blocks 1 --channels 4`); `TestOnnxModel` pins its outputs as computed in
Python.

ONNX Runtime is the GPU build (`onnxruntime_gpu`, CUDA 13 and cuDNN 9), which has the CPU
provider too: `BotClient` and the tests run on the CPU, self-play on CUDA when it can. The GPU
jar is about 400 MB and has native libraries for linux-x64 and win-x64 only: unlike the CPU jar,
not for macOS or linux-aarch64, so the engine does not run there.

#### Self-play

`SelfPlay` plays a generation against itself and writes one record per game for training, in
the format of [self-play-records.md](self-play-records.md):

```bash
sbt "engine/runMain go3d.engine.SelfPlay --size 5 --generation 0 --games 4 --simulations 50"
```

The records go to `selfplay/5/0/<game id>.json` (`selfplay/` is git-ignored). Each game starts
at the komi of the model. The search adds Dirichlet noise to the root priors (α = 10 / the
number of legal actions, ε = 0.25); the first size³ / 8 moves (15 on size 5) are drawn in
proportion to the root visits, the later ones are the most visited. At the end it prints the
games and moves played, the time per simulation in the network (`Evaluator.evaluate`) and in
the engine (the rest of the search and the rules), and Black's win rate. The times are summed
over the games played at the same time, so with `--parallel` they are thread time, not wall
time.

Options: `--simulations N` (default 200 per move), `--seed N` (default 1), `--parallel N` games at
the same time sharing one model (default 1), `--model-store DIR` (default `models`), `--out DIR`
(default `selfplay`), `--device cuda|cpu` (default `cuda`) and `--resume`. Without a usable CUDA
provider (no GPU, CUDA or cuDNN) `cuda` logs a warning and runs on the CPU; the report names the
device used. ONNX Runtime loads its native libraries once per JVM, so run one `SelfPlay` per sbt
invocation: a second `runMain` in the same sbt session cannot load the CUDA provider again and falls
back to the CPU. The same seed and options give the same records with `--parallel 1` on the CPU.
Options that are not valid (`--games 0`, a size other than 5, no `--generation`) write nothing and
exit with code 2, a missing model with code 1. A run whose records exist already exits with code 1
before it plays, unless `--resume` is given: then it plays only the games of the run that have no
record yet, which finishes an interrupted run with the games of an uninterrupted one. Each record is
written to a temporary file and renamed, so an interrupted run leaves no partial record.

#### Promotion

`Match` plays the candidate for generation `g` (`models/5/candidates/g/`) against the promoted
generation `g - 1`, and promotes it if it wins more than the threshold:

```bash
sbt "engine/runMain go3d.engine.Match --size 5 --generation 1"
```

Each side is Black in half the games. Every game starts at the candidate's komi (the refitted
one of its metadata). Each side searches with its own network, with `--simulations` per move
and no root noise; the first size³ / 8 moves are drawn in proportion to the root visits, so the
games differ, the later ones are the most visited. The result goes as one line to the match log
`models/5/matches.jsonl` ([model-contract.md](model-contract.md), Storage), promoted or not.
Then the candidate's directory is renamed: to `models/5/g/` if it won, else to
`models/5/rejected/g-<attempt>/`, the attempt counting from 1, which frees `candidates/g/` for
the next try. The report gives the candidate's wins as Black and as White.

Options: `--games N` (default 100, an even number), `--threshold N` (default 55: promoted with
56 wins or more), `--simulations N` (default 200), `--seed N` (default 1; the same seed and
models give the same games on the CPU), `--parallel N`, `--model-store DIR` and `--device
cuda|cpu` as for self-play. Options that are not valid exit with code 2. A promoted generation
`g`, a missing candidate or generation `g - 1`, or a model that does not load plays, logs and
renames nothing and exits with code 1.

`promotion-loop` chains self-play, training and the match until it has promoted `N`
candidates. Run it in `training/`; the JVM steps run as `sbt "engine/runMain ..."` in the
repository root, one JVM each, since ONNX Runtime loads CUDA once per JVM:

```bash
cd training
uv run promotion-loop --size 5 --promotions 1
```

With `p` the latest promoted generation, `g = p + 1` and `attempt` one more than the highest
rejected attempt of `g` (1 if none), it works out the next step from the store and the records:
the match if `candidates/g/` exists; else a self-play batch of `p` if `selfplay/5/p/` has fewer
than `attempt` batches of records (batch `k` has seed `k` and `--resume`); else training of `g`.
After a rejection, `p` thus plays another batch, which adds to its records, and `g` is
retrained on the larger set; the match's seed is the attempt. A rerun after an interruption
therefore goes on with the step that was interrupted. It stops after `--promotions N`
promotions, and exits with code 1 after `--max-rejections K` (default 3) rejections in a row in
the run, naming the candidate and the last match, or at the first step that fails.

Options: `--selfplay-games N` (default 200 per batch: keep it the same across runs, the batches
are counted by it), `--match-games N` (default 100), `--threshold N` (default 55),
`--simulations N` (default 200, for self-play and the match), `--parallel N` (games at the same
time in the JVM, default 1), `--device cuda|cpu` (default cuda), `--models DIR` (default
`../models`), `--records DIR` (default `../selfplay`), `--repository DIR` (where sbt runs,
default `..`) and `--sbt CMD` (default `sbt`). The paths are passed to sbt as absolute paths and
may not contain spaces.

### Docker
```bash
# Build Docker image
docker build -t ghcr.io/lene/go-3/server .

# Run server in Docker
docker run --net=host --env PORT=6030 --env SAVE_DIR=saves -t ghcr.io/lene/go-3/server:latest
```

## CI workflows

- `ci.yml`: tests, coverage ratchet, benchmarks, version/changelog checks, deployable and Docker
  image (`ghcr.io/lene/go-3/server`), Trivy container scan and gameplay tests
  (`ci/test-gameplay.sh`)
- `release.yml`: after CI passes on master, creates the tag and GitHub release for a new version
- `mirror-to-gitlab.yml`: replicates branches and tags to GitLab

The `Check coverage` job compares statement coverage of root and `engine` together
(`coverageAggregate`) against the last successful master run and fails if it drops by more than
0.1 percentage points (`COVERAGE_TOLERANCE` in `ci.yml`). It runs the test suites serially
because they share global state, and prints the files with the most uncovered statements
(`ci/coverage_summary.py`).

Rendering-only classes in `go3d.client.gdx` (`GobanDisplay`, `GeometryBuilder`,
`Go3DInputController`, `GDXResources`, `ParticleMarker`) need an OpenGL context, so they are
excluded from coverage (`coverageExcludedFiles` in `build.sbt`). See `ReplayState.hudLines` for
the pattern of keeping logic in testable classes.

The `Check warnings` job compiles main and test sources and fails if the number of compiler
warnings (WartRemover, `-Wunused`, deprecations) is higher than on the last successful master run;
`ci/warning_summary.sh` prints them by kind and file (see STATIC_ANALYSIS.md and issue #143).

Every sbt step in CI and in the Dockerfile runs through `ci/sbt-retry.sh`. Maven Central answers
bursts from the shared runner IPs with HTTP 429 and no `Retry-After`, so when sbt fails with a
download error the script waits 2, 5 and 10 minutes and tries again; a compile error or a failing
test fails at once. The `CI scripts` job runs its self-test (`ci/test-sbt-retry.sh`) and fails
on an sbt step that does not use it. `.github/actions/setup-jvm` caches the coursier, ivy and sbt
directories with `coursier/cache-action`, which falls back to the newest earlier cache, so a PR
that bumps the version in `build.sbt` starts from master's cache. Jobs share one cache per
`cache-group` input: `full` for those that resolve the engine (its ONNX Runtime jar dominates),
`root` for the rest; a cache per job would overflow the repository's 10 GB cache limit.

## Testing and development workflow

### Starting a game session

Start the server, create a game, register both players and make moves via curl as described
in [HOWTO-PLAY.md](../HOWTO-PLAY.md). To watch as an authenticated player (shows the
cursors), start the GDX client with the player's token:

```bash
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id GAME_ID --token TOKEN1" &
```

### Taking screenshots for analysis

Use `scrot` to capture the screen:

```bash
# Take full screen screenshot
scrot /tmp/screenshot.png

# Take screenshot with 3 second delay (useful for capturing menus)
scrot -d 3 /tmp/screenshot.png

# Take screenshot of focused window only
scrot -u /tmp/screenshot.png
```

To analyze one with Claude Code: take the screenshot, then load it with the Read tool
(`Read("/tmp/screenshot.png")`). It can show the 3D board state, cursor positions and colors,
stone placement and UI elements.

### GDX client cursor system

The GDX 3D client shows two cursors when authenticated:

- **Green cursor**: Marks the player's own last move
- **Red cursor**: Marks the opponent's last move
- Both cursors are wireframe spheres (1.1x stone size) positioned at grid intersections
- Cursors only appear when client is authenticated with `--token`
- Watch-only clients (no token) see no cursors

Implementation details:

- Green cursor color: `ColorAttribute(0.2, 0.5, 0.1)` - greenish
- Red cursor color: `ColorAttribute(0.5, 0.1, 0.1)` - reddish
- Both use `GL20.GL_LINES` rendering mode for wireframe appearance
- Position calculated with board-centering offset: `-(boardSize+1)/2`
- Uses `setToTranslationAndScaling()` for absolute positioning (not additive `translate()`)

### Common test scenarios

#### Testing cursor positioning
```bash
# Create game and register both players
GAME_ID=$(curl -s http://localhost:6030/new/5 | jq -r '.id')   # add ?komi=6.5 for another komi
BLACK_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/@" | jq -r '.authToken')
WHITE_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')

# Start GDX client as Black
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id $GAME_ID --token $BLACK_TOKEN" &

# Make moves and take screenshots
curl -s -H "Authentication: Bearer $BLACK_TOKEN" "http://localhost:6030/set/$GAME_ID/3/3/3"
sleep 2
scrot /tmp/after_black_move.png

curl -s -H "Authentication: Bearer $WHITE_TOKEN" "http://localhost:6030/set/$GAME_ID/4/3/3"
sleep 2
scrot /tmp/after_white_move.png
```

#### Testing bot vs bot game
```bash
# Start two bots playing each other
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --size 5 --color @ --strategy closestToCenter" &
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --game-id GAME_ID --color O --strategy random" &
```

### Debugging tips

- Check client output for cursor rendering: `grep "rendering.*cursor"` in sbt output
- Verify player color is set: Check `client.playerColor` is `Some(Black)` or `Some(White)`
- Check move history: `curl -s http://localhost:6030/status/GAME_ID | jq '.game.moves'`
- Monitor server logs for authentication issues

## Common patterns

When adding new client features:
1. Extend `InteractiveClient` trait
2. Implement `mainLoop(args: Array[String])`
3. Use `BaseClient` for server communication
4. Handle authentication tokens for move requests

When adding new bot strategies:
1. Add strategy case in `SetStrategy.narrowDown`
2. Implement method returning `Seq[Position]`
3. Use `bestBy` helper for ranking positions by metric

When adding new server endpoints:
1. Add route pattern in `GoHttpService.goService`
2. Create handler class extending `BaseHandler` or `MakeMoveTrait`
3. Implement `response` method returning `IO[Response[IO]]`
4. Add authentication check if needed

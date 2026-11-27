"""The promotion loop: self-play, training and the promotion match, until enough promotions.

Run from `training/`, `promotion-loop --size 5 --promotions 1` works out its next step from the
model store, the self-play records and the match log, so that a loop that was interrupted goes on
where it stopped when it is started again. With `p` the latest promoted generation, `g = p + 1`
and `attempt` one more than the highest attempt of `g` that a match rejected (1 if none):

- a candidate for `g` exists: its match against `p` (`go3d.engine.Match`), which promotes it or
  sets it aside;
- `p` has fewer than `attempt` batches of self-play records: the next batch, whose seed is its
  number (`go3d.engine.SelfPlay --resume`, which finishes an interrupted batch);
- else: training of the candidate for `g` on the larger record set (`train-generation`).

The JVM steps run as `sbt "engine/runMain ..."` in the repository root, one JVM per step, since
ONNX Runtime loads the CUDA provider once per JVM; training runs in a Python process of its own.
The loop stops after `--promotions` promotions, and exits with an error after
`--max-rejections` rejections in a row in this run, or at the first step that fails.
"""

import argparse
import json
import shlex
import subprocess
import sys
import time
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from go3d_training.records import record_paths
from go3d_training.store import (
    candidate_dir,
    generation_dir,
    match_log_path,
    promoted_generations,
    rejected_attempts,
)
from go3d_training.train import CPU, CUDA, DEFAULT_MODELS, DEFAULT_RECORDS, TRAINING_SIZES

PROGRAM = "promotion-loop"
FAILURE_EXIT_CODE = 1
DEFAULT_REPOSITORY = Path("..")
DEFAULT_SBT = "sbt"
DEFAULT_MAX_REJECTIONS = 3
DEFAULT_SELFPLAY_GAMES = 200
DEFAULT_MATCH_GAMES = 100
DEFAULT_THRESHOLD = 55
DEFAULT_SIMULATIONS = 200
ENGINE_MAIN = "engine/runMain go3d.engine"
TRAINING_MODULE = "go3d_training.train"


class LoopError(Exception):
    """A loop that cannot go on: a step failed, or the store does not allow the next one."""


@dataclass(frozen=True)
class LoopOptions:
    size: int
    promotions: int
    max_rejections: int = DEFAULT_MAX_REJECTIONS
    models: Path = DEFAULT_MODELS
    records: Path = DEFAULT_RECORDS
    repository: Path = DEFAULT_REPOSITORY
    selfplay_games: int = DEFAULT_SELFPLAY_GAMES
    match_games: int = DEFAULT_MATCH_GAMES
    threshold: int = DEFAULT_THRESHOLD
    simulations: int = DEFAULT_SIMULATIONS
    parallel: int = 1
    device: str = CUDA
    sbt: str = DEFAULT_SBT


@dataclass(frozen=True)
class SelfPlayStep:
    """Batch `seed` of self-play records of the promoted generation `generation`."""

    generation: int
    seed: int

    def describe(self) -> str:
        return f"self-play of generation {self.generation}, batch {self.seed}"


@dataclass(frozen=True)
class TrainStep:
    """Training of the candidate for `generation`."""

    generation: int

    def describe(self) -> str:
        return f"training of candidate {self.generation}"


@dataclass(frozen=True)
class MatchStep:
    """Attempt `attempt` of the candidate for `generation` in its promotion match."""

    generation: int
    attempt: int

    def describe(self) -> str:
        return f"match of candidate {self.generation}, attempt {self.attempt}"


Step = SelfPlayStep | TrainStep | MatchStep


@dataclass(frozen=True)
class Command:
    """The process that runs `step`: `args`, started in `cwd` (the current directory if None)."""

    step: Step
    args: list[str]
    cwd: Path | None


Runner = Callable[[Command], int]


def run_command(command: Command) -> int:
    """Run `command` with the loop's stdout and stderr, and return its exit code."""
    try:
        return subprocess.run(command.args, cwd=command.cwd, check=False).returncode
    except OSError as error:
        raise LoopError(f"{command.step.describe()} could not start: {error}") from error


def next_step(options: LoopOptions) -> Step:
    """The step that the store, the records and the match log call for next."""
    promoted = promoted_generations(options.models, options.size)
    if not promoted:
        directory = options.models / str(options.size)
        raise LoopError(f"no promoted generation in {directory}: export generation 0 first")
    parent = promoted[-1]
    generation = parent + 1
    attempt = max(rejected_attempts(options.models, options.size, generation), default=0) + 1
    if candidate_dir(options.models, options.size, generation).is_dir():
        return MatchStep(generation, attempt)
    records = len(record_paths(options.records, options.size, parent))
    if records < attempt * options.selfplay_games:
        return SelfPlayStep(parent, records // options.selfplay_games + 1)
    return TrainStep(generation)


def command(step: Step, options: LoopOptions) -> Command:
    """The process that runs `step` with `options`."""
    models = _absolute(options.models)
    common = ["--size", str(options.size), "--generation", str(step.generation)]
    engine = [
        *("--simulations", str(options.simulations), "--parallel", str(options.parallel)),
        *("--model-store", models, "--device", options.device),
    ]
    match step:
        case SelfPlayStep(seed=seed):
            out = _absolute(options.records)
            games = ["--games", str(options.selfplay_games), "--seed", str(seed)]
            return _engine(step, options, "SelfPlay", [*common, *games, *engine, "--out", out])
        case TrainStep():
            records = _absolute(options.records)
            stores = ["--models", models, "--records", records, "--device", options.device]
            return Command(step, [sys.executable, "-m", TRAINING_MODULE, *common, *stores], None)
        case MatchStep(attempt=attempt):
            games = ["--games", str(options.match_games), "--threshold", str(options.threshold)]
            match_seed = ["--seed", str(attempt)]
            return _engine(step, options, "Match", [*common, *games, *match_seed, *engine])


def _absolute(path: Path) -> str:
    """`path` as the JVM gets it in an sbt command, which splits its arguments at spaces."""
    absolute = str(path.resolve())
    if any(character.isspace() for character in absolute):
        raise LoopError(f"{absolute} has a space, which an sbt command cannot pass")
    return absolute


def _engine(step: Step, options: LoopOptions, main: str, args: list[str]) -> Command:
    resume = ["--resume"] if isinstance(step, SelfPlayStep) else []
    arguments = " ".join([f"{ENGINE_MAIN}.{main}", *args, *resume])
    return Command(step, [*shlex.split(options.sbt), arguments], options.repository)


def last_match(options: LoopOptions, step: MatchStep) -> dict[str, Any]:
    """The last line of the match log, which the match of `step` wrote."""
    log = match_log_path(options.models, options.size)
    lines = log.read_text().splitlines() if log.is_file() else []
    match: dict[str, Any] = json.loads(lines[-1]) if lines else {}
    if (match.get("generation"), match.get("attempt")) != (step.generation, step.attempt):
        raise LoopError(f"the {step.describe()} left no line in {log}")
    return match


def run_loop(options: LoopOptions, runner: Runner = run_command) -> int:
    """Run steps until `options.promotions` promotions; return the latest promoted generation."""
    promotions = rejections = 0
    while promotions < options.promotions:
        step = next_step(options)
        _run(command(step, options), runner)
        if next_step(options) == step:
            raise LoopError(f"{step.describe()} ended without changing the store")
        if isinstance(step, MatchStep):
            match = last_match(options, step)
            if generation_dir(options.models, options.size, step.generation).is_dir():
                promotions, rejections = promotions + 1, 0
            else:
                rejections += 1
                if rejections >= options.max_rejections:
                    times = f"{rejections} times in a row"
                    raise LoopError(
                        f"candidate {step.generation} was rejected {times}, last match: "
                        + json.dumps(match)
                    )
    return promoted_generations(options.models, options.size)[-1]


def _run(command: Command, runner: Runner) -> None:
    """Run `command`, with its duration, or raise a LoopError if it fails."""
    description = command.step.describe()
    print(f"{PROGRAM}: {description}: {' '.join(command.args)}", flush=True)
    started = time.monotonic()
    code = runner(command)
    if code != 0:
        raise LoopError(f"{description} failed with exit code {code}")
    print(f"{PROGRAM}: {description} took {time.monotonic() - started:.0f} s", flush=True)


def _positive_int(text: str) -> int:
    value = int(text)
    if value < 1:
        raise argparse.ArgumentTypeError(f"{value} is not 1 or more")
    return value


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog=PROGRAM,
        description="Run self-play, training and promotion matches until <promotions> "
        "candidates are promoted; a rerun goes on where an interrupted run stopped.",
    )
    sizes = ", ".join(map(str, TRAINING_SIZES))
    parser.add_argument("--size", type=int, required=True, choices=TRAINING_SIZES, help=sizes)
    parser.add_argument("--promotions", type=_positive_int, required=True, help="1 or more")
    parser.add_argument(
        "--max-rejections",
        type=_positive_int,
        default=DEFAULT_MAX_REJECTIONS,
        help="stop with an error after this many rejections in a row",
    )
    parser.add_argument("--models", type=Path, default=DEFAULT_MODELS, help="the model store")
    parser.add_argument(
        "--records", type=Path, default=DEFAULT_RECORDS, help="the self-play records"
    )
    parser.add_argument(
        "--repository", type=Path, default=DEFAULT_REPOSITORY, help="where sbt runs"
    )
    parser.add_argument(
        "--selfplay-games",
        type=_positive_int,
        default=DEFAULT_SELFPLAY_GAMES,
        help="games of a self-play batch; keep it the same across runs",
    )
    parser.add_argument("--match-games", type=_positive_int, default=DEFAULT_MATCH_GAMES)
    parser.add_argument(
        "--threshold",
        type=int,
        default=DEFAULT_THRESHOLD,
        help="a candidate is promoted with more wins than this",
    )
    parser.add_argument("--simulations", type=_positive_int, default=DEFAULT_SIMULATIONS)
    parser.add_argument(
        "--parallel", type=_positive_int, default=1, help="games at the same time in the JVM"
    )
    parser.add_argument(
        "--device", choices=(CUDA, CPU), default=CUDA, help="cuda falls back to the CPU"
    )
    parser.add_argument("--sbt", default=DEFAULT_SBT, help="the sbt command, e.g. 'sbt --client'")
    return parser


def main(argv: Sequence[str] | None = None, runner: Runner = run_command) -> None:
    parser = _parser()
    args = parser.parse_args(argv)
    # every option of the parser is a field of LoopOptions under the same name
    options = LoopOptions(**vars(args))
    if options.match_games % 2:
        parser.error("--match-games must be even: each side is Black in half the games")
    if not 0 <= options.threshold < options.match_games:
        parser.error("--threshold must be from 0 to one less than --match-games")
    try:
        latest = run_loop(options, runner)
    except LoopError as error:
        parser.exit(FAILURE_EXIT_CODE, f"{PROGRAM}: error: {error}\n")
    print(f"{PROGRAM}: {options.promotions} promoted, generation {latest} is the latest")

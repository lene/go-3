import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

from go3d_training.loop import (
    Command,
    LoopError,
    LoopOptions,
    MatchStep,
    SelfPlayStep,
    Step,
    TrainStep,
    command,
    main,
    next_step,
    run_loop,
)
from go3d_training.store import (
    MODEL_FILE_NAME,
    candidate_dir,
    generation_dir,
    match_log_path,
    record_dir,
    rejected_dir,
)

SIZE = 5
GAMES = 4


@pytest.fixture
def options(tmp_path) -> LoopOptions:
    """A store with generation 0 and no records, and small batches."""
    models = tmp_path / "models"
    make_model(generation_dir(models, SIZE, 0))
    return LoopOptions(
        size=SIZE,
        promotions=1,
        models=models,
        records=tmp_path / "selfplay",
        repository=tmp_path,
        selfplay_games=GAMES,
        match_games=4,
        threshold=2,
        device="cpu",
    )


def make_model(directory: Path) -> None:
    directory.mkdir(parents=True)
    (directory / MODEL_FILE_NAME).write_text("model")


def add_records(options: LoopOptions, generation: int, count: int) -> None:
    directory = record_dir(options.records, SIZE, generation)
    directory.mkdir(parents=True, exist_ok=True)
    start = len(list(directory.glob("*.json")))
    for number in range(start, start + count):
        (directory / f"{number:06d}.json").write_text("{}")


def reject(options: LoopOptions, generation: int, attempt: int) -> None:
    make_model(rejected_dir(options.models, SIZE, generation, attempt))


class FakeSteps:
    """Does to the store what each step does, and records the steps; the matches promote or
    reject in the order of `outcomes`. With `interrupt_at`, that step is interrupted instead:
    a self-play batch writes half its records first."""

    def __init__(self, options: LoopOptions, outcomes: list[bool], interrupt_at: int = -1):
        self.options = options
        self.outcomes = list(outcomes)
        self.interrupt_at = interrupt_at
        self.steps: list[Step] = []

    def __call__(self, command: Command) -> int:
        step = command.step
        if len(self.steps) == self.interrupt_at:
            if isinstance(step, SelfPlayStep):
                add_records(self.options, step.generation, GAMES // 2)
            raise KeyboardInterrupt
        self.steps.append(step)
        match step:
            case SelfPlayStep(generation=generation):
                directory = record_dir(self.options.records, SIZE, generation)
                written = len(list(directory.glob("*.json"))) if directory.is_dir() else 0
                add_records(self.options, generation, step.seed * GAMES - written)
            case TrainStep(generation=generation):
                make_model(candidate_dir(self.options.models, SIZE, generation))
            case MatchStep(generation=generation, attempt=attempt):
                self.match(generation, attempt, self.outcomes.pop(0))
        return 0

    def match(self, generation: int, attempt: int, promoted: bool) -> None:
        models = self.options.models
        log = match_log_path(models, SIZE)
        line = {"generation": generation, "attempt": attempt, "promoted": promoted}
        with log.open("a") as file:
            file.write(json.dumps(line) + "\n")
        target = (
            generation_dir(models, SIZE, generation)
            if promoted
            else rejected_dir(models, SIZE, generation, attempt)
        )
        target.parent.mkdir(parents=True, exist_ok=True)
        os.rename(candidate_dir(models, SIZE, generation), target)


def test_no_promoted_generation_is_an_error(options, tmp_path):
    empty = LoopOptions(size=SIZE, promotions=1, models=tmp_path / "none")
    with pytest.raises(LoopError, match="export generation 0"):
        next_step(empty)


def test_generation_without_records_plays_the_first_batch(options):
    assert next_step(options) == SelfPlayStep(0, 1)


def test_interrupted_batch_is_played_again_to_finish_it(options):
    add_records(options, 0, GAMES - 1)
    assert next_step(options) == SelfPlayStep(0, 1)


def test_full_batch_trains_the_candidate(options):
    add_records(options, 0, GAMES)
    assert next_step(options) == TrainStep(1)


def test_candidate_plays_its_match(options):
    add_records(options, 0, GAMES)
    make_model(candidate_dir(options.models, SIZE, 1))
    assert next_step(options) == MatchStep(1, 1)


def test_rejection_asks_for_the_next_batch_then_training_then_the_next_attempt(options):
    add_records(options, 0, GAMES)
    reject(options, 1, 1)
    assert next_step(options) == SelfPlayStep(0, 2)
    add_records(options, 0, GAMES)
    assert next_step(options) == TrainStep(1)
    make_model(candidate_dir(options.models, SIZE, 1))
    assert next_step(options) == MatchStep(1, 2)


def test_promoted_generation_plays_its_own_first_batch(options):
    add_records(options, 0, GAMES)
    make_model(generation_dir(options.models, SIZE, 1))
    assert next_step(options) == SelfPlayStep(1, 1)


def test_rejections_of_other_generations_do_not_count(options):
    add_records(options, 0, GAMES)
    reject(options, 2, 1)
    reject(options, 11, 1)
    assert next_step(options) == TrainStep(1)


def test_self_play_command_resumes_its_batch_in_the_repository(options):
    step = SelfPlayStep(0, 2)
    run = command(step, options)
    assert run.cwd == options.repository
    assert run.args[0] == "sbt"
    [arguments] = run.args[1:]
    expected = (
        "engine/runMain go3d.engine.SelfPlay --size 5 --generation 0 --games 4 --seed 2 "
        f"--simulations 200 --parallel 1 --model-store {options.models.resolve()} --device cpu "
        f"--out {options.records.resolve()} --resume"
    )
    assert arguments == expected


def test_match_command_has_the_attempt_as_seed(options):
    run = command(MatchStep(1, 3), options)
    assert run.cwd == options.repository
    expected = (
        "engine/runMain go3d.engine.Match --size 5 --generation 1 --games 4 --threshold 2 "
        f"--seed 3 --simulations 200 --parallel 1 --model-store {options.models.resolve()} "
        "--device cpu"
    )
    assert run.args == ["sbt", expected]


def test_training_command_runs_train_generation(options):
    run = command(TrainStep(1), options)
    assert run.cwd is None
    assert run.args == [
        sys.executable,
        *("-m", "go3d_training.train", "--size", "5", "--generation", "1"),
        *("--models", str(options.models.resolve())),
        *("--records", str(options.records.resolve()), "--device", "cpu"),
    ]


def test_path_with_a_space_is_refused_for_sbt(options, tmp_path):
    spaced = LoopOptions(size=SIZE, promotions=1, models=tmp_path / "my models")
    with pytest.raises(LoopError, match="space"):
        command(MatchStep(1, 1), spaced)


def test_loop_promotes_from_the_kept_records(options):
    add_records(options, 0, GAMES)
    steps = FakeSteps(options, [True])
    assert run_loop(options, steps) == 1
    assert steps.steps == [TrainStep(1), MatchStep(1, 1)]


def test_rejected_candidate_is_retrained_on_another_batch(options):
    steps = FakeSteps(options, [False, True])
    run_loop(options, steps)
    assert steps.steps == [
        SelfPlayStep(0, 1),
        TrainStep(1),
        MatchStep(1, 1),
        SelfPlayStep(0, 2),
        TrainStep(1),
        MatchStep(1, 2),
    ]
    assert len(list(record_dir(options.records, SIZE, 0).glob("*.json"))) == 2 * GAMES


def test_loop_goes_on_to_the_next_generation(options):
    add_records(options, 0, GAMES)
    steps = FakeSteps(options, [True, True])
    two = LoopOptions(**{**vars(options), "promotions": 2})
    assert run_loop(two, steps) == 2
    assert steps.steps == [
        TrainStep(1),
        MatchStep(1, 1),
        SelfPlayStep(1, 1),
        TrainStep(2),
        MatchStep(2, 1),
    ]


def test_rejections_in_a_row_stop_the_loop_with_the_last_match(options):
    add_records(options, 0, GAMES)
    steps = FakeSteps(options, [False, False])
    twice = LoopOptions(**{**vars(options), "max_rejections": 2})
    with pytest.raises(LoopError) as error:
        run_loop(twice, steps)
    assert "candidate 1 was rejected 2 times in a row" in str(error.value)
    assert '"attempt": 2' in str(error.value)
    assert steps.steps[-1] == MatchStep(1, 2)


def test_failing_step_stops_the_loop(options):
    add_records(options, 0, GAMES)
    ran: list[Step] = []

    def failing(command: Command) -> int:
        ran.append(command.step)
        return 2

    with pytest.raises(LoopError, match="training of candidate 1 failed with exit code 2"):
        run_loop(options, failing)
    assert ran == [TrainStep(1)]


def test_step_that_changes_nothing_stops_the_loop(options):
    with pytest.raises(LoopError, match="without changing the store"):
        run_loop(options, lambda command: 0)


def test_match_line_of_an_earlier_attempt_is_not_this_match(options):
    add_records(options, 0, GAMES)
    reject(options, 1, 1)
    add_records(options, 0, GAMES)
    log = match_log_path(options.models, SIZE)
    log.write_text(json.dumps({"generation": 1, "attempt": 1, "promoted": False}) + "\n")
    make_model(candidate_dir(options.models, SIZE, 1))

    def unlogged(command: Command) -> int:
        os.rename(candidate_dir(options.models, SIZE, 1), rejected_dir(options.models, SIZE, 1, 2))
        return 0

    with pytest.raises(LoopError, match="match of candidate 1, attempt 2 left no line"):
        run_loop(options, unlogged)


def test_generation_directory_without_a_model_counts_as_promoted(options):
    # as for the JVM's Match: a partial 5/1/ is not matched again
    add_records(options, 0, GAMES)
    generation_dir(options.models, SIZE, 1).mkdir()
    assert next_step(options) == SelfPlayStep(1, 1)


def test_missing_sbt_stops_the_loop_naming_the_step(options):
    add_records(options, 0, GAMES)
    make_model(candidate_dir(options.models, SIZE, 1))
    missing = LoopOptions(**{**vars(options), "sbt": str(options.repository / "no-sbt")})
    with pytest.raises(LoopError, match="match of candidate 1, attempt 1 could not start"):
        run_loop(missing)


def test_sbt_command_may_have_arguments(options):
    client = LoopOptions(**{**vars(options), "sbt": "sbt --client"})
    run = command(MatchStep(1, 1), client)
    assert run.args[:2] == ["sbt", "--client"]
    assert run.args[2].startswith("engine/runMain go3d.engine.Match ")


def test_match_that_logs_nothing_stops_the_loop(options):
    add_records(options, 0, GAMES)
    make_model(candidate_dir(options.models, SIZE, 1))

    def unlogged(command: Command) -> int:
        os.rename(candidate_dir(options.models, SIZE, 1), generation_dir(options.models, SIZE, 1))
        return 0

    with pytest.raises(LoopError, match="left no line"):
        run_loop(options, unlogged)


UNINTERRUPTED = [
    SelfPlayStep(0, 1),
    TrainStep(1),
    MatchStep(1, 1),
    SelfPlayStep(0, 2),
    TrainStep(1),
    MatchStep(1, 2),
]


@pytest.mark.parametrize("interrupted", range(len(UNINTERRUPTED)))
def test_rerun_after_an_interruption_repeats_and_skips_no_step(options, interrupted):
    first = FakeSteps(options, [False, True], interrupt_at=interrupted)
    with pytest.raises(KeyboardInterrupt):
        run_loop(options, first)
    second = FakeSteps(options, first.outcomes)
    run_loop(options, second)
    assert second.steps[0] == UNINTERRUPTED[interrupted]
    assert first.steps + second.steps == UNINTERRUPTED


def test_rerun_after_training_starts_with_the_match(options):
    add_records(options, 0, GAMES)
    make_model(candidate_dir(options.models, SIZE, 1))
    steps = FakeSteps(options, [True])
    run_loop(options, steps)
    assert steps.steps == [MatchStep(1, 1)]


def args(options: LoopOptions, *extra: str) -> list[str]:
    stores = ["--models", str(options.models), "--records", str(options.records)]
    return ["--size", "5", "--promotions", "1", *stores, "--selfplay-games", str(GAMES), *extra]


def test_main_reports_the_latest_generation(options, capsys):
    add_records(options, 0, GAMES)
    main(args(options, "--match-games", "4", "--threshold", "2"), FakeSteps(options, [True]))
    out = capsys.readouterr().out
    assert "training of candidate 1 took " in out
    assert "promotion-loop: 1 promoted, generation 1 is the latest" in out


def test_main_exits_with_1_on_a_loop_error(options, capsys):
    add_records(options, 0, GAMES)
    with pytest.raises(SystemExit) as exit_info:
        main(args(options, "--match-games", "4", "--threshold", "2"), lambda command: 3)
    assert exit_info.value.code == 1
    assert "failed with exit code 3" in capsys.readouterr().err


@pytest.mark.parametrize(
    "extra",
    [["--match-games", "5", "--threshold", "2"], ["--match-games", "4", "--threshold", "4"]],
)
def test_main_refuses_a_match_that_cannot_be_played(options, extra):
    with pytest.raises(SystemExit) as exit_info:
        main(args(options, *extra), lambda command: 0)
    assert exit_info.value.code == 2


def test_training_module_runs_as_the_loop_starts_it():
    result = subprocess.run(
        [sys.executable, "-m", "go3d_training.train", "--help"], capture_output=True, text=True
    )
    assert result.returncode == 0
    assert "train-generation" in result.stdout

"""The layout of the model store and of the self-play records (docs/model-contract.md).

A promoted generation `g` of size `s` lives in `<store>/<s>/<g>/`, a candidate for generation
`g` in `<store>/<s>/candidates/<g>/` and, once a match rejected it, in
`<store>/<s>/rejected/<g>-<attempt>/`. The match log is `<store>/<s>/matches.jsonl`, and the
records of generation `g` are in `<records>/<s>/<g>/`. The JVM's `OnnxModel` and `Match` use the
same layout.
"""

import re
from pathlib import Path

MODEL_FILE_NAME = "model.onnx"
CHECKPOINT_FILE_NAME = "model.pt"
METRICS_FILE_NAME = "training.json"
CANDIDATES_DIR_NAME = "candidates"
REJECTED_DIR_NAME = "rejected"
MATCH_LOG_FILE_NAME = "matches.jsonl"


def generation_dir(store: Path, size: int, generation: int) -> Path:
    """The directory of the promoted generation `generation`."""
    return store / str(size) / str(generation)


def candidate_dir(store: Path, size: int, generation: int) -> Path:
    """The directory of the candidate for generation `generation`, until it is promoted."""
    return store / str(size) / CANDIDATES_DIR_NAME / str(generation)


def rejected_dir(store: Path, size: int, generation: int, attempt: int) -> Path:
    """The directory that attempt `attempt` of the candidate for `generation` was set aside to."""
    return store / str(size) / REJECTED_DIR_NAME / f"{generation}-{attempt}"


def rejected_attempts(store: Path, size: int, generation: int) -> list[int]:
    """The attempts of the candidate for `generation` that a match rejected, in order."""
    directory = store / str(size) / REJECTED_DIR_NAME
    if not directory.is_dir():
        return []
    pattern = re.compile(f"{generation}-([0-9]+)")
    matches = (pattern.fullmatch(path.name) for path in directory.iterdir())
    return sorted(int(match.group(1)) for match in matches if match)


def promoted_generations(store: Path, size: int) -> list[int]:
    """The promoted generations of `size`, in order: every `<g>/` directory, with or without a
    model, as the JVM's `Match` counts them."""
    directory = store / str(size)
    if not directory.is_dir():
        return []
    return sorted(
        int(path.name)
        for path in directory.iterdir()
        if re.fullmatch("[0-9]+", path.name) and path.is_dir()
    )


def match_log_path(store: Path, size: int) -> Path:
    """The match log of `size`: one JSON object per line, one line per match."""
    return store / str(size) / MATCH_LOG_FILE_NAME


def model_path(store: Path, size: int, generation: int) -> Path:
    return generation_dir(store, size, generation) / MODEL_FILE_NAME


def checkpoint_path(store: Path, size: int, generation: int) -> Path:
    return generation_dir(store, size, generation) / CHECKPOINT_FILE_NAME


def record_dir(records: Path, size: int, generation: int) -> Path:
    """The directory of the self-play records that generation `generation` played."""
    return records / str(size) / str(generation)

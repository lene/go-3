"""The layout of the model store and of the self-play records (docs/model-contract.md).

A promoted generation `g` of size `s` lives in `<store>/<s>/<g>/`, a candidate for generation
`g` in `<store>/<s>/candidates/<g>/`, and the records of generation `g` in
`<records>/<s>/<g>/`.
"""

from pathlib import Path

MODEL_FILE_NAME = "model.onnx"
CHECKPOINT_FILE_NAME = "model.pt"
METRICS_FILE_NAME = "training.json"
CANDIDATES_DIR_NAME = "candidates"


def generation_dir(store: Path, size: int, generation: int) -> Path:
    """The directory of the promoted generation `generation`."""
    return store / str(size) / str(generation)


def candidate_dir(store: Path, size: int, generation: int) -> Path:
    """The directory of the candidate for generation `generation`, until it is promoted."""
    return store / str(size) / CANDIDATES_DIR_NAME / str(generation)


def model_path(store: Path, size: int, generation: int) -> Path:
    return generation_dir(store, size, generation) / MODEL_FILE_NAME


def checkpoint_path(store: Path, size: int, generation: int) -> Path:
    return generation_dir(store, size, generation) / CHECKPOINT_FILE_NAME


def record_dir(records: Path, size: int, generation: int) -> Path:
    """The directory of the self-play records that generation `generation` played."""
    return records / str(size) / str(generation)

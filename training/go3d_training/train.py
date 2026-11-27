"""Train a candidate for the next generation from the predecessor's weights and its self-play.

Run from `training/`, `train-generation --size 5 --generation 1` trains from the checkpoint of
the promoted generation 0 (`../models/5/0/model.pt`) on the records of generations
`1 - window` to 0 (`../selfplay/5/<generation>/`), and writes the candidate to
`../models/5/candidates/1/`: `model.onnx` with the refitted komi, `model.pt` and
`training.json`. Promotion is not its business: a candidate becomes generation 1 only once it
beats generation 0 in the JVM's `Match`, which `promotion-loop` (`loop.py`) runs.
"""

import argparse
import json
import os
import shutil
import sys
import time
import uuid
from collections.abc import Sequence
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as functional

from go3d_training.checkpoint import load_checkpoint
from go3d_training.export import (
    DEFAULT_SEED,
    ERROR_EXIT_CODE,
    KOMI_KEY,
    model_metadata,
    write_model,
)
from go3d_training.network import PolicyValueNetwork
from go3d_training.records import (
    Record,
    RecordError,
    Samples,
    all_samples,
    load_records,
    refit_komi,
)
from go3d_training.store import (
    CHECKPOINT_FILE_NAME,
    METRICS_FILE_NAME,
    candidate_dir,
    checkpoint_path,
    generation_dir,
    model_path,
    record_dir,
)
from go3d_training.symmetry import SYMMETRIES, augment

TRAINING_SIZES = (5,)
DEFAULT_MODELS = Path("../models")
DEFAULT_RECORDS = Path("../selfplay")
DEFAULT_WINDOW = 2
DEFAULT_EPOCHS = 10
DEFAULT_BATCH_SIZE = 256
DEFAULT_LEARNING_RATE = 1e-3
WEIGHT_DECAY = 1e-4
CUDA = "cuda"
CPU = "cpu"
PROGRAM = "train-generation"


class TrainingError(Exception):
    """A training run that cannot start; it has written nothing."""


@dataclass(frozen=True)
class TrainingOptions:
    size: int
    generation: int
    models: Path = DEFAULT_MODELS
    records: Path = DEFAULT_RECORDS
    window: int = DEFAULT_WINDOW
    epochs: int = DEFAULT_EPOCHS
    batch_size: int = DEFAULT_BATCH_SIZE
    learning_rate: float = DEFAULT_LEARNING_RATE
    seed: int = DEFAULT_SEED
    device: str = CUDA

    @property
    def parent(self) -> int:
        return self.generation - 1

    @property
    def record_generations(self) -> list[int]:
        """The generations whose records train the candidate: `generation - window` to the
        parent."""
        return list(range(max(0, self.generation - self.window), self.generation))


@dataclass(frozen=True)
class EpochLosses:
    """The mean losses over the samples of one epoch."""

    epoch: int
    policy: float
    value: float
    total: float


@dataclass(frozen=True)
class TrainingData:
    """The samples of a training run at `komi`, the komi refitted from the parent's records,
    and the number of records they come from."""

    komi: float
    records: int
    samples: Samples


@dataclass(frozen=True)
class TrainingMetrics:
    """The content of `training.json`, in the order of its keys."""

    size: int
    generation: int
    parent: int
    old_komi: float
    new_komi: float
    record_generations: list[int]
    records: int
    samples: int
    epochs: int
    batch_size: int
    learning_rate: float
    weight_decay: float
    seed: int
    device: str
    losses: list[EpochLosses]
    duration_seconds: float


def resolve_device(name: str) -> torch.device:
    """`name`, or the CPU with a warning if CUDA is asked for and not available."""
    if name == CUDA and not torch.cuda.is_available():
        print(f"{PROGRAM}: CUDA is not available, training on the CPU", file=sys.stderr)
        return torch.device(CPU)
    return torch.device(name)


def losses(
    network: PolicyValueNetwork, planes: torch.Tensor, policies: torch.Tensor, values: torch.Tensor
) -> tuple[torch.Tensor, torch.Tensor]:
    """The policy cross-entropy against the visit distribution and the value mean squared
    error, each the mean over the batch."""
    logits, value = network(planes)
    policy_loss = -(policies * functional.log_softmax(logits, dim=1)).sum(dim=1).mean()
    value_loss = functional.mse_loss(value.squeeze(1), values)
    return policy_loss, value_loss


def fit(
    network: PolicyValueNetwork,
    samples: Samples,
    options: TrainingOptions,
    device: torch.device,
) -> list[EpochLosses]:
    """Train `network` on `samples` in place, each sample under a random symmetry in each
    epoch; the same seed and samples give the same weights on the CPU. Return the losses of
    each epoch; `network` is left on the CPU in eval mode."""
    rng = np.random.default_rng(options.seed)
    network.to(device).train()
    optimizer = torch.optim.AdamW(
        network.parameters(), lr=options.learning_rate, weight_decay=WEIGHT_DECAY
    )
    history = [
        _epoch(network, optimizer, samples, options.batch_size, rng, device, epoch)
        for epoch in range(1, options.epochs + 1)
    ]
    network.to(CPU).eval()
    return history


def _epoch(
    network: PolicyValueNetwork,
    optimizer: torch.optim.Optimizer,
    samples: Samples,
    batch_size: int,
    rng: np.random.Generator,
    device: torch.device,
    epoch: int,
) -> EpochLosses:
    count = len(samples)
    order = rng.permutation(count)
    symmetries = rng.integers(len(SYMMETRIES), size=count)
    policy_sum = value_sum = 0.0
    for start in range(0, count, batch_size):
        batch = order[start : start + batch_size]
        planes, policies = augment(
            samples.planes[batch], samples.policies[batch], symmetries[batch]
        )
        policy_loss, value_loss = losses(
            network,
            torch.from_numpy(planes).to(device),
            torch.from_numpy(policies).to(device),
            torch.from_numpy(samples.values[batch]).to(device),
        )
        optimizer.zero_grad()
        (policy_loss + value_loss).backward()
        optimizer.step()
        policy_sum += policy_loss.item() * len(batch)
        value_sum += value_loss.item() * len(batch)
    policy, value = policy_sum / count, value_sum / count
    result = EpochLosses(epoch, policy, value, policy + value)
    print(f"epoch {epoch}: policy loss {policy:.4f}, value loss {value:.4f}")
    return result


def _check_free(options: TrainingOptions) -> None:
    taken = [
        candidate_dir(options.models, options.size, options.generation),
        generation_dir(options.models, options.size, options.generation),
    ]
    for directory in taken:
        if directory.exists():
            raise TrainingError(f"{directory} exists and is not overwritten")


def _parent(options: TrainingOptions) -> tuple[PolicyValueNetwork, float]:
    """The network and the komi of the parent generation."""
    checkpoint = checkpoint_path(options.models, options.size, options.parent)
    if not checkpoint.is_file():
        hint = (
            ": re-export generation 0 with export-generation and the same --seed, --blocks and "
            f"--channels to another --out and copy its {CHECKPOINT_FILE_NAME} here"
            if options.parent == 0
            else ""
        )
        raise TrainingError(f"{checkpoint} is missing{hint}")
    model = model_path(options.models, options.size, options.parent)
    if not model.is_file():
        raise TrainingError(f"{model} is missing")
    network = load_checkpoint(checkpoint)
    if network.config.size != options.size:
        raise TrainingError(f"{checkpoint} is of board size {network.config.size}")
    metadata = model_metadata(model)
    _check_same_network(network, options.parent, metadata, f"{checkpoint} and {model}")
    return network, float(metadata[KOMI_KEY])


def _check_same_network(
    network: PolicyValueNetwork, generation: int, metadata: dict[str, str], names: str
) -> None:
    """Refuse a checkpoint that is not the network of the ONNX model with `metadata`."""
    config = network.config
    expected = {
        "board_size": config.size,
        "blocks": config.blocks,
        "channels": config.channels,
        "generation": generation,
    }
    for key, value in expected.items():
        found = metadata.get(key)
        if found != str(value):
            message = f"{key} {value} in the checkpoint, {found} in the model"
            raise TrainingError(f"{names} are not the same network: {message}")


def _records(options: TrainingOptions) -> list[Record]:
    """The records of the generations of `options`; those of the parent must be among them."""
    records = load_records(options.records, options.size, options.record_generations)
    if not records:
        dirs = [record_dir(options.records, options.size, g) for g in options.record_generations]
        raise TrainingError(f"no records in {', '.join(map(str, dirs))}")
    if not any(record.generation == options.parent for record in records):
        parent_records = record_dir(options.records, options.size, options.parent)
        raise TrainingError(f"no records in {parent_records} to refit the komi from")
    return records


def _training_data(options: TrainingOptions) -> TrainingData:
    """The samples of the records of `options` at the komi refitted from the parent's records.
    The records, which hold their planes too, are dropped once their samples are taken."""
    records = _records(options)
    margins = [record.margin for record in records if record.generation == options.parent]
    komi = refit_komi(margins, options.size)
    return TrainingData(komi, len(records), all_samples(records, komi))


def train_generation(options: TrainingOptions) -> Path:
    """Train the candidate of `options` and write it; return its directory. A TrainingError
    or a RecordError leaves the model store as it was."""
    started = time.monotonic()
    _check_free(options)
    network, old_komi = _parent(options)
    data = _training_data(options)
    device = resolve_device(options.device)
    history = fit(network, data.samples, options, device)
    metrics = TrainingMetrics(
        size=options.size,
        generation=options.generation,
        parent=options.parent,
        old_komi=old_komi,
        new_komi=data.komi,
        record_generations=options.record_generations,
        records=data.records,
        samples=len(data.samples),
        epochs=options.epochs,
        batch_size=options.batch_size,
        learning_rate=options.learning_rate,
        weight_decay=WEIGHT_DECAY,
        seed=options.seed,
        device=device.type,
        losses=history,
        duration_seconds=time.monotonic() - started,
    )
    return _write_candidate(options, network, data.komi, metrics)


def _write_candidate(
    options: TrainingOptions, network: PolicyValueNetwork, komi: float, metrics: TrainingMetrics
) -> Path:
    """Write the candidate to a hidden directory next to its own, then rename it, so that a
    failure leaves no partial candidate."""
    target = candidate_dir(options.models, options.size, options.generation)
    target.parent.mkdir(parents=True, exist_ok=True)
    # mkdir, unlike mkdtemp (always 0700), gives the directory the mode the umask allows
    staging = target.parent / f".{options.generation}-{uuid.uuid4().hex}"
    staging.mkdir()
    try:
        write_model(staging, network, options.generation, komi)
        (staging / METRICS_FILE_NAME).write_text(json.dumps(asdict(metrics), indent=2) + "\n")
        os.rename(staging, target)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return target


def _positive_int(text: str) -> int:
    value = int(text)
    if value < 1:
        raise argparse.ArgumentTypeError(f"{value} is not 1 or more")
    return value


def _positive_float(text: str) -> float:
    value = float(text)
    if not value > 0:
        raise argparse.ArgumentTypeError(f"{value} is not above 0")
    return value


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog=PROGRAM,
        description="Train a candidate for <generation> from the promoted generation before it "
        "and write it to <models>/<size>/candidates/<generation>/.",
    )
    sizes = ", ".join(map(str, TRAINING_SIZES))
    parser.add_argument("--size", type=int, required=True, choices=TRAINING_SIZES, help=sizes)
    parser.add_argument("--generation", type=_positive_int, required=True, help="1 or more")
    parser.add_argument("--models", type=Path, default=DEFAULT_MODELS, help="the model store")
    parser.add_argument(
        "--records", type=Path, default=DEFAULT_RECORDS, help="the self-play records"
    )
    parser.add_argument(
        "--window", type=_positive_int, default=DEFAULT_WINDOW, help="generations of records"
    )
    parser.add_argument("--epochs", type=_positive_int, default=DEFAULT_EPOCHS)
    parser.add_argument("--batch-size", type=_positive_int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--learning-rate", type=_positive_float, default=DEFAULT_LEARNING_RATE)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument(
        "--device", choices=(CUDA, CPU), default=CUDA, help="cuda falls back to the CPU"
    )
    return parser


def main(argv: Sequence[str] | None = None) -> None:
    parser = _parser()
    args = parser.parse_args(argv)
    # every option of the parser is a field of TrainingOptions under the same name
    options = TrainingOptions(**vars(args))
    try:
        target = train_generation(options)
    except (TrainingError, RecordError) as error:
        parser.exit(ERROR_EXIT_CODE, f"{PROGRAM}: error: {error}\n")
    print(f"wrote {target}")


if __name__ == "__main__":
    main()

"""Export a model generation to ONNX with its contract in the model's metadata.

Run from `training/`, `export-generation --size 5 --generation 0 --out ../models` writes
`models/5/0/model.onnx` and its checkpoint `model.pt` at the repository root, the
`<size>/<generation>/` layout of the store.
"""

import argparse
from collections.abc import Sequence
from pathlib import Path

import onnx
import torch
from torch.export import Dim

from go3d_training.checkpoint import save_checkpoint
from go3d_training.encoding import (
    CONTRACT_VERSION,
    INPUT_PLANES,
    SCORING_RULE,
    action_count,
)
from go3d_training.network import (
    DEFAULT_BLOCKS,
    DEFAULT_CHANNELS,
    NetworkConfig,
    PolicyValueNetwork,
    build_network,
)
from go3d_training.store import (
    CHECKPOINT_FILE_NAME,
    MODEL_FILE_NAME,
    generation_dir,
)

INPUT_NAME = "board"
POLICY_OUTPUT = "policy"
VALUE_OUTPUT = "value"
KOMI_KEY = "komi"
INITIAL_KOMI = 4.5
DEFAULT_SEED = 0
UNTRAINED_GENERATION = 0
EXAMPLE_BATCH = 2


def contract_metadata(network: PolicyValueNetwork, generation: int, komi: float) -> dict[str, str]:
    config = network.config
    return {
        "contract_version": str(CONTRACT_VERSION),
        "board_size": str(config.size),
        "generation": str(generation),
        "scoring_rule": SCORING_RULE,
        KOMI_KEY: str(komi),
        "input_planes": str(INPUT_PLANES),
        "actions": str(action_count(config.size)),
        "blocks": str(config.blocks),
        "channels": str(config.channels),
    }


def to_onnx(network: PolicyValueNetwork, generation: int, komi: float) -> onnx.ModelProto:
    """The ONNX model of `network` (on the CPU) with a dynamic batch axis and the contract
    metadata."""
    network.eval()
    size = network.config.size
    example = torch.zeros(EXAMPLE_BATCH, INPUT_PLANES, size, size, size)
    program = torch.onnx.export(
        network,
        (example,),
        input_names=[INPUT_NAME],
        output_names=[POLICY_OUTPUT, VALUE_OUTPUT],
        dynamic_shapes={INPUT_NAME: {0: Dim("batch")}},
        dynamo=True,
        external_data=False,
        verbose=False,
    )
    if program is None:
        raise RuntimeError("torch.onnx.export returned no program")
    model = program.model_proto
    onnx.helper.set_model_props(model, contract_metadata(network, generation, komi))
    onnx.checker.check_model(model)
    return model


def write_model(directory: Path, network: PolicyValueNetwork, generation: int, komi: float) -> Path:
    """Write `network` as generation `generation` at `komi` to `directory`: the ONNX model and
    the checkpoint. Return the path of the ONNX model."""
    model = to_onnx(network, generation, komi)
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / MODEL_FILE_NAME
    onnx.save_model(model, path)
    save_checkpoint(network, directory / CHECKPOINT_FILE_NAME)
    return path


def model_metadata(path: Path) -> dict[str, str]:
    """The metadata of the ONNX model at `path`."""
    return {prop.key: prop.value for prop in onnx.load(path).metadata_props}


def model_komi(path: Path) -> float:
    """The komi in the metadata of the ONNX model at `path`."""
    return float(model_metadata(path)[KOMI_KEY])


def export_generation(
    out: Path,
    config: NetworkConfig,
    generation: int,
    seed: int,
) -> Path:
    """Write generation `generation` of `config`, its weights drawn from `seed`, at the initial
    komi; return the path of its ONNX model."""
    directory = generation_dir(out, config.size, generation)
    return write_model(directory, build_network(config, seed), generation, INITIAL_KOMI)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="export-generation",
        description="Export an untrained model generation to <out>/<size>/<generation>/.",
    )
    parser.add_argument("--size", type=int, required=True, help="odd board size, 3 to 25")
    parser.add_argument(
        "--generation", type=int, required=True, help="only 0: train-generation makes the rest"
    )
    parser.add_argument("--out", type=Path, required=True, help="the model store directory")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--blocks", type=int, default=DEFAULT_BLOCKS)
    parser.add_argument("--channels", type=int, default=DEFAULT_CHANNELS)
    return parser


def main(argv: Sequence[str] | None = None) -> None:
    parser = _parser()
    args = parser.parse_args(argv)
    if args.generation != UNTRAINED_GENERATION:
        parser.error(
            f"generation {args.generation}: only generation 0 is exported, "
            "train-generation trains the others"
        )
    try:
        config = NetworkConfig(args.size, args.blocks, args.channels)
    except ValueError as error:
        parser.error(str(error))
    path = export_generation(args.out, config, args.generation, args.seed)
    print(f"wrote {path} and {path.with_name(CHECKPOINT_FILE_NAME)}")

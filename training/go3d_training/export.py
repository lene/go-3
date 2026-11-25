"""Export a model generation to ONNX with its contract in the model's metadata.

Run from `training/`, `export-generation --size 5 --generation 0 --out ../models` writes
`models/5/0/model.onnx` at the repository root, the `<size>/<generation>/` layout of the store.
"""

import argparse
from collections.abc import Sequence
from pathlib import Path

import onnx
import torch
from torch.export import Dim

from go3d_training.encoding import (
    CONTRACT_VERSION,
    INPUT_PLANES,
    action_count,
)
from go3d_training.network import (
    DEFAULT_BLOCKS,
    DEFAULT_CHANNELS,
    NetworkConfig,
    PolicyValueNetwork,
    build_network,
)

INPUT_NAME = "board"
POLICY_OUTPUT = "policy"
VALUE_OUTPUT = "value"
MODEL_FILE_NAME = "model.onnx"
SCORING_RULE = "area"
INITIAL_KOMI = 4.5
DEFAULT_SEED = 0
UNTRAINED_GENERATION = 0
EXAMPLE_BATCH = 2


def model_path(out: Path, size: int, generation: int) -> Path:
    return out / str(size) / str(generation) / MODEL_FILE_NAME


def contract_metadata(network: PolicyValueNetwork, generation: int) -> dict[str, str]:
    config = network.config
    return {
        "contract_version": str(CONTRACT_VERSION),
        "board_size": str(config.size),
        "generation": str(generation),
        "scoring_rule": SCORING_RULE,
        "komi": str(INITIAL_KOMI),
        "input_planes": str(INPUT_PLANES),
        "actions": str(action_count(config.size)),
        "blocks": str(config.blocks),
        "channels": str(config.channels),
    }


def to_onnx(network: PolicyValueNetwork, generation: int) -> onnx.ModelProto:
    """The ONNX model of `network` with a dynamic batch axis and the contract metadata."""
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
    onnx.helper.set_model_props(model, contract_metadata(network, generation))
    onnx.checker.check_model(model)
    return model


def export_generation(
    out: Path,
    config: NetworkConfig,
    generation: int,
    seed: int,
) -> Path:
    """Write generation `generation` of `config`, its weights drawn from `seed`; return its path."""
    model = to_onnx(build_network(config, seed), generation)
    path = model_path(out, config.size, generation)
    path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save_model(model, path)
    return path


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="export-generation",
        description="Export an untrained model generation to <out>/<size>/<generation>/.",
    )
    parser.add_argument("--size", type=int, required=True, help="odd board size, 3 to 25")
    parser.add_argument("--generation", type=int, required=True, help="only 0 until training")
    parser.add_argument("--out", type=Path, required=True, help="the model store directory")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--blocks", type=int, default=DEFAULT_BLOCKS)
    parser.add_argument("--channels", type=int, default=DEFAULT_CHANNELS)
    return parser


def main(argv: Sequence[str] | None = None) -> None:
    parser = _parser()
    args = parser.parse_args(argv)
    if args.generation != UNTRAINED_GENERATION:
        parser.error(f"generation {args.generation}: only generation 0 exists before training")
    try:
        config = NetworkConfig(args.size, args.blocks, args.channels)
    except ValueError as error:
        parser.error(str(error))
    path = export_generation(args.out, config, args.generation, args.seed)
    print(f"wrote {path}")

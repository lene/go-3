from pathlib import Path

import numpy as np
import onnx
import onnxruntime
import pytest
import torch

from go3d_training.checkpoint import load_checkpoint
from go3d_training.encoding import encode
from go3d_training.export import export_generation, main, model_komi, write_model
from go3d_training.network import NetworkConfig, build_network
from go3d_training.store import checkpoint_path, model_path

SIZE = 5
SEED = 0
EXPECTED_METADATA = {
    "contract_version": "1",
    "board_size": "5",
    "generation": "0",
    "scoring_rule": "area",
    "komi": "4.5",
    "input_planes": "5",
    "actions": "126",
    "blocks": "6",
    "channels": "64",
}


def positions() -> np.ndarray:
    return np.stack(
        [
            encode(SIZE, [], [], 0, False),
            encode(SIZE, [(1, 1, 1)], [(3, 3, 3)], 2, False),
            encode(SIZE, [(2, 2, 2)], [(4, 4, 4)], 3, True),
        ]
    )


def run_onnx(path: Path, boards: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    session = onnxruntime.InferenceSession(path, providers=["CPUExecutionProvider"])
    policy, value = session.run(["policy", "value"], {"board": boards})
    return policy, value


@pytest.fixture(scope="module")
def store(tmp_path_factory) -> Path:
    return tmp_path_factory.mktemp("models")


@pytest.fixture(scope="module")
def exported(store) -> Path:
    return export_generation(store, NetworkConfig(SIZE), generation=0, seed=SEED)


def test_layout(store, exported):
    assert exported == model_path(store, SIZE, 0)
    assert exported == store / "5" / "0" / "model.onnx"
    assert exported.is_file()


def test_checkpoint_next_to_the_model_holds_the_network(store, exported):
    checkpoint = checkpoint_path(store, SIZE, 0)
    assert checkpoint == exported.with_name("model.pt")
    network = load_checkpoint(checkpoint)
    expected = build_network(NetworkConfig(SIZE), seed=SEED)
    assert network.config == expected.config
    assert not network.training
    for name, tensor in expected.state_dict().items():
        assert torch.equal(network.state_dict()[name], tensor), name


def test_write_model_takes_the_generation_and_the_komi(tmp_path):
    network = build_network(NetworkConfig(3, blocks=1, channels=4), seed=SEED)
    path = write_model(tmp_path / "candidate", network, generation=3, komi=-2.5)
    metadata = {prop.key: prop.value for prop in onnx.load(path).metadata_props}
    assert metadata["generation"] == "3"
    assert metadata["komi"] == "-2.5"
    assert model_komi(path) == -2.5
    assert (tmp_path / "candidate" / "model.pt").is_file()


def test_round_trip_matches_pytorch(exported):
    boards = positions()
    policy, value = run_onnx(exported, boards)
    network = build_network(NetworkConfig(SIZE), seed=SEED)
    with torch.no_grad():
        expected_policy, expected_value = network(torch.from_numpy(boards))
    assert policy.shape == (3, 126)
    assert value.shape == (3, 1)
    assert ((value >= -1) & (value <= 1)).all()
    np.testing.assert_allclose(policy, expected_policy.numpy(), atol=1e-5, rtol=0)
    np.testing.assert_allclose(value, expected_value.numpy(), atol=1e-5, rtol=0)


def test_interface_names_and_dynamic_batch(exported):
    session = onnxruntime.InferenceSession(exported, providers=["CPUExecutionProvider"])
    [board] = session.get_inputs()
    assert board.name == "board"
    assert board.shape[1:] == [5, 5, 5, 5]
    assert isinstance(board.shape[0], str)
    assert [output.name for output in session.get_outputs()] == ["policy", "value"]
    single = positions()[:1]
    policy, value = run_onnx(exported, single)
    assert policy.shape == (1, 126)
    assert value.shape == (1, 1)


def test_metadata(exported):
    model = onnx.load(exported)
    metadata = {prop.key: prop.value for prop in model.metadata_props}
    assert metadata == EXPECTED_METADATA
    session = onnxruntime.InferenceSession(exported, providers=["CPUExecutionProvider"])
    assert session.get_modelmeta().custom_metadata_map == EXPECTED_METADATA


def test_same_seed_gives_the_same_outputs(exported, tmp_path):
    again = export_generation(tmp_path, NetworkConfig(SIZE), generation=0, seed=SEED)
    boards = positions()
    first = run_onnx(exported, boards)
    second = run_onnx(again, boards)
    np.testing.assert_array_equal(first[0], second[0])
    np.testing.assert_array_equal(first[1], second[1])


def test_cli_writes_the_model(tmp_path, capsys):
    argv = ["--size", "3", "--generation", "0", "--out", str(tmp_path), "--blocks", "1"]
    main(argv + ["--channels", "4"])
    path = tmp_path / "3" / "0" / "model.onnx"
    assert path.is_file()
    assert path.with_name("model.pt").is_file()
    assert str(path) in capsys.readouterr().out
    metadata = {prop.key: prop.value for prop in onnx.load(path).metadata_props}
    assert metadata["blocks"] == "1"
    assert metadata["channels"] == "4"
    assert metadata["actions"] == "28"


def test_cli_seed_draws_the_network(tmp_path):
    config = NetworkConfig(3, blocks=1, channels=4)
    argv = ["--size", "3", "--generation", "0", "--blocks", "1", "--channels", "4", "--seed", "1"]
    main(argv + ["--out", str(tmp_path / "cli")])
    from_cli = model_path(tmp_path / "cli", 3, 0)
    seed_1 = export_generation(tmp_path / "seed-1", config, generation=0, seed=1)
    seed_0 = export_generation(tmp_path / "seed-0", config, generation=0, seed=0)
    boards = np.stack([encode(3, [], [], 0, False), encode(3, [(2, 2, 2)], [], 1, False)])
    cli_policy, cli_value = run_onnx(from_cli, boards)
    policy_1, value_1 = run_onnx(seed_1, boards)
    policy_0, _ = run_onnx(seed_0, boards)
    np.testing.assert_array_equal(cli_policy, policy_1)
    np.testing.assert_array_equal(cli_value, value_1)
    assert not np.array_equal(cli_policy, policy_0)


@pytest.mark.parametrize(
    "args",
    [
        ["--size", "4", "--generation", "0"],
        ["--size", "2", "--generation", "0"],
        ["--size", "1", "--generation", "0"],
        ["--size", "27", "--generation", "0"],
        ["--size", "5", "--generation", "1"],
        ["--size", "5", "--generation", "0", "--blocks", "0"],
        ["--size", "5", "--generation", "0", "--channels", "0"],
    ],
)
def test_bad_arguments_write_nothing(tmp_path, capsys, args):
    with pytest.raises(SystemExit) as exit_info:
        main(args + ["--out", str(tmp_path / "models")])
    assert exit_info.value.code == 2
    assert "error" in capsys.readouterr().err
    assert not (tmp_path / "models").exists()

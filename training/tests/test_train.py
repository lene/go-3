import json
import os
import shutil
from pathlib import Path

import numpy as np
import onnx
import onnxruntime
import pytest
import torch

from go3d_training import train as train_module
from go3d_training.checkpoint import load_checkpoint
from go3d_training.encoding import encode
from go3d_training.export import export_generation, write_model
from go3d_training.network import NetworkConfig
from go3d_training.records import load_record, refit_komi
from go3d_training.store import candidate_dir, checkpoint_path, generation_dir, model_path
from go3d_training.symmetry import IDENTITY, SYMMETRIES, augment
from go3d_training.train import TrainingOptions, main, resolve_device

SIZE = 5
SMALL = NetworkConfig(SIZE, blocks=1, channels=4)
FAST = ["--epochs", "2", "--batch-size", "32", "--device", "cpu"]


@pytest.fixture
def store(tmp_path) -> Path:
    """A model store with a small generation 0 of size 5 and its checkpoint."""
    models = tmp_path / "models"
    export_generation(models, SMALL, generation=0, seed=0)
    return models


def promote(store: Path, generation: int) -> None:
    """Write generation 0's network as the promoted generation `generation`."""
    network = load_checkpoint(checkpoint_path(store, SIZE, 0))
    write_model(generation_dir(store, SIZE, generation), network, generation, komi=4.5)


def write_records(records: Path, source: Path, generation: int, margins: list[int]) -> None:
    """Copies of the record at `source` as records of `generation`, one per Black-minus-White
    margin, each with the winner its margin gives at the record's komi."""
    data = json.loads(source.read_text())
    directory = records / "5" / str(generation)
    directory.mkdir(parents=True)
    for number, margin in enumerate(margins):
        white = data["score"]["white"]
        winner = "black" if margin > data["komi"] else "white"
        changed = {**data, "generation": generation, "winner": winner}
        changed["score"] = {"black": white + margin, "white": white}
        (directory / f"{number:06d}.json").write_text(json.dumps(changed))


def train(store: Path, records: Path, *extra: str, generation: int = 1) -> None:
    args = ["--size", "5", "--generation", str(generation)]
    main(args + ["--models", str(store), "--records", str(records), *FAST, *extra])


def snapshot(directory: Path) -> dict[str, bytes]:
    return {
        str(path.relative_to(directory)): path.read_bytes()
        for path in sorted(directory.rglob("*"))
        if path.is_file()
    }


def refused(store: Path, records: Path, capsys, *extra: str, generation: int = 1) -> str:
    """The error of a run that must exit with 2 and leave the store as it was."""
    before = snapshot(store)
    with pytest.raises(SystemExit) as exit_info:
        train(store, records, *extra, generation=generation)
    assert exit_info.value.code == 2
    assert snapshot(store) == before
    assert not (store / "5" / "candidates").exists()
    return capsys.readouterr().err


def test_trains_a_candidate_from_the_fixture(store, fixture_records, fixture_record_path, capsys):
    train(store, fixture_records)
    candidate = candidate_dir(store, SIZE, 1)
    assert candidate == store / "5" / "candidates" / "1"
    files = sorted(path.name for path in candidate.iterdir())
    assert files == ["model.onnx", "model.pt", "training.json"]
    assert str(candidate) in capsys.readouterr().out
    assert not generation_dir(store, SIZE, 1).exists()

    record = load_record(fixture_record_path, SIZE, 0)
    komi = refit_komi([record.margin], SIZE)
    metadata = {p.key: p.value for p in onnx.load(candidate / "model.onnx").metadata_props}
    assert metadata["generation"] == "1"
    assert metadata["komi"] == str(komi)
    assert metadata["board_size"] == "5"
    assert metadata["blocks"] == "1"
    assert metadata["channels"] == "4"

    session = onnxruntime.InferenceSession(
        candidate / "model.onnx", providers=["CPUExecutionProvider"]
    )
    board = encode(SIZE, [(1, 1, 1)], [(3, 3, 3)], 2, False)[np.newaxis]
    policy, value = session.run(["policy", "value"], {"board": board})
    network = load_checkpoint(candidate / "model.pt")
    with torch.no_grad():
        expected_policy, expected_value = network(torch.from_numpy(board))
    np.testing.assert_allclose(policy, expected_policy.numpy(), atol=1e-5, rtol=0)
    np.testing.assert_allclose(value, expected_value.numpy(), atol=1e-5, rtol=0)


def test_metrics(store, fixture_records, fixture_record_path):
    train(store, fixture_records, "--learning-rate", "0.002", "--seed", "7")
    metrics = json.loads((candidate_dir(store, SIZE, 1) / "training.json").read_text())
    record = load_record(fixture_record_path, SIZE, 0)
    assert metrics["generation"] == 1
    assert metrics["parent"] == 0
    assert metrics["old_komi"] == 4.5
    assert metrics["new_komi"] == refit_komi([record.margin], SIZE)
    assert metrics["record_generations"] == [0]
    assert metrics["records"] == 1
    assert metrics["samples"] == len(record.moves)
    assert metrics["epochs"] == 2
    assert metrics["batch_size"] == 32
    assert metrics["learning_rate"] == 0.002
    assert metrics["seed"] == 7
    assert metrics["device"] == "cpu"
    assert [epoch["epoch"] for epoch in metrics["losses"]] == [1, 2]
    for epoch in metrics["losses"]:
        assert epoch["total"] == pytest.approx(epoch["policy"] + epoch["value"])
    assert metrics["duration_seconds"] > 0


def test_metrics_keys_and_types(store, fixture_records):
    """`training.json` keeps its keys in this order and its values of these types."""
    train(store, fixture_records)
    text = (candidate_dir(store, SIZE, 1) / "training.json").read_text()
    metrics = json.loads(text)
    expected_types = {
        "size": int,
        "generation": int,
        "parent": int,
        "old_komi": float,
        "new_komi": float,
        "record_generations": list,
        "records": int,
        "samples": int,
        "epochs": int,
        "batch_size": int,
        "learning_rate": float,
        "weight_decay": float,
        "seed": int,
        "device": str,
        "losses": list,
        "duration_seconds": float,
    }
    assert list(metrics) == list(expected_types)
    for key, kind in expected_types.items():
        assert type(metrics[key]) is kind, key
    assert metrics["size"] == SIZE
    assert metrics["weight_decay"] == 1e-4
    assert all(type(generation) is int for generation in metrics["record_generations"])
    for epoch in metrics["losses"]:
        assert list(epoch) == ["epoch", "policy", "value", "total"]
        assert [type(value) for value in epoch.values()] == [int, float, float, float]
    assert text.startswith('{\n  "size": 5,\n')
    assert text.endswith("\n}\n")


def test_losses_fall(store, fixture_records):
    train(store, fixture_records, "--epochs", "10", "--learning-rate", "0.01")
    losses = json.loads((candidate_dir(store, SIZE, 1) / "training.json").read_text())["losses"]
    assert losses[-1]["policy"] < losses[0]["policy"]
    assert losses[-1]["value"] < losses[0]["value"]


def test_same_seed_and_data_give_the_same_weights(tmp_path, fixture_records):
    def trained(name: str, seed: str) -> dict[str, torch.Tensor]:
        models = tmp_path / name
        export_generation(models, SMALL, generation=0, seed=0)
        train(models, fixture_records, "--seed", seed)
        return load_checkpoint(candidate_dir(models, SIZE, 1) / "model.pt").state_dict()

    first, second, other = trained("first", "3"), trained("second", "3"), trained("other", "4")
    assert all(torch.equal(first[name], second[name]) for name in first)
    assert not all(torch.equal(first[name], other[name]) for name in first)


def test_missing_parent_checkpoint(store, fixture_records, capsys):
    checkpoint = checkpoint_path(store, SIZE, 0)
    checkpoint.unlink()
    error = refused(store, fixture_records, capsys)
    assert str(checkpoint) in error
    assert "re-export" in error
    assert "the same --seed, --blocks and --channels" in error


def test_missing_parent_generation(store, fixture_records, capsys):
    error = refused(store, fixture_records, capsys, generation=3)
    assert str(checkpoint_path(store, SIZE, 2)) in error


def test_no_records(store, tmp_path, capsys):
    error = refused(store, tmp_path / "empty", capsys)
    assert "no records" in error


def test_no_records_of_the_parent(store, fixture_records, capsys):
    promote(store, 1)
    error = refused(store, fixture_records, capsys, generation=2)
    assert str(fixture_records / "5" / "1") in error
    assert "refit the komi" in error


def test_invalid_record(store, fixture_record_path, tmp_path, capsys):
    records = tmp_path / "records"
    target = records / "5" / "0" / fixture_record_path.name
    target.parent.mkdir(parents=True)
    data = json.loads(fixture_record_path.read_text())
    data["policies"].pop()
    target.write_text(json.dumps(data))
    shutil.copy(fixture_record_path, target.with_name("000000.json"))
    error = refused(store, records, capsys)
    assert str(target) in error


def test_existing_candidate_is_not_overwritten(store, fixture_records, capsys):
    candidate = candidate_dir(store, SIZE, 1)
    candidate.mkdir(parents=True)
    (candidate / "model.onnx").write_text("earlier")
    before = snapshot(store)
    with pytest.raises(SystemExit) as exit_info:
        train(store, fixture_records)
    assert exit_info.value.code == 2
    assert snapshot(store) == before
    assert str(candidate) in capsys.readouterr().err


def test_existing_promoted_generation_is_not_overwritten(store, fixture_records, capsys):
    promoted = generation_dir(store, SIZE, 1)
    shutil.copytree(generation_dir(store, SIZE, 0), promoted)
    error = refused(store, fixture_records, capsys)
    assert str(promoted) in error


@pytest.mark.parametrize(
    "generation, window, generations",
    [(1, 2, [0]), (2, 2, [0, 1]), (5, 2, [3, 4]), (5, 1, [4]), (2, 5, [0, 1])],
)
def test_record_generations(generation, window, generations):
    options = TrainingOptions(SIZE, generation, window=window)
    assert options.record_generations == generations
    assert options.parent == generation - 1


@pytest.mark.parametrize(
    "args",
    [
        ["--size", "7", "--generation", "1"],
        ["--size", "5", "--generation", "0"],
        ["--size", "5", "--generation", "1", "--window", "0"],
        ["--size", "5", "--generation", "1", "--epochs", "0"],
        ["--size", "5", "--generation", "1", "--batch-size", "0"],
        ["--size", "5", "--generation", "1", "--learning-rate", "0"],
        ["--size", "5", "--generation", "1", "--device", "tpu"],
    ],
)
def test_bad_arguments_write_nothing(store, fixture_records, capsys, args):
    before = snapshot(store)
    with pytest.raises(SystemExit) as exit_info:
        main(args + ["--models", str(store), "--records", str(fixture_records)])
    assert exit_info.value.code == 2
    assert "error" in capsys.readouterr().err
    assert snapshot(store) == before


def test_cuda_falls_back_to_the_cpu(monkeypatch, capsys):
    monkeypatch.setattr(torch.cuda, "is_available", lambda: False)
    assert resolve_device("cuda") == torch.device("cpu")
    assert "CPU" in capsys.readouterr().err
    assert resolve_device("cpu") == torch.device("cpu")


def test_window_trains_on_every_generation_and_refits_from_the_parent(
    store, fixture_record_path, tmp_path
):
    promote(store, 1)
    records = tmp_path / "records"
    write_records(records, fixture_record_path, 0, [-30, -40, -50])
    write_records(records, fixture_record_path, 1, [10, 20])
    train(store, records, generation=2)
    metrics = json.loads((candidate_dir(store, SIZE, 2) / "training.json").read_text())
    moves = len(load_record(fixture_record_path, SIZE, 0).moves)
    assert metrics["record_generations"] == [0, 1]
    assert metrics["records"] == 5
    assert metrics["samples"] == 5 * moves
    assert metrics["new_komi"] == refit_komi([10, 20], SIZE) == 15.5


def test_training_draws_a_seeded_symmetry_per_sample(
    store, fixture_records, fixture_record_path, monkeypatch
):
    received = []

    def recording(planes, policies, indices):
        received.append(indices.copy())
        return augment(planes, policies, indices)

    monkeypatch.setattr(train_module, "augment", recording)
    train(store, fixture_records, "--seed", "5")
    count = len(load_record(fixture_record_path, SIZE, 0).moves)
    rng = np.random.default_rng(5)
    expected = []
    for _ in range(2):  # the epochs of FAST
        order = rng.permutation(count)
        symmetries = rng.integers(len(SYMMETRIES), size=count)
        expected += [symmetries[order[start : start + 32]] for start in range(0, count, 32)]
    assert len(received) == len(expected)
    for got, want in zip(received, expected, strict=True):
        np.testing.assert_array_equal(got, want)
    identity = SYMMETRIES.index(IDENTITY)
    assert any((indices != identity).any() for indices in received)


def test_failed_write_leaves_no_candidate(store, fixture_records, monkeypatch):
    def failing(*args, **kwargs):
        raise OSError("disk full")

    monkeypatch.setattr(train_module, "write_model", failing)
    with pytest.raises(OSError, match="disk full"):
        train(store, fixture_records)
    assert list((store / "5" / "candidates").iterdir()) == []


def test_candidate_directory_follows_the_umask(store, fixture_records):
    umask = os.umask(0o022)
    try:
        train(store, fixture_records)
    finally:
        os.umask(umask)
    assert candidate_dir(store, SIZE, 1).stat().st_mode & 0o777 == 0o755


def test_missing_parent_model(store, fixture_records, capsys):
    model = model_path(store, SIZE, 0)
    model.unlink()
    assert str(model) in refused(store, fixture_records, capsys)


def test_parent_checkpoint_of_another_board_size(store, fixture_records, tmp_path, capsys):
    other = export_generation(tmp_path / "other", NetworkConfig(3, 1, 4), generation=0, seed=0)
    shutil.copy(other.with_name("model.pt"), checkpoint_path(store, SIZE, 0))
    assert "board size 3" in refused(store, fixture_records, capsys)


def test_parent_checkpoint_of_another_network(store, fixture_records, tmp_path, capsys):
    other = export_generation(tmp_path / "other", NetworkConfig(SIZE, 2, 4), generation=0, seed=0)
    shutil.copy(other, model_path(store, SIZE, 0))
    error = refused(store, fixture_records, capsys)
    assert "not the same network" in error
    assert "blocks 1 in the checkpoint, 2 in the model" in error


def test_parent_model_of_another_generation(store, fixture_records, capsys):
    shutil.copytree(generation_dir(store, SIZE, 0), generation_dir(store, SIZE, 1))
    error = refused(store, fixture_records, capsys, generation=2)
    assert "generation 1 in the checkpoint, 0 in the model" in error

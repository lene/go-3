import json
from pathlib import Path

import numpy as np
import pytest

from go3d_training.encoding import Color, encode
from go3d_training.records import (
    RecordError,
    all_samples,
    load_record,
    load_records,
    refit_komi,
    samples,
)

SIZE = 5


def raw(path: Path) -> dict:
    return json.loads(path.read_text())


def point(move: dict) -> tuple[int, int, int]:
    position = move["position"]
    return position["x"], position["y"], position["z"]


def replayed_planes(record: dict) -> list[np.ndarray]:
    """The planes before each move, replayed straight from the JSON of the record."""
    board: dict[tuple[int, int, int], str] = {}
    planes = []
    moves = record["game"]["moves"]
    for index, move in enumerate(moves):
        black = [p for p, color in board.items() if color == "@"]
        white = [p for p, color in board.items() if color == "O"]
        previous_pass = index > 0 and moves[index - 1].get("pass", False)
        planes.append(encode(SIZE, black, white, index, previous_pass))
        if not move.get("pass", False):
            board[point(move)] = move["color"]["color"]
        for stone in record["game"]["captures"].get(str(index), []):
            del board[point(stone)]
    return planes


def write_changed(source: Path, directory: Path, change) -> Path:
    record = raw(source)
    change(record)
    target = directory / "5" / "0" / source.name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(record))
    return target


def test_fixture_has_captures_and_passes(fixture_record_path):
    game = raw(fixture_record_path)["game"]
    assert game["captures"]
    assert any(move.get("pass", False) for move in game["moves"])


def test_one_sample_per_move_from_the_replayed_position(fixture_record_path):
    data = raw(fixture_record_path)
    record = load_record(fixture_record_path, SIZE, 0)
    result = samples(record, record.komi)
    moves = len(data["game"]["moves"])
    assert len(result) == moves
    assert result.planes.shape == (moves, 5, SIZE, SIZE, SIZE)
    assert result.policies.shape == (moves, 126)
    np.testing.assert_array_equal(result.planes, np.stack(replayed_planes(data)))
    np.testing.assert_allclose(result.policies, np.array(data["policies"]), atol=1e-7)


def test_value_alternates_with_the_player_to_move(fixture_record_path):
    record = load_record(fixture_record_path, SIZE, 0)
    values = samples(record, record.komi).values
    first = 1.0 if record.winner is Color.BLACK else -1.0
    expected = np.array([first * (-1) ** index for index in range(len(values))])
    np.testing.assert_array_equal(values, expected)


def alternating(first: float, count: int) -> np.ndarray:
    return np.array([first * (-1) ** index for index in range(count)], dtype=np.float32)


def test_value_is_the_result_at_the_komi_of_training(fixture_record_path):
    record = load_record(fixture_record_path, SIZE, 0)
    count = len(record.moves)
    # just below the margin Black wins, just above White does, whatever the record's winner
    black_wins = samples(record, record.margin - 0.5).values
    white_wins = samples(record, record.margin + 0.5).values
    np.testing.assert_array_equal(black_wins, alternating(1.0, count))
    np.testing.assert_array_equal(white_wins, alternating(-1.0, count))
    flipped = record.margin + 0.5 if record.winner is Color.BLACK else record.margin - 0.5
    own = samples(record, record.komi).values
    np.testing.assert_array_equal(samples(record, flipped).values, -own)
    assert record.winner_at(record.komi) is record.winner


def test_record_fields(fixture_record_path):
    data = raw(fixture_record_path)
    record = load_record(fixture_record_path, SIZE, 0)
    assert record.komi == data["komi"]
    assert record.margin == data["score"]["black"] - data["score"]["white"]
    assert record.winner is (Color.BLACK if data["winner"] == "black" else Color.WHITE)


def test_load_records_reads_the_generations_in_name_order(fixture_records, tmp_path):
    [record] = load_records(fixture_records, SIZE, [0])
    assert record.generation == 0
    assert load_records(fixture_records, SIZE, [1]) == []
    second = write_changed(record.path, tmp_path, lambda r: None)
    first = second.with_name("000000.json")
    first.write_text(second.read_text())
    second.with_name("notes.txt").write_text("not a record")
    assert [r.path for r in load_records(tmp_path, SIZE, [0])] == [first, second]


def test_all_samples_concatenates(fixture_record_path):
    record = load_record(fixture_record_path, SIZE, 0)
    both = all_samples([record, record], record.komi)
    assert len(both) == 2 * len(record.moves)


def _drop_last_policy(record):
    record["policies"].pop()


def _flip_winner(record):
    record["winner"] = "black" if record["winner"] == "white" else "white"


def _stone_on_a_stone(record):
    moves = record["game"]["moves"]
    stones = [move for move in moves if not move.get("pass", False)]
    later = next(i for i, move in enumerate(moves) if i > 1 and not move.get("pass", False))
    moves[later] = {**moves[later], "position": stones[0]["position"]}


def _capture_of_an_empty_point(record):
    index = next(iter(record["game"]["captures"]))
    color = record["game"]["captures"][index][0]["color"]
    empty = {"position": {"x": 1, "y": 1, "z": 1}, "color": color}
    record["game"]["captures"] = {"0": [empty]}


def _unfinished_game(record):
    record["game"]["moves"].pop()
    record["policies"].pop()


def _negative_share(record):
    record["policies"][0][0] = -1.0


@pytest.mark.parametrize(
    "change",
    [
        lambda r: r.update(contract_version=2),
        lambda r: r.update(size=7),
        lambda r: r.update(generation=1),
        lambda r: r.update(scoring_rule="territory"),
        lambda r: r.update(komi=0.5),
        lambda r: r.pop("winner"),
        _drop_last_policy,
        _flip_winner,
        _stone_on_a_stone,
        _capture_of_an_empty_point,
        _unfinished_game,
        _negative_share,
    ],
)
def test_invalid_record_names_its_path(fixture_record_path, tmp_path, change):
    path = write_changed(fixture_record_path, tmp_path, change)
    with pytest.raises(RecordError, match=str(path)):
        load_record(path, SIZE, 0)


def test_unreadable_record(tmp_path):
    path = tmp_path / "broken.json"
    path.write_text("{")
    with pytest.raises(RecordError, match=str(path)):
        load_record(path, SIZE, 0)


@pytest.mark.parametrize(
    "margins, komi",
    [
        ([3], 3.5),
        ([1, 3, 5], 3.5),
        ([3, 4], 3.5),
        ([2, 3, 4, 5], 3.5),
        ([200], 124.5),
        ([-3], -2.5),
        ([-3, -4], -3.5),
        ([-200], -124.5),
        ([125], 124.5),
    ],
)
def test_komi_refit(margins, komi):
    assert refit_komi(margins, SIZE) == komi


def test_komi_refit_needs_margins():
    with pytest.raises(ValueError):
        refit_komi([], SIZE)

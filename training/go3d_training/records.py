"""The self-play records of `SelfPlay` (docs/self-play-records.md), as training samples.

A record gives one sample per move `i`: the planes of the position before move `i`, the policy
`policies[i]`, and `z`, +1 if the player to move there wins at the komi of the model being
trained, else -1. The position is rebuilt from the `moves` and `captures` of the record's game.
"""

import json
import math
import statistics
from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from go3d_training.encoding import (
    CONTRACT_VERSION,
    SCORING_RULE,
    Color,
    Point,
    action_count,
    encode,
    player_to_move,
    point_count,
)
from go3d_training.store import record_dir

RECORD_SUFFIX = ".json"
POLICY_SUM_TOLERANCE = 1e-6
COLOR_SYMBOLS = {"@": Color.BLACK, "O": Color.WHITE}
WINNER_NAMES = {"black": Color.BLACK, "white": Color.WHITE}
WIN = 1.0
LOSS = -1.0


class RecordError(ValueError):
    """A record that does not follow docs/self-play-records.md."""


@dataclass(frozen=True)
class Record:
    """A self-play game: its moves (a point, or None for a pass) in order, the stones each move
    captured by the index of the move, the planes of the position before each move, the policy
    of each move, the winner and the score."""

    path: Path
    size: int
    generation: int
    komi: float
    moves: tuple[Point | None, ...]
    captures: dict[int, tuple[Point, ...]]
    planes: np.ndarray
    policies: np.ndarray
    winner: Color
    black_score: int
    white_score: int

    @property
    def margin(self) -> int:
        """Black's area minus White's, without the komi."""
        return self.black_score - self.white_score

    def winner_at(self, komi: float) -> Color:
        """The winner of the game at `komi`: Black if its area beats White's plus the komi."""
        return Color.BLACK if self.black_score > self.white_score + komi else Color.WHITE


@dataclass(frozen=True)
class Samples:
    """`planes` `[n, 5, s, s, s]`, `policies` `[n, s³ + 1]` and `values` `[n]`, all float32."""

    planes: np.ndarray
    policies: np.ndarray
    values: np.ndarray

    def __len__(self) -> int:
        return len(self.values)


@dataclass(frozen=True)
class Position:
    """The stones on the board before a move, and whether the move before was a pass."""

    black: frozenset[Point]
    white: frozenset[Point]
    moves_played: int
    previous_pass: bool

    def planes(self, size: int) -> np.ndarray:
        return encode(size, self.black, self.white, self.moves_played, self.previous_pass)


def load_record(path: Path, size: int, generation: int) -> Record:
    """The record at `path`, which generation `generation` of `size` played, or a RecordError
    that names the path."""
    try:
        record = _parse(json.loads(path.read_text()), path, size, generation)
        _check_winner(record)
        return record
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise RecordError(f"{path} is not a valid self-play record: {error}") from error


def load_records(records: Path, size: int, generations: Sequence[int]) -> list[Record]:
    """The records of `generations` under `records/<size>/<generation>/`, in name order."""
    return [
        load_record(path, size, generation)
        for generation in generations
        for path in record_paths(records, size, generation)
    ]


def record_paths(records: Path, size: int, generation: int) -> list[Path]:
    directory = record_dir(records, size, generation)
    if not directory.is_dir():
        return []
    return sorted(path for path in directory.iterdir() if path.suffix == RECORD_SUFFIX)


def _expect(condition: bool, message: str) -> None:
    if not condition:
        raise RecordError(message)


def _parse(data: dict[str, Any], path: Path, size: int, generation: int) -> Record:
    version = CONTRACT_VERSION
    _expect(data["contract_version"] == version, f"contract_version is not {version}")
    _expect(data["size"] == size and data["game"]["size"] == size, f"size is not {size}")
    _expect(data["generation"] == generation, f"generation is not {generation}")
    _expect(data["scoring_rule"] == SCORING_RULE, f"scoring_rule is not {SCORING_RULE}")
    komi = float(data["komi"])
    _expect(komi == float(data["game"]["komi"]), "komi differs from the komi of the game")
    moves = tuple(_move(move, index, size) for index, move in enumerate(data["game"]["moves"]))
    _expect(_is_over(moves, size), "the game is not over")
    captures = {
        _move_index(key, len(moves)): tuple(_captured(stones, int(key), size))
        for key, stones in data["game"]["captures"].items()
    }
    policies = np.array(data["policies"], dtype=np.float64)
    _check_policies(policies, len(moves), size)
    return Record(
        path=path,
        size=size,
        generation=generation,
        komi=komi,
        moves=moves,
        captures=captures,
        planes=_replay(moves, captures, size),
        policies=policies.astype(np.float32),
        winner=WINNER_NAMES[data["winner"]],
        black_score=int(data["score"]["black"]),
        white_score=int(data["score"]["white"]),
    )


def _color(move: dict[str, Any]) -> Color:
    return COLOR_SYMBOLS[move["color"]["color"]]


def _move(move: dict[str, Any], index: int, size: int) -> Point | None:
    _expect(_color(move) is player_to_move(index), f"move {index} is not of the player to move")
    if move.get("pass") is True:
        return None
    return _point(move, size)


def _point(move: dict[str, Any], size: int) -> Point:
    position = move["position"]
    point = (int(position["x"]), int(position["y"]), int(position["z"]))
    _expect(all(1 <= c <= size for c in point), f"{point} is not on a board of size {size}")
    return point


def _is_over(moves: tuple[Point | None, ...], size: int) -> bool:
    """Whether a game with `moves` is over, as `Game.isOver`: two passes in a row, or size³
    moves."""
    two_passes = len(moves) >= 2 and moves[-1] is None and moves[-2] is None
    return two_passes or len(moves) >= point_count(size)


def _move_index(key: str, move_count: int) -> int:
    index = int(key)
    _expect(0 <= index < move_count, f"captures of move {key}, which was not played")
    return index


def _captured(stones: list[dict[str, Any]], index: int, size: int) -> Iterator[Point]:
    """The stones that move `index` captured: the opponent's of the player who made it."""
    for stone in stones:
        _expect(_color(stone) is player_to_move(index + 1), f"move {index} captured its own stone")
        yield _point(stone, size)


def _check_policies(policies: np.ndarray, move_count: int, size: int) -> None:
    _expect(policies.shape == (move_count, action_count(size)), "a policy per move is missing")
    _expect(bool((policies >= 0).all()), "a policy has a negative share")
    sums = policies.sum(axis=1)
    _expect(bool(np.allclose(sums, 1.0, atol=POLICY_SUM_TOLERANCE)), "a policy does not sum to 1")


def _check_winner(record: Record) -> None:
    winner = record.winner_at(record.komi)
    _expect(record.winner is winner, "the winner does not follow from the score and the komi")


def _replay(
    moves: tuple[Point | None, ...], captures: dict[int, tuple[Point, ...]], size: int
) -> np.ndarray:
    """The planes `[n, 5, s, s, s]` of the position before each of the `n` moves; the replay
    checks the stones of every move."""
    return np.stack([position.planes(size) for position in _positions(moves, captures)])


def _positions(
    moves: tuple[Point | None, ...], captures: dict[int, tuple[Point, ...]]
) -> Iterator[Position]:
    """The position before each of `moves`: the empty board with the moves before it played in
    order, each stone placed, then the stones it captured removed."""
    stones: dict[Color, set[Point]] = {Color.BLACK: set(), Color.WHITE: set()}
    for index, move in enumerate(moves):
        previous_pass = index > 0 and moves[index - 1] is None
        black, white = frozenset(stones[Color.BLACK]), frozenset(stones[Color.WHITE])
        yield Position(black, white, index, previous_pass)
        player = player_to_move(index)
        if move is not None:
            occupied = move in stones[Color.BLACK] or move in stones[Color.WHITE]
            _expect(not occupied, f"move {index} is on the stone at {move}")
            stones[player].add(move)
        for stone in captures.get(index, ()):
            _expect(stone in stones[player.opponent()], f"move {index} captured {stone}, no stone")
            stones[player.opponent()].remove(stone)


def samples(record: Record, komi: float) -> Samples:
    """One sample per move of `record`, its value the result at `komi`: the komi of the model
    that is trained on it, which may not be the komi the game was played at."""
    winner = record.winner_at(komi)
    values = [
        WIN if player_to_move(index) is winner else LOSS for index in range(len(record.moves))
    ]
    return Samples(
        planes=record.planes,
        policies=record.policies,
        values=np.array(values, dtype=np.float32),
    )


def all_samples(records: Sequence[Record], komi: float) -> Samples:
    """The samples at `komi` of every record of `records`, which must not be empty, in order."""
    parts = [samples(record, komi) for record in records]
    return Samples(
        planes=np.concatenate([part.planes for part in parts]),
        policies=np.concatenate([part.policies for part in parts]),
        values=np.concatenate([part.values for part in parts]),
    )


def refit_komi(margins: Sequence[int], size: int) -> float:
    """The half-integer nearest the median of Black-minus-White `margins`, the one above an
    integer median, within the valid komi of `size`: a half-integer of magnitude at most size³
    (`Komi.scala`)."""
    if not margins:
        raise ValueError("no margins to refit the komi from")
    komi = math.floor(statistics.median(margins)) + 0.5
    bound = point_count(size) - 0.5
    return max(-bound, min(bound, komi))

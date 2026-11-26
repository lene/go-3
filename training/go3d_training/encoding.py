"""The board encoding and the action indexing of the model contract (docs/model-contract.md).

Coordinates are those of the Scala `Position`: x, y and z run from 1 to the board size. A
tensor index is `coordinate - 1`. The JVM engine must build the same planes and use the same
action indices, or it plays legal but meaningless moves.
"""

from collections.abc import Iterable
from enum import Enum, auto

import numpy as np

CONTRACT_VERSION = 1
SCORING_RULE = "area"
INPUT_PLANES = 5
MIN_BOARD_SIZE = 3
MAX_BOARD_SIZE = 25

PLANE_PLAYER_TO_MOVE = 0
PLANE_OPPONENT = 1
PLANE_BLACK_TO_MOVE = 2
PLANE_MOVES_PLAYED = 3
PLANE_PREVIOUS_PASS = 4

Point = tuple[int, int, int]


class Color(Enum):
    BLACK = auto()
    WHITE = auto()

    def opponent(self) -> "Color":
        return Color.WHITE if self is Color.BLACK else Color.BLACK


def validate_size(size: int) -> int:
    """`size` if it is a board size `Goban.start` accepts (odd, 3 to 25), else a ValueError."""
    if size < MIN_BOARD_SIZE:
        raise ValueError(f"board size {size} is too small, the smallest is {MIN_BOARD_SIZE}")
    if size > MAX_BOARD_SIZE:
        raise ValueError(f"board size {size} is too big, the biggest is {MAX_BOARD_SIZE}")
    if size % 2 == 0:
        raise ValueError(f"board size {size} is even, only odd sizes are valid")
    return size


def point_count(size: int) -> int:
    return size**3


def action_count(size: int) -> int:
    """Every point plus a pass: size³ + 1."""
    return point_count(size) + 1


def pass_action(size: int) -> int:
    return point_count(size)


def _check_point(point: Point, size: int) -> None:
    if not all(1 <= c <= size for c in point):
        raise ValueError(f"point {point} is not on a board of size {size}")


def action_of_point(point: Point, size: int) -> int:
    """The action of a stone at `point`: ((x-1)*size + (y-1))*size + (z-1)."""
    _check_point(point, size)
    x, y, z = point
    return ((x - 1) * size + (y - 1)) * size + (z - 1)


def point_of_action(action: int, size: int) -> Point | None:
    """The point of `action`, or None for the pass; the inverse of `action_of_point`."""
    if not 0 <= action < action_count(size):
        raise ValueError(f"action {action} is not between 0 and {pass_action(size)}")
    if action == pass_action(size):
        return None
    xy, z = divmod(action, size)
    x, y = divmod(xy, size)
    return x + 1, y + 1, z + 1


def player_to_move(moves_played: int) -> Color:
    """Black moves first and the turn alternates, passes included: Black after an even count."""
    return Color.BLACK if moves_played % 2 == 0 else Color.WHITE


def encode(
    size: int,
    black: Iterable[Point],
    white: Iterable[Point],
    moves_played: int,
    previous_pass: bool,
) -> np.ndarray:
    """The input planes `[5, size, size, size]` of one position, as float32.

    `moves_played` counts every move so far, passes included, as `Game.moves` does; the player
    to move follows from it. `previous_pass` says whether the last of those moves was a pass.
    """
    validate_size(size)
    to_move = player_to_move(moves_played)
    stones = {Color.BLACK: list(black), Color.WHITE: list(white)}
    planes = np.zeros((INPUT_PLANES, size, size, size), dtype=np.float32)
    _place(planes[PLANE_PLAYER_TO_MOVE], stones[to_move], size)
    _place(planes[PLANE_OPPONENT], stones[to_move.opponent()], size)
    planes[PLANE_BLACK_TO_MOVE] = 1.0 if to_move is Color.BLACK else 0.0
    planes[PLANE_MOVES_PLAYED] = moves_played / point_count(size)
    planes[PLANE_PREVIOUS_PASS] = 1.0 if previous_pass else 0.0
    return planes


def _place(plane: np.ndarray, points: list[Point], size: int) -> None:
    for point in points:
        _check_point(point, size)
        x, y, z = point
        plane[x - 1, y - 1, z - 1] = 1.0

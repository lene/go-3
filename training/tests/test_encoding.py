import numpy as np
import pytest

from go3d_training.encoding import (
    PLANE_BLACK_TO_MOVE,
    PLANE_MOVES_PLAYED,
    PLANE_OPPONENT,
    PLANE_PLAYER_TO_MOVE,
    PLANE_PREVIOUS_PASS,
    Color,
    action_count,
    action_of_point,
    encode,
    pass_action,
    player_to_move,
    point_of_action,
    validate_size,
)

SIZES = [3, 5, 7, 9]


def test_empty_board_black_to_move():
    planes = encode(5, [], [], 0, False)
    assert planes.shape == (5, 5, 5, 5)
    assert planes.dtype == np.float32
    assert not planes[PLANE_PLAYER_TO_MOVE].any()
    assert not planes[PLANE_OPPONENT].any()
    assert (planes[PLANE_BLACK_TO_MOVE] == 1).all()
    assert not planes[PLANE_MOVES_PLAYED].any()
    assert not planes[PLANE_PREVIOUS_PASS].any()


def test_worked_example_of_the_contract():
    planes = encode(5, [(1, 1, 1)], [(3, 3, 3)], 2, False)
    to_move = np.zeros((5, 5, 5), dtype=np.float32)
    to_move[0, 0, 0] = 1
    opponent = np.zeros((5, 5, 5), dtype=np.float32)
    opponent[2, 2, 2] = 1
    np.testing.assert_array_equal(planes[PLANE_PLAYER_TO_MOVE], to_move)
    np.testing.assert_array_equal(planes[PLANE_OPPONENT], opponent)
    assert (planes[PLANE_BLACK_TO_MOVE] == 1).all()
    np.testing.assert_allclose(planes[PLANE_MOVES_PLAYED], 2 / 125)
    assert not planes[PLANE_PREVIOUS_PASS].any()
    assert action_of_point((3, 3, 3), 5) == 62


def test_after_a_pass():
    after_pass = encode(5, [(3, 3, 3)], [], 2, True)
    after_stone = encode(5, [(3, 3, 3)], [(1, 1, 1)], 2, False)
    assert (after_pass[PLANE_PREVIOUS_PASS] == 1).all()
    assert not after_stone[PLANE_PREVIOUS_PASS].any()


def test_white_to_move_after_an_odd_move_count():
    planes = encode(5, [(1, 2, 3), (1, 1, 1)], [(5, 4, 3)], 3, False)
    assert planes[PLANE_PLAYER_TO_MOVE, 4, 3, 2] == 1
    assert planes[PLANE_PLAYER_TO_MOVE].sum() == 1
    assert planes[PLANE_OPPONENT, 0, 1, 2] == 1
    assert planes[PLANE_OPPONENT, 0, 0, 0] == 1
    assert planes[PLANE_OPPONENT].sum() == 2
    assert not planes[PLANE_BLACK_TO_MOVE].any()
    np.testing.assert_allclose(planes[PLANE_MOVES_PLAYED], 3 / 125)


@pytest.mark.parametrize("moves, color", [(0, Color.BLACK), (1, Color.WHITE), (2, Color.BLACK)])
def test_player_to_move(moves, color):
    assert player_to_move(moves) is color


@pytest.mark.parametrize("size", SIZES)
def test_point_to_action_and_back(size):
    points = [
        (x, y, z)
        for x in range(1, size + 1)
        for y in range(1, size + 1)
        for z in range(1, size + 1)
    ]
    actions = [action_of_point(point, size) for point in points]
    assert actions == list(range(size**3))
    assert [point_of_action(action, size) for action in actions] == points


def test_action_index_formula():
    assert action_of_point((1, 1, 1), 5) == 0
    assert action_of_point((1, 1, 2), 5) == 1
    assert action_of_point((1, 2, 1), 5) == 5
    assert action_of_point((2, 1, 1), 5) == 25
    assert action_of_point((5, 5, 5), 5) == 124


def test_pass():
    assert pass_action(5) == 125
    assert action_count(5) == 126
    assert point_of_action(125, 5) is None


@pytest.mark.parametrize("size, actions", [(5, 126), (7, 344), (9, 730)])
def test_action_count(size, actions):
    assert action_count(size) == actions


@pytest.mark.parametrize("point", [(0, 1, 1), (1, 6, 1), (1, 1, 0)])
def test_point_off_the_board(point):
    with pytest.raises(ValueError):
        action_of_point(point, 5)
    with pytest.raises(ValueError):
        encode(5, [point], [], 1, False)


@pytest.mark.parametrize("action", [-1, 126])
def test_action_out_of_range(action):
    with pytest.raises(ValueError):
        point_of_action(action, 5)


@pytest.mark.parametrize("size", [-1, 0, 1, 2, 4, 6, 27])
def test_bad_size(size):
    with pytest.raises(ValueError):
        validate_size(size)


@pytest.mark.parametrize("size", [3, 5, 7, 9, 25])
def test_good_size(size):
    assert validate_size(size) == size

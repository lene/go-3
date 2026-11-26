import numpy as np
import pytest

from go3d_training.encoding import (
    PLANE_OPPONENT,
    PLANE_PLAYER_TO_MOVE,
    action_count,
    action_of_point,
    encode,
    pass_action,
)
from go3d_training.symmetry import IDENTITY, SYMMETRIES, Symmetry, augment

SIZE = 5
BLACK = [(1, 1, 1), (1, 2, 3), (5, 4, 1)]
WHITE = [(3, 3, 3), (2, 5, 4)]


def every_point(size: int = SIZE) -> list[tuple[int, int, int]]:
    span = range(1, size + 1)
    return [(x, y, z) for x in span for y in span for z in span]


def distinct_policy() -> np.ndarray:
    """A policy whose shares all differ, so that any move of a share shows."""
    shares = np.arange(1, action_count(SIZE) + 1, dtype=np.float32)
    return shares / shares.sum()


def test_there_are_48_distinct_symmetries():
    assert len(SYMMETRIES) == 48
    assert IDENTITY in SYMMETRIES
    images = {tuple(symmetry.point(p, SIZE) for p in every_point()) for symmetry in SYMMETRIES}
    assert len(images) == 48


@pytest.mark.parametrize("symmetry", SYMMETRIES)
def test_each_symmetry_maps_the_board_onto_itself(symmetry):
    assert sorted(symmetry.point(p, SIZE) for p in every_point()) == every_point()


@pytest.mark.parametrize("symmetry", SYMMETRIES)
def test_planes_and_policy_move_together(symmetry):
    planes = encode(SIZE, BLACK, WHITE, 5, False)
    policy = distinct_policy()
    moved_planes = symmetry.planes(planes)
    moved_policy = symmetry.policy(policy, SIZE)
    expected_planes = encode(
        SIZE,
        [symmetry.point(p, SIZE) for p in BLACK],
        [symmetry.point(p, SIZE) for p in WHITE],
        5,
        False,
    )
    np.testing.assert_array_equal(moved_planes, expected_planes)
    for point in every_point():
        image = symmetry.point(point, SIZE)
        assert moved_policy[action_of_point(image, SIZE)] == policy[action_of_point(point, SIZE)]
    assert moved_policy[pass_action(SIZE)] == policy[pass_action(SIZE)]


@pytest.mark.parametrize("symmetry", SYMMETRIES)
def test_inverse_restores_planes_and_policy(symmetry):
    planes = encode(SIZE, BLACK, WHITE, 4, True)
    policy = distinct_policy()
    inverse = symmetry.inverse()
    np.testing.assert_array_equal(inverse.planes(symmetry.planes(planes)), planes)
    np.testing.assert_array_equal(inverse.policy(symmetry.policy(policy, SIZE), SIZE), policy)
    assert all(inverse.point(symmetry.point(p, SIZE), SIZE) == p for p in every_point())


def test_reflection_and_permutation():
    swap_and_flip = Symmetry((1, 0, 2), (True, False, False))
    assert swap_and_flip.point((1, 2, 3), SIZE) == (4, 1, 3)
    assert IDENTITY.point((1, 2, 3), SIZE) == (1, 2, 3)


def test_identity_changes_nothing():
    planes = encode(SIZE, BLACK, WHITE, 5, False)
    np.testing.assert_array_equal(IDENTITY.planes(planes), planes)
    np.testing.assert_array_equal(IDENTITY.policy(distinct_policy(), SIZE), distinct_policy())


def test_augment_applies_each_sample_its_own_symmetry():
    planes = np.stack([encode(SIZE, BLACK, WHITE, n, False) for n in range(3)])
    policies = np.stack([distinct_policy()] * 3)
    indices = np.array([0, 17, 47])
    out_planes, out_policies = augment(planes, policies, indices)
    for n, index in enumerate(indices):
        np.testing.assert_array_equal(out_planes[n], SYMMETRIES[index].planes(planes[n]))
        np.testing.assert_array_equal(out_policies[n], SYMMETRIES[index].policy(policies[n], SIZE))
    assert out_planes[:, PLANE_PLAYER_TO_MOVE].sum() == planes[:, PLANE_PLAYER_TO_MOVE].sum()
    assert out_planes[:, PLANE_OPPONENT].sum() == planes[:, PLANE_OPPONENT].sum()

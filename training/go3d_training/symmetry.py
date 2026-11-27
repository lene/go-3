"""The 48 symmetries of the cube, applied to the input planes and to the policy alike.

A symmetry permutes the three axes (6 ways) and reflects any of them (8 ways). It maps the
point `(c1, c2, c3)` to the point whose coordinate `i` is `c[axes[i]]`, reflected to
`size + 1 - c[axes[i]]` if `flips[i]`. No plane of `encoding.encode` depends on the orientation
of the board, so every symmetry maps a legal position to a legal one, and the pass keeps its
action.
"""

from dataclasses import dataclass
from functools import cache
from itertools import permutations, product

import numpy as np

from go3d_training.encoding import Point, action_count, action_of_point, point_of_action

DIMENSIONS = 3


@dataclass(frozen=True)
class Symmetry:
    axes: tuple[int, int, int]
    flips: tuple[bool, bool, bool]

    def point(self, point: Point, size: int) -> Point:
        x, y, z = (self._coordinate(point, i, size) for i in range(DIMENSIONS))
        return x, y, z

    def _coordinate(self, point: Point, i: int, size: int) -> int:
        coordinate = point[self.axes[i]]
        return size + 1 - coordinate if self.flips[i] else coordinate

    def inverse(self) -> "Symmetry":
        """The symmetry that undoes this one."""
        inverse_axes = tuple(self.axes.index(j) for j in range(DIMENSIONS))
        a, b, c = inverse_axes
        return Symmetry((a, b, c), (self.flips[a], self.flips[b], self.flips[c]))

    def planes(self, planes: np.ndarray) -> np.ndarray:
        """`planes` with the symmetry applied to its last three, spatial axes: a stone at a
        point moves to the image of the point."""
        lead = planes.ndim - DIMENSIONS
        spatial = [lead + axis for axis in self.axes]
        transposed = np.transpose(planes, [*range(lead), *spatial])
        flipped = tuple(lead + i for i, flip in enumerate(self.flips) if flip)
        return np.ascontiguousarray(np.flip(transposed, axis=flipped))

    def policy(self, policy: np.ndarray, size: int) -> np.ndarray:
        """`policy` over the actions of `size` on its last axis, with the share of each point
        moved to the image of the point; the pass keeps its share."""
        return np.ascontiguousarray(policy[..., _source_actions(self, size)])


@cache
def _source_actions(symmetry: Symmetry, size: int) -> np.ndarray:
    """For each action, the action that the symmetry maps to it."""
    inverse = symmetry.inverse()
    return np.array([_image(inverse, action, size) for action in range(action_count(size))])


def _image(symmetry: Symmetry, action: int, size: int) -> int:
    point = point_of_action(action, size)
    return action if point is None else action_of_point(symmetry.point(point, size), size)


SYMMETRIES: tuple[Symmetry, ...] = tuple(
    Symmetry((a, b, c), (f, g, h))
    for (a, b, c) in permutations(range(DIMENSIONS))
    for (f, g, h) in product((False, True), repeat=DIMENSIONS)
)
IDENTITY = Symmetry((0, 1, 2), (False, False, False))


def augment(
    planes: np.ndarray, policies: np.ndarray, symmetry_indices: np.ndarray
) -> tuple[np.ndarray, np.ndarray]:
    """Sample `n` of `planes` `[n, 5, s, s, s]` and `policies` `[n, s³ + 1]` under the symmetry
    `SYMMETRIES[symmetry_indices[n]]`."""
    size = planes.shape[-1]
    out_planes = np.empty_like(planes)
    out_policies = np.empty_like(policies)
    for index in np.unique(symmetry_indices):
        chosen = symmetry_indices == index
        symmetry = SYMMETRIES[index]
        out_planes[chosen] = symmetry.planes(planes[chosen])
        out_policies[chosen] = symmetry.policy(policies[chosen], size)
    return out_planes, out_policies

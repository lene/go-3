"""The policy-and-value network: a residual tower of 3D convolutions with two heads.

One network per board size. It maps the planes of `encoding.encode` to the policy logits over
`encoding.action_count(size)` actions and a value in [-1, 1] for the player to move.
"""

from dataclasses import dataclass

import torch
from torch import nn

from go3d_training.encoding import INPUT_PLANES, action_count, point_count, validate_size

DEFAULT_BLOCKS = 6
DEFAULT_CHANNELS = 64
KERNEL_SIZE = 3
HEAD_KERNEL_SIZE = 1
POLICY_CHANNELS = 2
VALUE_CHANNELS = 1


@dataclass(frozen=True)
class NetworkConfig:
    size: int
    blocks: int = DEFAULT_BLOCKS
    channels: int = DEFAULT_CHANNELS

    def __post_init__(self) -> None:
        validate_size(self.size)
        if self.blocks < 1:
            raise ValueError(f"blocks must be at least 1, not {self.blocks}")
        if self.channels < 1:
            raise ValueError(f"channels must be at least 1, not {self.channels}")


def _conv_bn(in_channels: int, out_channels: int, kernel_size: int) -> list[nn.Module]:
    """A convolution that keeps the board shape (padding 1 for 3×3×3), then batch norm."""
    return [
        nn.Conv3d(in_channels, out_channels, kernel_size, padding=kernel_size // 2, bias=False),
        nn.BatchNorm3d(out_channels),
    ]


class ResidualBlock(nn.Module):
    def __init__(self, channels: int) -> None:
        super().__init__()
        self.body = nn.Sequential(
            *_conv_bn(channels, channels, KERNEL_SIZE),
            nn.ReLU(),
            *_conv_bn(channels, channels, KERNEL_SIZE),
        )
        self.relu = nn.ReLU()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out: torch.Tensor = self.relu(x + self.body(x))  # a Module call is typed as Any
        return out


class PolicyValueNetwork(nn.Module):
    """Input `[batch, 5, size, size, size]`; outputs `policy` `[batch, size³ + 1]` (logits, no
    softmax, no legality mask) and `value` `[batch, 1]` (tanh)."""

    def __init__(self, config: NetworkConfig) -> None:
        super().__init__()
        self.config = config
        channels = config.channels
        points = point_count(config.size)
        self.stem = nn.Sequential(*_conv_bn(INPUT_PLANES, channels, KERNEL_SIZE), nn.ReLU())
        self.tower = nn.Sequential(*(ResidualBlock(channels) for _ in range(config.blocks)))
        self.policy_head = nn.Sequential(
            *_conv_bn(channels, POLICY_CHANNELS, HEAD_KERNEL_SIZE),
            nn.ReLU(),
            nn.Flatten(),
            nn.Linear(POLICY_CHANNELS * points, action_count(config.size)),
        )
        self.value_head = nn.Sequential(
            *_conv_bn(channels, VALUE_CHANNELS, HEAD_KERNEL_SIZE),
            nn.ReLU(),
            nn.Flatten(),
            nn.Linear(VALUE_CHANNELS * points, channels),
            nn.ReLU(),
            nn.Linear(channels, 1),
            nn.Tanh(),
        )

    def forward(self, board: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        features = self.tower(self.stem(board))
        return self.policy_head(features), self.value_head(features)


def build_network(config: NetworkConfig, seed: int) -> PolicyValueNetwork:
    """A network with weights drawn from `seed`, in eval mode: the same seed gives the same
    weights."""
    with torch.random.fork_rng():
        torch.manual_seed(seed)
        network = PolicyValueNetwork(config)
    return network.eval()

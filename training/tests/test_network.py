import pytest
import torch

from go3d_training.encoding import INPUT_PLANES
from go3d_training.network import NetworkConfig, build_network

BATCH = 3


def random_boards(size: int, batch: int = BATCH) -> torch.Tensor:
    generator = torch.Generator().manual_seed(1)
    return torch.rand(batch, INPUT_PLANES, size, size, size, generator=generator)


@pytest.mark.parametrize("size, actions", [(5, 126), (7, 344), (9, 730)])
def test_output_shapes(size, actions):
    network = build_network(NetworkConfig(size), seed=0)
    with torch.no_grad():
        policy, value = network(random_boards(size))
    assert policy.shape == (BATCH, actions)
    assert value.shape == (BATCH, 1)
    assert ((value >= -1) & (value <= 1)).all()


def test_default_tower():
    network = build_network(NetworkConfig(5), seed=0)
    assert network.config.blocks == 6
    assert network.config.channels == 64
    assert len(network.tower) == 6


def test_blocks_and_channels_are_parameters():
    network = build_network(NetworkConfig(5, blocks=2, channels=8), seed=0)
    assert len(network.tower) == 2
    with torch.no_grad():
        policy, value = network(random_boards(5))
    assert policy.shape == (BATCH, 126)
    assert value.shape == (BATCH, 1)


def test_same_seed_gives_the_same_network():
    boards = random_boards(5)
    with torch.no_grad():
        first = build_network(NetworkConfig(5), seed=7)(boards)
        second = build_network(NetworkConfig(5), seed=7)(boards)
        other = build_network(NetworkConfig(5), seed=8)(boards)
    assert torch.equal(first[0], second[0])
    assert torch.equal(first[1], second[1])
    assert not torch.equal(first[0], other[0])


def test_building_does_not_change_the_global_generator():
    torch.manual_seed(3)
    expected = torch.rand(1)
    torch.manual_seed(3)
    build_network(NetworkConfig(5), seed=0)
    assert torch.equal(torch.rand(1), expected)


@pytest.mark.parametrize("size, blocks, channels", [(4, 6, 64), (5, 0, 64), (5, 6, 0)])
def test_bad_config(size, blocks, channels):
    with pytest.raises(ValueError):
        NetworkConfig(size, blocks, channels)

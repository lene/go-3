"""The PyTorch checkpoint of a generation: `model.pt` next to `model.onnx`.

It holds the network config and the state dict, so that training can go on from the weights of
a generation. The ONNX model alone is not enough: it has no batch-norm statistics to train on.
"""

from dataclasses import asdict
from pathlib import Path

import torch

from go3d_training.network import NetworkConfig, PolicyValueNetwork

CONFIG_KEY = "config"
STATE_DICT_KEY = "state_dict"


def save_checkpoint(network: PolicyValueNetwork, path: Path) -> None:
    """Write the config and the weights of `network`, moved to the CPU, to `path`."""
    state_dict = {name: tensor.cpu() for name, tensor in network.state_dict().items()}
    torch.save({CONFIG_KEY: asdict(network.config), STATE_DICT_KEY: state_dict}, path)


def load_checkpoint(path: Path) -> PolicyValueNetwork:
    """The network of the checkpoint at `path`, on the CPU, in eval mode."""
    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    network = PolicyValueNetwork(NetworkConfig(**checkpoint[CONFIG_KEY]))
    network.load_state_dict(checkpoint[STATE_DICT_KEY])
    return network.eval()

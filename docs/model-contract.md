# Model contract

What a policy-and-value network of the AlphaZero engine sees and what it answers. The Python
training code (`training/go3d_training/`) and the JVM engine must follow it exactly: a mismatch
does not fail, it makes the engine play legal but meaningless moves. This is contract version 1.

There is one network per board size. Coordinates are those of `Position`: x, y and z run from 1
to the board size `s`, and a tensor index is `coordinate - 1`.

## Input

One input, named `board`: a float32 tensor `[batch, 5, s, s, s]`, indexed
`[n, plane, x - 1, y - 1, z - 1]`. The batch axis is dynamic. The tensor is row-major (C order):
the flat offset of a cell is `(((n * 5 + plane) * s + (x - 1)) * s + (y - 1)) * s + (z - 1)`.

Black moves first and the turn alternates, passes included, so the player to move follows from
the number of moves played: Black when it is even, White when it is odd.

| Plane | Content |
|---|---|
| 0 | 1 where the player to move has a stone, else 0 |
| 1 | 1 where the opponent has a stone, else 0 |
| 2 | all ones if Black is to move, all zeros if White is |
| 3 | moves played so far (`Game.moves.length`, passes included) divided by s³, in every cell |
| 4 | all ones if the previous move was a pass, all zeros otherwise (and before the first move) |

There are no other history planes: simple ko depends on history too, but the search masks
illegal moves. Plane 2 is there because the komi favours White, plane 3 because a game that
reaches s³ moves is scored as it stands, so a position's value depends on how many moves are left,
and plane 4 because two passes in a row end the game.
The komi and the scoring rule are not inputs: they are fixed per model (see the metadata).

## Actions

`s³ + 1` actions: 126 on size 5, 344 on size 7, 730 on size 9.

- A stone at `(x, y, z)` is action `((x - 1) * s + (y - 1)) * s + (z - 1)`.
- A pass is action `s³`.

The inverse, for an action `a < s³`: `z = a % s + 1`, `y = (a / s) % s + 1`, `x = a / s² + 1`
(integer division).

## Outputs

- `policy`, float32 `[batch, s³ + 1]`: logits in the action order above. There is no softmax and
  no legality mask; the search masks the illegal actions and normalises the rest.
- `value`, float32 `[batch, 1]`: in [-1, 1] (tanh), the expected result for the player to move,
  +1 a win and -1 a loss at the komi of the metadata. The search negates it one ply up.

## Metadata

The ONNX model's `metadata_props`, all strings:

| Key | Value |
|---|---|
| `contract_version` | `1` |
| `board_size` | `s`, e.g. `5` |
| `generation` | the generation number, `0` for the untrained network |
| `scoring_rule` | `area` (stones plus territory, no captures) |
| `komi` | the komi the model was made under, `4.5` for generation 0 |
| `input_planes` | `5` |
| `actions` | `s³ + 1`, e.g. `126` |
| `blocks` | residual blocks in the tower, default `6` |
| `channels` | channels of the tower, default `64` |

The JVM side should refuse a model whose `contract_version`, `board_size`, `input_planes` or
`actions` is not what it expects.

The value assumes the `komi` and `scoring_rule` of the metadata. A game with another komi (a
server game defaults to 0.5) or another scoring rule gets a biased value; whether the JVM engine
(story 5) refuses such a game or warns is its decision.

## Storage

Generation `g` of size `s` is `<store>/<s>/<g>/model.onnx`: a local directory for the proof of
concept (`models/`, git-ignored), an S3 bucket with the same keys later. Generation 0 is written
by `uv run export-generation --size 5 --generation 0 --out ../models` in `training/`
(`docs/development.md`); `--seed` (default 0) makes it reproducible.

Next to `model.onnx`, `model.pt` is the PyTorch checkpoint that training goes on from: a dict
with `config` (`size`, `blocks`, `channels`) and `state_dict`, the weights and batch-norm
statistics. `<store>/<s>/<g>/` holds only promoted generations. `uv run train-generation --size
5 --generation g` writes a candidate for generation `g` to `<store>/<s>/candidates/<g>/`, with
`model.onnx` (its `generation` is `g`, its `komi` the refitted one), `model.pt` and
`training.json`, the metrics of the run:

| Key | Value |
|---|---|
| `size`, `generation`, `parent` | the board size, `g` and `g - 1` |
| `old_komi`, `new_komi` | the komi of the parent and the refitted komi of the candidate |
| `record_generations` | the generations whose self-play records were used |
| `records`, `samples` | how many records, and how many samples (one per move) |
| `epochs`, `batch_size`, `learning_rate`, `weight_decay`, `seed`, `device` | the training settings |
| `losses` | per epoch: `{"epoch", "policy", "value", "total"}`, means over the samples |
| `duration_seconds` | the wall-clock time of the run |

## Worked example

Size 5, Black at (1,1,1), White at (3,3,3), Black to move after 2 moves:

- plane 0 (Black, to move): 1 at `[0, 0, 0]`, 0 elsewhere;
- plane 1 (White, the opponent): 1 at `[2, 2, 2]`, 0 elsewhere;
- plane 2: all ones;
- plane 3: 2 / 125 = 0.016 everywhere;
- plane 4: all zeros (the previous move was a stone).

The action for (3,3,3) is (2 · 5 + 2) · 5 + 2 = 62, and a pass is action 125. A `value` of 0.3
here means the network expects Black to win.

# Self-play records

What `SelfPlay` (`engine/src/main/scala/go3d/engine/SelfPlay.scala`) writes for training: one
JSON file per game, `<out>/<size>/<generation>/<game id>.json` (`selfplay/` by default,
git-ignored). The game id is six base-62 characters, a valid game id of the server. Coordinates,
actions and the board size `s` are those of the [model contract](model-contract.md).

## Fields

One JSON object per file:

| Field | Type | Content |
|---|---|---|
| `contract_version` | integer | the model contract version, `1` |
| `size` | integer | the board size `s`, e.g. `5` |
| `generation` | integer | the model generation that played both sides |
| `komi` | number | the komi of the game, added to White's score: the komi of the model's metadata |
| `scoring_rule` | string | `area`: stones plus territory, no captures |
| `simulations` | integer | search simulations per move |
| `seed` | integer | the 64-bit seed of the game's random generator (it may not fit a double) |
| `game` | object | the game as the server stores it (see below) |
| `policies` | array | one array of `s³ + 1` numbers per move (see below) |
| `winner` | string | `black` or `white`, at the komi of the game |
| `score` | object | `{"black": b, "white": w}`: stones plus territory, without the komi |

White wins when `w + komi > b`. The komi is a half-integer, so there is no draw.

### `game`

The JSON of the Scala `Game`, as in the server's save files, so a record replays through
`Game` (`go3d.server.decodeGame`) and the viewer:

- `size`, `komi`;
- `moves`: the moves in order. A stone is `{"position": {"x": 3, "y": 3, "z": 3}, "color":
  {"color": "@"}}`, a pass `{"pass": true, "color": {"color": "O"}}`. `@` is Black and `O` is
  White; Black moves first and the colours alternate, passes included;
- `captures`: an object from the index of a move in `moves` (a string key, from 0) to the stones
  that move captured, each as a stone move with the colour of the captured stone. A move that
  captured nothing has no key;
- `goban`: the final board.

The position before move `i` is the empty board with moves `0` to `i - 1` played in order:
place each stone, then remove the stones in `captures[str(j)]` of that move `j`. The game ends
after two passes in a row or after `s³` moves.

### `policies`

`policies[i]` is the policy to learn at the position before move `i`: the share of the root
visits of the search that chose move `i`, for every action in the action order of the model
contract (point `(x, y, z)` is `((x - 1) · s + (y - 1)) · s + (z - 1)`, a pass is `s³`). The
values are non-negative and sum to 1, and an illegal action has 0, since the search never
visits it: training needs no legality information. The value target of that position is the
result at the komi of the model being trained, seen from the player to move there (Black when
`i` is even): Black wins if `score.black > score.white + komi`. That komi is refitted for each
generation, so it may not be the komi of the record and its `winner`.

The committed record `engine/src/test/resources/selfplay/5/0/<game id>.json` pins this format:
`TestSelfPlay` checks that the writer still writes it, and the training tests read it. After a
format change on purpose, delete it and write it again from the repository root:

```bash
echo 'go3d.engine.SelfPlay.run(go3d.engine.SelfPlayOptions(5, 0, 1, 6, go3d.engine.fixtureRecordSeed, 1, "", "engine/src/test/resources/selfplay", go3d.engine.Device.Cpu), go3d.engine.FakeEvaluator(Map(), 0.0), 4.5)' | sbt engine/Test/console
```

## How the games are played

Both sides are the same generation, from an empty board at the komi of its metadata. Each move
runs `simulations` PUCT simulations of `Search` with Dirichlet noise on the root priors (α = 10 /
the number of legal actions, weight ε = 0.25). The first `s³ / 8` moves (15 on size 5) are
drawn in proportion to the root visits; the later ones are the most visited. A position whose
game is over is scored exactly by `Game`, and dead stones count for their owner, as in
`Game.score`. The same seed and options give the same records with `--parallel 1` on the CPU.

## Example

Size 5, shortened:

```json
{
  "contract_version": 1, "size": 5, "generation": 0, "komi": 4.5, "scoring_rule": "area",
  "simulations": 200, "seed": 6506741267517474185,
  "game": {"size": 5, "goban": {"size": 5, "stones": ["..."]},
           "moves": [{"position": {"x": 5, "y": 2, "z": 2}, "color": {"color": "@"}}, "..."],
           "captures": {}, "komi": 4.5},
  "policies": [[0.0, 0.04, "... 126 values"], "..."],
  "winner": "white", "score": {"black": 35, "white": 34}
}
```

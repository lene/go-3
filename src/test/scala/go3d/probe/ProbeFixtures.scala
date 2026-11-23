package go3d.probe

/** A game of size 5 in which Black wins at komi 0.5 under both scorings, and nobody captured. */
val BlackWinsResult: GameResult = GameResult(
  5,
  "random",
  "random",
  0,
  125,
  Ending.MoveLimit,
  SideScore(stones = 60, area = 70, captures = 0),
  SideScore(stones = 40, area = 55, captures = 0),
  None
)

/** Black wins by area (70 to 69.5), White wins once the 4 stones captured by White are added. */
val CaptureFlipsResult: GameResult = BlackWinsResult.copy(
  whiteSide = SideScore(stones = 40, area = 69, captures = 4)
)

/** `count` copies of `result` with consecutive game numbers. */
def games(count: Int, result: GameResult): List[GameResult] =
  List.tabulate(count)(number => result.copy(game = number))

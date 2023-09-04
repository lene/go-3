package go3d.client

import go3d.{newGame, Game, Goban, Move, Pass, newGoban, Black, White, Position}

object Util {
  def gameWithCornerStones(size: Int): Game =
    val corners = (
      for (x <- 1 to size by size-1; y <- 1 to size by size-1; z <- 1 to size by size-1)
      yield (x, y, z)
    ).map((x, y, z) => Position(x, y, z)) :++ (if size > 3 then StarPoints(size).all else Seq())
    val moves = corners.zipWithIndex.map((pos, i) => Move(pos, if i%2 == 0 then Black else White))
    playListOfMoves(size, moves)

  val presetGame: Game = Game(5, fromStrings(Map(
    1 ->
      """O   O|
        | @   |
        |     |
        |     |
        |O   O|""",
    2 ->
      """ @   |
        |@ @  |
        | @   |
        |     |
        |     |""",
    3 ->
      """ O   |
        |O@O  |
        | O   |
        |     |
        |     |""",
    4 ->
      """     |
        | O   |
        |     |
        |     |
        |     |""",
    5 ->
      """O   O|
        |     |
        |     |
        |     |
        |O   O|""",
  )), Array(), Map())

  def fromStrings(levels: Map[Int, String]): Goban =
    if levels.isEmpty then throw IllegalArgumentException("nothing to generate")
    val goban = newGoban((levels.head._2.stripMargin.replace("|", "").split("\n").length))
    for (z, level) <- levels do
      val lines = level.stripMargin.replace("|", "").split("\n")
      for (line, y) <- lines.zipWithIndex do
        for (stone, x) <- line.zipWithIndex do
          goban.stones(x + 1)(y + 1)(z) = go3d.Color(stone)
    goban

  def playListOfMoves(boardSize: Int, moves: Iterable[Move | Pass], verbose: Boolean = false): Game =
    var game = newGame(boardSize)
    for move <- moves do
      game = game.makeMove(move)
      if verbose then logger.info(move.toString + "\n" + game)
    game

}

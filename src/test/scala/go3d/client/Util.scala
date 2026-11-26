package go3d.client

import go3d.Black
import go3d.Game
import go3d.Komi
import go3d.Move
import go3d.Position
import go3d.White
import go3d.fromStrings
import go3d.playListOfMoves

object Util {
  def gameWithCornerStones(size: Int): Game =
    val corners = (
      for (x <- 1 to size by size - 1; y <- 1 to size by size - 1; z <- 1 to size by size - 1)
        yield (x, y, z)
    ).map((x, y, z) => Position(x, y, z)) :++ (if size > 3 then StarPoints(size).all else Seq())
    val moves = corners.zipWithIndex.map((pos, i) => Move(pos, if i % 2 == 0 then Black else White))
    playListOfMoves(size, moves)

  val presetGame: Game = Game(
    5,
    fromStrings(Map(
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
        |O   O|"""
    )),
    Array(),
    Map(),
    Komi.Default
  )
}

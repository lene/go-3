package go3d.client

import go3d.Black
import go3d.Game
import go3d.Goban
import go3d.Komi
import go3d.Move
import go3d.Pass
import go3d.Position
import go3d.White
import org.scalatest.TryValues.*

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

  def fromStrings(levels: Map[Int, String]): Goban =
    if levels.isEmpty then sys.error("nothing to generate")
    val lines = levels.view.mapValues(_.stripMargin.replace("|", "").split("\n")).toMap
    val size = lines.head._2.length
    Goban
      .tabulate(size)((x, y, z) =>
        // a point the strings leave out is empty: `stripMargin` empties a first line of blanks
        lines.get(z).flatMap(_.lift(y - 1)).flatMap(_.lift(x - 1))
          .fold(go3d.Empty)(go3d.Color(_).success.value)
      )
      .success
      .value

  private def playListOfMoves(boardSize: Int, moves: Iterable[Move | Pass]): Game =
    var game = Game.start(boardSize).success.value
    for move <- moves do
      game = game.makeMove(move).success.value
    game

}

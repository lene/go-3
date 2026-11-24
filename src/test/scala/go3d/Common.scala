package go3d

import org.junit.jupiter.api.Assertions

import scala.reflect.ClassTag
import org.scalatest.TryValues.*

val TestSize = MinBoardSize

val CaptureMoves =
  Move(2, 2, 2, Black) :: Move(2, 2, 1, White) :: Move(2, 1, 1, Black) :: Move(2, 2, 3, White) ::
    Move(2, 3, 1, Black) :: Move(2, 1, 2, White) :: Move(3, 2, 1, Black) :: Move(2, 3, 2, White) ::
    Move(1, 2, 1, Black) :: Nil

/** Komi texts that `/new/3?komi=...` rejects: integers, no number, a rounded number, no value and
  * a komi beyond 3^3. Cases that are not one plain text (a bare or repeated parameter) are listed
  * by the suites that test them.
  */
val BadKomiTexts: List[String] = List(
  "0",
  "7",
  "-7",
  "abc",
  "NaN",
  "6.5f",
  "1e2",
  ".5",
  "6.",
  "",
  "27.5",
  "-27.5",
  "6.5000000000000001"
)

/** A game of size 3 without captures, ended by two passes: black has 2 points, white 1. */
val BlackLeadsByOneMoves: List[Move | Pass] = List(
  Move(2, 2, 2, Black),
  Move(1, 1, 1, White),
  Move(3, 3, 3, Black),
  Pass(White),
  Pass(Black)
)

/** A game of size 3 without captures, ended by two passes: black has 7 points, white 1. */
val BlackLeadsBySixMoves: List[Move | Pass] = List(
  Move(2, 2, 2, Black),
  Move(1, 1, 1, White),
  Move(3, 3, 3, Black),
  Pass(White),
  Move(1, 3, 3, Black),
  Pass(White),
  Move(3, 1, 3, Black),
  Pass(White),
  Move(3, 3, 1, Black),
  Pass(White),
  Move(1, 3, 1, Black),
  Pass(White),
  Move(3, 1, 1, Black),
  Pass(White),
  Pass(Black)
)

/** White stones around two disjoint black stones on a size 5 board: one white move at (3, 1, 1)
  * captures both.
  */
val TwoDisjointStonesCaptureSetup: List[Move | Pass] = List(
  Move(2, 1, 1, Black),
  Move(1, 1, 1, White),
  Move(4, 1, 1, Black),
  Move(5, 1, 1, White),
  Pass(Black),
  Move(2, 2, 1, White),
  Pass(Black),
  Move(4, 2, 1, White),
  Pass(Black),
  Move(2, 1, 2, White),
  Pass(Black),
  Move(4, 1, 2, White),
  Pass(Black)
)

val eyeSituation = Map(
  1 ->
    """     |
      | @   |
      |     |
      |     |
      |     |""",
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
      |     |"""
)

def playListOfMoves(boardSize: Int, moves: Iterable[Move | Pass], verbose: Boolean = false): Game =
  var game = Game.start(boardSize).success.value
  for move <- moves do
    game = game.makeMove(move).success.value
    if verbose then println(move.toString + "\n" + game.toString)
  game

def playListOfMovesWithKomi(boardSize: Int, komi: Double, moves: Iterable[Move | Pass]): Game =
  moves.foldLeft(Game.start(boardSize, komi).success.value) { (game, move) =>
    game.makeMove(move).success.value
  }

def setListOfStones(boardSize: Int, moves: List[Move | Pass]): Goban =
  var goban = Goban.start(boardSize).success.value
  for move <- moves do
    move match
      case _: Pass =>
      case m: Move => goban = goban.setStone(m)
  goban

def checkStonesOnBoard(game: GoGame, moves: List[Move | Pass]): Unit =
  for move <- moves do
    move match
      case _: Pass =>
      case m: Move => Assertions.assertEquals(m.color, game.at(m.position))

def assertPositionsEqual(expected: Seq[(Int, Int, Int)], actual: Seq[Position]): Unit =
  def assertCollectionEqual[T](expected: Seq[T], actual: Seq[T]): Unit =
    Assertions.assertTrue(
      expected.sortBy(_.toString) == actual.sortBy(_.toString),
      s"$actual == $expected"
    )
  assertCollectionEqual(for (p <- expected) yield Position(p._1, p._2, p._3), actual)

def fromStrings(levels: Map[Int, String]): Goban =
  if levels.isEmpty then sys.error("nothing to generate")
  val lines = levels.view.mapValues(_.stripMargin.replace("|", "").split("\n")).toMap
  val size = lines.head._2.length
  Goban
    .tabulate(size)((x, y, z) =>
      // a point the strings leave out is empty: `stripMargin` empties a first line of blanks
      lines.get(z).flatMap(_.lift(y - 1)).flatMap(_.lift(x - 1))
        .fold(Empty)(Color(_).success.value)
    )
    .success
    .value

def fromGoban(goban: Goban): Game =
  Game(goban.size, goban, Array(), Map(), Komi.Default)

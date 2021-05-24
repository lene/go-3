package go3d.testing

import go3d._
import go3d.Color.{Black, White}
import org.junit.Assert

import scala.reflect.ClassTag

val TestSize = MinBoardSize

val CaptureMoves =
  Move(2, 2, 2, Black) :: Move(2, 2, 1, White) :: Move(2, 1, 1, Black) :: Move(2, 2, 3, White) ::
    Move(2, 3, 1, Black) :: Move(2, 1, 2, White) :: Move(3, 2, 1, Black) :: Move(2, 3, 2, White) ::
    Move(1, 2, 1, Black) :: Nil

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

def playListOfMoves(boardSize: Int, moves: List[Move | Pass], verbose: Boolean = false): Game =
  var game = newGame(boardSize)
  for move <- moves do
    game = game.makeMove(move)
    if verbose then println(move.toString+"\n"+game)
  game

def setListOfStones(boardSize: Int, moves: List[Move | Pass]): Goban =
  var goban = newGoban(boardSize)
  for move <- moves do
    move match
      case p: Pass =>
      case m: Move => goban = goban.setStone(m)
  goban

def checkStonesOnBoard(game: GoGame, moves: List[Move | Pass]): Unit =
  for move <- moves do
    move match
      case p: Pass =>
      case m: Move => Assert.assertEquals(m.color, game.at(m.position))

def assertCollectionEqual[T](expected: Seq[T], actual: Seq[T]): Unit =
  Assert.assertTrue(expected.sortBy(_.toString) == actual.sortBy(_.toString))

def assertPositionsEqual(expected: List[(Int, Int, Int)], actual: Seq[Position]): Unit =
  assertCollectionEqual(for (p <- expected) yield Position(p._1, p._2, p._3), actual)

def assertThrows[E](f: => Unit)(implicit eType:ClassTag[E]): Unit = {
  try f
  catch
    case e: E => return
    case e: _ => Assert.fail(s"Expected ${eType.runtimeClass.getName} got ${e.getClass}")
  Assert.fail(s"Expected ${eType.runtimeClass.getName}")
}

def fromStrings(levels: Map[Int, String]): Goban = {
  if levels.isEmpty then throw IllegalArgumentException("nothing to generate")
  val goban = newGoban((levels.head._2.stripMargin.replace("|", "").split("\n").length))
  for (z, level) <- levels do
    val lines = level.stripMargin.replace("|", "").split("\n")
    for (line, y) <- lines.zipWithIndex do
      for (stone, x) <- line.zipWithIndex do
        goban.stones(x+1)(y+1)(z) = colorFromChar(stone)
  return goban
}

def fromGoban(goban: Goban): Game =
  Game(goban.size, goban, Array(), Map())
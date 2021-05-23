package go3d.testing

import go3d._
import org.junit.Assert

import scala.reflect.ClassTag

val TestSize = MinBoardSize

val CaptureMoves =
  Move(2, 2, 2, Color.Black) :: Move(2, 2, 1, Color.White) ::
    Move(2, 1, 1, Color.Black) :: Move(2, 2, 3, Color.White) ::
    Move(2, 3, 1, Color.Black) :: Move(2, 1, 2, Color.White) ::
    Move(3, 2, 1, Color.Black) :: Move(2, 3, 2, Color.White) ::
    Move(1, 2, 1, Color.Black) :: Nil

def playListOfMoves(boardSize: Int, moves: List[Move | Pass], verbose: Boolean = false): Game =
  var game = newGame(boardSize)
  for move <- moves do
    game = game.makeMove(move)
    if verbose then println(move.toString+"\n"+game)
  game

def checkStonesOnBoard(game: Game, moves: List[Move | Pass]): Unit =
  for move <- moves do
    move match
      case p: Pass =>
      case m: Move => Assert.assertEquals(game.at(m.position), m.color)

def assertThrows[E](f: => Unit)(implicit eType:ClassTag[E]): Unit = {
  try f
  catch
    case e: E => return
    case e: _ => Assert.fail(s"Expected ${eType.runtimeClass.getName} got ${e.getClass}")
  Assert.fail(s"Expected ${eType.runtimeClass.getName}")
}
package go3d.testing

import go3d._
import org.junit.Assert

val TestSize = MinBoardSize

val CaptureMoves =
  (2, 2, 2, Color.Black) :: (2, 2, 1, Color.White) ::
    (2, 1, 1, Color.Black) :: (2, 2, 3, Color.White) ::
    (2, 3, 1, Color.Black) :: (2, 1, 2, Color.White) ::
    (3, 2, 1, Color.Black) :: (2, 3, 2, Color.White) ::
    (1, 2, 1, Color.Black) :: Nil

def playListOfMoves(boardSize: Int, moves: List[(Int, Int, Int, Color)]): Goban =
  var goban = Goban(boardSize)
  for move <- CaptureMoves do
    goban = goban.newBoard(
      Move(move._1.toInt, move._2.toInt, move._3.toInt, move._4.asInstanceOf[Color])
    )
  goban

def assertThrowsIllegalArgument(f: => Unit): Unit =
  try f
  catch
    case e: IllegalArgumentException => return
    case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
  Assert.fail("Expected IllegalArgumentException")

def assertThrowsIllegalMove(f: => Unit): Unit =
  try f
  catch
    case e: IllegalMove => return
    case e: _ => Assert.fail("Expected IllegalMove, got "+e.getClass)
  Assert.fail("Expected IllegalMove")

def assertThrowsGameOver(f: => Unit): Unit =
  try f
  catch
    case e: GameOver => return
    case e: _ => Assert.fail("Expected GameOver, got "+e.getClass)
  Assert.fail("Expected GameOver")


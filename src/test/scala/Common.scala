package go3d.testing

import go3d._
import org.junit.Assert

val TestSize = MinBoardSize

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


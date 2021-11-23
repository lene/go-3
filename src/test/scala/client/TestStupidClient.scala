package go3d.testing

import go3d.{BadColor, Position}
import go3d.client.*
import go3d.server.GoServer
import org.eclipse.jetty.server.Server
import org.junit.{After, Assert, Before, Test}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}


class TestStupidClient:

  @Test def testBadColor(): Unit =
    assertThrows[BadColor]({
      StupidClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    assertThrows[UnknownHostException]({
      StupidClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingArguments(): Unit =
    assertThrows[NoSuchElementException]({
      StupidClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      StupidClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      StupidClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      ))
    })
    assertThrows[IllegalArgumentException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      ))
    })

  def checkStrategyResults(
    strategy: List[Position] => List[Position],
    toCheck: List[(Int, Int, Int)], expected: List[(Int, Int, Int)]
  ): Unit =
    assertPositionsEqual(expected, strategy(toCheck.map(e => Position(e))))

  @Test def testClosestToCenterStrategy(): Unit =
    StupidClient.gameSize = 3
    val check = checkStrategyResults.curried(StupidClient.closestToCenter)
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))
    check(List((1, 1, 1),(1, 2, 2), (2, 1, 2), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))
    check(List((1, 1, 1), (1, 2, 2), (3, 3, 3)))(List((1, 2, 2)))
    check(List((1, 1, 1), (1, 2, 2), (2, 1, 2), (3, 3, 3)))(List((1, 2, 2), (2, 1, 2)))
    check(List((1, 1, 1), (1, 1, 2), (2, 1, 1), (3, 3, 3)))(List((1, 1, 2), (2, 1, 1)))
    val allCornerPoints = List((1, 1, 1), (1, 1, 3), (1, 3, 1), (1, 3, 3), (3, 1, 1), (3, 1, 3), (3, 3, 1), (3, 3, 3))
    check(allCornerPoints)(allCornerPoints)



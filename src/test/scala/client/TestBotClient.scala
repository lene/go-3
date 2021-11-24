package go3d.testing

import go3d.{BadColor, Position}
import go3d.client.*
import go3d.server.GoServer
import org.eclipse.jetty.server.Server
import org.junit.{After, Assert, Before, Ignore, Test}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}


class TestBotClient:

  @Test def testBadColor(): Unit =
    assertThrows[BadColor]({
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    assertThrows[UnknownHostException]({
      BotClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingArguments(): Unit =
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
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
  ): Unit = {
    val found = strategy(toCheck.map(e => Position(e)))
    assertPositionsEqual(expected, found)
  }

  @Test def testClosestToCenterStrategy(): Unit =
    BotClient.gameSize = 3
    val check = checkStrategyResults.curried(BotClient.closestToCenter)
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))
    check(
      List((1, 1, 1),(1, 2, 2), (2, 1, 2), (2, 2, 2), (3, 3, 3)))(
      List((2, 2, 2))
    )
    check(List((1, 1, 1), (1, 2, 2), (3, 3, 3)))(List((1, 2, 2)))
    check(
      List((1, 1, 1), (1, 2, 2), (2, 1, 2), (3, 3, 3)))(
      List((1, 2, 2), (2, 1, 2))
    )
    check(
      List((1, 1, 1), (1, 1, 2), (2, 1, 1), (3, 3, 3)))(
      List((1, 1, 2), (2, 1, 1))
    )
    val allCornerPoints = List((1, 1, 1), (1, 1, 3), (1, 3, 1), (1, 3, 3), (3, 1, 1), (3, 1, 3), (3, 3, 1), (3, 3, 3))
    check(allCornerPoints)(allCornerPoints)

  @Test def testClosestToStarPointsStrategyCornerStarPoints(): Unit =
    BotClient.gameSize = 3
    val check = checkStrategyResults.curried(BotClient.closestToStarPoints)
    check(
      List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(
      List((1, 1, 1), (3, 3, 3))
    )
    check(
      List((1, 1, 1), (1, 1, 3), (2, 2, 2), (3, 3, 1), (3, 3, 3)))(
      List((1, 1, 1), (1, 1, 3), (3, 3, 1), (3, 3, 3))
    )
    BotClient.gameSize = 7
    check(
      List((2, 2, 2), (4, 4, 4), (6, 6, 6)))(
      List((2, 2, 2), (6, 6, 6))
    )
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testClosestToStarPointsStrategyMidLines(): Unit =
    BotClient.gameSize = 7
    val check = checkStrategyResults.curried(BotClient.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 2), (7, 7, 7)))(List((2, 6, 2)))

  @Test def testClosestToStarPointsStrategyMidFaces(): Unit =
    BotClient.gameSize = 7
    val check = checkStrategyResults.curried(BotClient.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 6), (7, 7, 7)))(List((2, 6, 6)))

  @Test def testClosestToStarPointsStrategyCenter(): Unit =
    BotClient.gameSize = 7
    val check = checkStrategyResults.curried(BotClient.closestToStarPoints)
    check(List((1, 1, 1), (4, 4, 4), (7, 7, 7)))(List((4, 4, 4)))

  @Test def testClosestToStarPointsStrategyOffStarPoints(): Unit =
    BotClient.gameSize = 7
    val check = checkStrategyResults.curried(BotClient.closestToStarPoints)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((2, 2, 1)))

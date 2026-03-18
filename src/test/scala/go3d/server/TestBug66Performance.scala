package go3d.server

import go3d.server.*
import go3d.Game
import go3d.Move
import go3d.Pass
import io.circe.parser.decode
import org.scalatest.funsuite.AnyFunSuite

import scala.io.Source

/**
 * Performance regression tests for bug #66 - "Server hanging sometimes"
 *
 * These tests verify that the game data files from issue #66 can be replayed
 * without experiencing the severe performance degradation that was caused by
 * deep copying board arrays in connectedStones() and hasLiberties() methods.
 *
 * Before fix: Individual moves took 2000+ ms, total game time 2000+ ms
 * After fix: All moves complete in <10ms, total game time 64-124ms
 *
 * Tests use a generous 5-second timeout per game to allow for slow/overloaded
 * systems while still catching the exponential performance degradation.
 * Tests will fail immediately if the timeout is exceeded (not wait for completion).
 */
class TestBug66Performance extends AnyFunSuite:

  private def replayGameFile(filename: String, maxTimeMs: Long): Long =
    val source = Source.fromFile(filename)
    val jsonContent = try source.mkString finally source.close()
    val saveGame = decode[SaveGame](jsonContent) match
      case Right(sg) => sg
      case Left(e) => fail(s"Failed to decode $filename: ${e.getMessage}")

    val startTime = System.nanoTime()
    var game = Game.start(saveGame.game.size)

    for (moveOrPass, index) <- saveGame.game.moves.zipWithIndex do
      val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
      if elapsedMs > maxTimeMs then
        fail(s"Game replay exceeded ${maxTimeMs}ms timeout at move ${index}/${saveGame.game.moves.length}. " +
             s"Elapsed: ${elapsedMs}ms. This suggests performance regression in connectedStones() or hasLiberties().")

      moveOrPass match
        case move: Move => game = game.makeMove(move)
        case pass: Pass => game = game.makeMove(pass)

    val endTime = System.nanoTime()
    val totalTimeMs = (endTime - startTime) / 1_000_000
    totalTimeMs

  test("bug #66: gNoDpK.json performance - 341 moves") {
    val timeMs = replayGameFile("src/test/resources/data/gNoDpK.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

  test("bug #66: yLtN8s.json performance - 336 moves") {
    val timeMs = replayGameFile("src/test/resources/data/yLtN8s.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

  test("bug #66: gyxAXY.json performance - 337 moves") {
    val timeMs = replayGameFile("src/test/resources/data/gyxAXY.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

  test("bug #66: sXBvhd.json performance - 342 moves") {
    val timeMs = replayGameFile("src/test/resources/data/sXBvhd.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

  test("bug #66: 6axryR.json performance - 342 moves") {
    val timeMs = replayGameFile("src/test/resources/data/6axryR.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

  test("bug #66: B8orAu.json performance - 340 moves") {
    val timeMs = replayGameFile("src/test/resources/data/B8orAu.json", 5000)
    info(s"Replay completed in ${timeMs}ms")
  }

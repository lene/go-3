package go3d.client

import org.junit.jupiter.api.{Assertions, Test}
import go3d.*
import go3d.server.{*, given}
import io.circe.parser.decode
import scala.io.Source

/**
 * Test for bug #77: bot crashes with "empty.minBy" in prioritiseCapture strategy
 *
 * The bug occurs when:
 * 1. A bot uses the prioritiseCapture strategy
 * 2. After placing a stone, all opponent stones are captured
 * 3. minLiberties is called with a game where opponent has no areas
 * 4. minBy is called on an empty list
 */
class TestBug77:

  @org.junit.jupiter.api.Disabled(
    """Takes 20+ minutes. Run manually with:
       sbt 'set Test / testOptions += Tests.Argument(jupiterTestFramework, "-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition")' 'testOnly go3d.client.TestBug77'"""
  )
  @Test def testReplayGameFromJson(): Unit =
    // Load the game state from the JSON file that triggered the bug
    // Even if the game is over, replaying it should not crash
    System.err.println("Starting testReplayGameFromJson - loading JSON file...")
    System.err.flush()

    val source = Source.fromFile("src/test/resources/data/rYcmnR.json")
    val jsonContent = try source.mkString finally source.close()
    val saveGameResult = decode[go3d.server.SaveGame](jsonContent)

    Assertions.assertTrue(saveGameResult.isRight, s"Failed to decode JSON: $saveGameResult")

    val saveGame = saveGameResult.toOption.get
    System.err.println(s"Loaded game with ${saveGame.game.moves.length} moves")
    System.err.flush()

    // Replay the game move by move, testing the strategy at each step
    var game = Game.start(7)
    val strategy = SetStrategy(7, Array("prioritiseCapture", "minimizeOpponentLiberties", "closestToStarPoints"))

    for (moveOrPass, index) <- saveGame.game.moves.zipWithIndex do
      if index % 10 == 0 then
        System.err.println(s"Processing move $index/${saveGame.game.moves.length}")
        System.err.flush()

      moveOrPass match
        case move: Move =>
          val color = move.color
          val possibleMoves = game.possibleMoves(color)

          // If there are possible moves, the strategy should not crash
          if possibleMoves.nonEmpty then
            try
              val t0 = System.currentTimeMillis()
              val result = strategy.narrowDown(possibleMoves, game)
              val elapsed = System.currentTimeMillis() - t0
              if elapsed > 100 then
                System.err.println(s"Move $index took ${elapsed}ms (${possibleMoves.length} possible moves)")
                System.err.flush()
              // After the fix, this should always succeed
              Assertions.assertTrue(result.nonEmpty || possibleMoves.isEmpty,
                s"Strategy should return moves or have no moves at move $index")
            catch
              case e: UnsupportedOperationException if e.getMessage.contains("empty.minBy") =>
                System.err.println(s"Bug #77 reproduced at move $index!")
                System.err.flush()
                Assertions.fail(s"Bug #77 reproduced at move $index: ${e.getMessage}")

          game = game.makeMove(move)
        case pass: Pass =>
          game = game.makeMove(pass)

  @Test def testMinLibertiesWithEmptyAreas(): Unit =
    // Create a simple test case: Black has one stone in corner (1,1,1) which has only 3 neighbors
    // White surrounds it completely, capturing it
    // This triggers the bug: when evaluating moves, minLiberties is called on a game where one color has no areas

    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))  // Black at corner (has 3 neighbors: 2,1,1 and 1,2,1 and 1,1,2)
      .makeMove(Move(2, 1, 1, White))  // White blocks one liberty
      .makeMove(Move(2, 2, 2, Black))  // Black plays elsewhere
      .makeMove(Move(1, 2, 1, White))  // White blocks second liberty
      .makeMove(Pass(Black))
      .makeMove(Move(1, 1, 2, White))  // White captures Black's corner stone
      // Now Black's corner stone is captured. Black still has a stone at (2,2,2)

    // The key test: when it's Black's turn and the strategy evaluates possible moves,
    // if Black can capture White's group entirely, minLiberties would be called with White having no areas
    val strategy = SetStrategy(3, Array("prioritiseCapture"))
    val possibleMoves = game.possibleMoves(Black)
    Assertions.assertTrue(possibleMoves.nonEmpty, "Should have possible moves for Black")

    // This should not crash with "empty.minBy" after the fix
    val result = strategy.narrowDown(possibleMoves, game)

    // After the fix, it should return valid moves
    Assertions.assertTrue(result.nonEmpty, "Strategy should return at least one move")
    Assertions.assertTrue(result.forall(possibleMoves.contains), "All returned moves should be valid")

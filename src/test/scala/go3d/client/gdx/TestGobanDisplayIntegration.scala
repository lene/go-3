package go3d.client.gdx

import go3d.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestGobanDisplayIntegration:

  @Test def testCursorPositionsEmptyGame(): Unit =
    val game = Game.start(3)

    val ownMove = game.playerLastMove(Some(Black))
    val opponentMove = game.playerLastMove(Some(Black).map(!_))

    assertEquals(None, ownMove)
    assertEquals(None, opponentMove)

  @Test def testBlackClientAfterFirstMove(): Unit =
    val game = Game.start(3).makeMove(Move(1,1,1, Black))

    val ownMove = game.playerLastMove(Some(Black))
    val opponentMove = game.playerLastMove(Some(Black).map(!_))

    assertEquals(Some(Position(1,1,1)), ownMove)
    assertEquals(None, opponentMove)

  @Test def testWhiteClientAfterTwoMoves(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(3,3,3, White))

    val ownMove = game.playerLastMove(Some(White))
    val opponentMove = game.playerLastMove(Some(White).map(!_))

    assertEquals(Some(Position(3,3,3)), ownMove)
    assertEquals(Some(Position(1,1,1)), opponentMove)

  @Test def testBlackClientAfterTwoMoves(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(3,3,3, White))

    val ownMove = game.playerLastMove(Some(Black))
    val opponentMove = game.playerLastMove(Some(Black).map(!_))

    assertEquals(Some(Position(1,1,1)), ownMove)
    assertEquals(Some(Position(3,3,3)), opponentMove)

  @Test def testCursorPositionsAfterCapture(): Unit =
    // Create a situation where a stone is captured
    val game = Game.start(3)
      .makeMove(Move(2,2,2, Black))
      .makeMove(Move(1,2,2, White))
      .makeMove(Move(3,2,2, Black))
      .makeMove(Move(2,1,2, White))
      .makeMove(Move(2,3,2, Black))
      .makeMove(Move(2,2,1, White))
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(2,2,3, White))  // captures Black at (2,2,2)

    val whiteOwnMove = game.playerLastMove(Some(White))
    val whiteOpponentMove = game.playerLastMove(Some(White).map(!_))

    // Cursors should show last moves, not captured positions
    assertEquals(Some(Position(2,2,3)), whiteOwnMove)
    assertEquals(Some(Position(1,1,1)), whiteOpponentMove)

  @Test def testCursorPositionsWithPasses(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Pass(White))
      .makeMove(Move(2,2,2, Black))
      .makeMove(Pass(White))

    val blackOwnMove = game.playerLastMove(Some(Black))
    val blackOpponentMove = game.playerLastMove(Some(Black).map(!_))

    // Should show last actual moves, ignoring passes
    assertEquals(Some(Position(2,2,2)), blackOwnMove)
    assertEquals(None, blackOpponentMove)  // White only passed, never placed stone

  @Test def testCursorPositionsWithPassesBothColors(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(3,3,3, White))
      .makeMove(Pass(Black))
      .makeMove(Move(2,2,2, White))
      .makeMove(Move(1,2,1, Black))  // Black plays another move after White

    val blackOwnMove = game.playerLastMove(Some(Black))
    val blackOpponentMove = game.playerLastMove(Some(Black).map(!_))

    // Should show last actual moves
    assertEquals(Some(Position(1,2,1)), blackOwnMove)
    assertEquals(Some(Position(2,2,2)), blackOpponentMove)

  @Test def testWatchOnlyClientHasNoCursors(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(2,2,2, White))

    val ownMove = game.playerLastMove(None)
    val opponentMove = game.playerLastMove(None)

    assertEquals(None, ownMove)
    assertEquals(None, opponentMove)

  @Test def testCursorSwitchingBlackPerspective(): Unit =
    val game = Game.start(5)
      .makeMove(Move(3,3,3, Black))
      .makeMove(Move(4,4,4, White))
      .makeMove(Move(2,2,2, Black))
      .makeMove(Move(5,5,5, White))

    // Black player's view
    val blackOwnMove = game.playerLastMove(Some(Black))
    val blackOpponentMove = game.playerLastMove(Some(Black).map(!_))

    // Green cursor (own) should be at Black's last move
    assertEquals(Some(Position(2,2,2)), blackOwnMove)
    // Red cursor (opponent) should be at White's last move
    assertEquals(Some(Position(5,5,5)), blackOpponentMove)

  @Test def testCursorSwitchingWhitePerspective(): Unit =
    val game = Game.start(5)
      .makeMove(Move(3,3,3, Black))
      .makeMove(Move(4,4,4, White))
      .makeMove(Move(2,2,2, Black))
      .makeMove(Move(5,5,5, White))

    // White player's view
    val whiteOwnMove = game.playerLastMove(Some(White))
    val whiteOpponentMove = game.playerLastMove(Some(White).map(!_))

    // Green cursor (own) should be at White's last move
    assertEquals(Some(Position(5,5,5)), whiteOwnMove)
    // Red cursor (opponent) should be at Black's last move
    assertEquals(Some(Position(2,2,2)), whiteOpponentMove)

  @Test def testMultiMoveSequenceBlackPerspective(): Unit =
    // Simulate a longer game from Black's perspective
    val game = Game.start(5)
      .makeMove(Move(3,3,3, Black))  // Black #1
      .makeMove(Move(4,4,4, White))  // White #1
      .makeMove(Move(2,2,2, Black))  // Black #2
      .makeMove(Move(5,5,5, White))  // White #2
      .makeMove(Move(1,1,1, Black))  // Black #3
      .makeMove(Move(1,2,1, White))  // White #3
      .makeMove(Move(2,3,2, Black))  // Black #4
      .makeMove(Move(3,4,3, White))  // White #4

    val blackOwnMove = game.playerLastMove(Some(Black))
    val blackOpponentMove = game.playerLastMove(Some(Black).map(!_))

    // After 8 moves, Black's last move is #4, White's last move is #4
    assertEquals(Some(Position(2,3,2)), blackOwnMove)
    assertEquals(Some(Position(3,4,3)), blackOpponentMove)

  @Test def testMultiMoveSequenceWhitePerspective(): Unit =
    // Simulate a longer game from White's perspective
    val game = Game.start(5)
      .makeMove(Move(3,3,3, Black))  // Black #1
      .makeMove(Move(4,4,4, White))  // White #1
      .makeMove(Move(2,2,2, Black))  // Black #2
      .makeMove(Move(5,5,5, White))  // White #2
      .makeMove(Move(1,1,1, Black))  // Black #3
      .makeMove(Move(1,2,1, White))  // White #3
      .makeMove(Move(2,3,2, Black))  // Black #4
      .makeMove(Move(3,4,3, White))  // White #4

    val whiteOwnMove = game.playerLastMove(Some(White))
    val whiteOpponentMove = game.playerLastMove(Some(White).map(!_))

    // After 8 moves, White's last move is #4, Black's last move is #4
    assertEquals(Some(Position(3,4,3)), whiteOwnMove)
    assertEquals(Some(Position(2,3,2)), whiteOpponentMove)

  @Test def testCursorUpdateAfterBlackMove(): Unit =
    val game1 = Game.start(3)
      .makeMove(Move(1,1,1, Black))

    val blackOwnMove1 = game1.playerLastMove(Some(Black))
    assertEquals(Some(Position(1,1,1)), blackOwnMove1)

    // Black makes another move
    val game2 = game1
      .makeMove(Move(2,2,2, White))
      .makeMove(Move(3,3,3, Black))

    val blackOwnMove2 = game2.playerLastMove(Some(Black))
    // Cursor should update to new move
    assertEquals(Some(Position(3,3,3)), blackOwnMove2)

  @Test def testNoCursorLeakageBetweenPlayers(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1,1,1, Black))
      .makeMove(Move(2,2,2, White))

    // Black's cursors
    val blackOwn = game.playerLastMove(Some(Black))
    val blackOpponent = game.playerLastMove(Some(Black).map(!_))

    // White's cursors
    val whiteOwn = game.playerLastMove(Some(White))
    val whiteOpponent = game.playerLastMove(Some(White).map(!_))

    // Verify no position leakage
    assertEquals(Some(Position(1,1,1)), blackOwn)
    assertEquals(Some(Position(2,2,2)), blackOpponent)
    assertEquals(Some(Position(2,2,2)), whiteOwn)
    assertEquals(Some(Position(1,1,1)), whiteOpponent)

    // Verify they're opposite
    assertEquals(blackOwn, whiteOpponent)
    assertEquals(blackOpponent, whiteOwn)

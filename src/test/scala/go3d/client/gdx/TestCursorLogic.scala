package go3d.client.gdx

import go3d.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.scalatest.TryValues.*

class TestCursorLogic:

  @Test def testPlayerLastMoveNoPlayerColor(): Unit =
    val game = Game.start(3).success.value.makeMove(Move(1, 1, 1, Black)).success.value
    assertEquals(None, game.playerLastMove(None))

  @Test def testPlayerLastMoveNoMoves(): Unit =
    val game = Game.start(3).success.value
    assertEquals(None, game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveBlackOneMove(): Unit =
    val game = Game.start(3).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveWhiteOneMove(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
      .makeMove(Move(3, 3, 3, White)).success.value
    assertEquals(Some(Position(3, 3, 3)), game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveBlackTwoMoves(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Move(3, 3, 3, White)).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveWhiteTwoMoves(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Move(3, 3, 3, White)).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
      .makeMove(Move(3, 2, 2, White)).success.value
    assertEquals(Some(Position(3, 2, 2)), game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveWithPass(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Pass(White)).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))
    assertEquals(None, game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveOpponentPerspectiveNoPlayerColor(): Unit =
    val game = Game.start(3).success.value.makeMove(Move(1, 1, 1, Black)).success.value
    assertEquals(None, game.playerLastMove(None))

  @Test def testPlayerLastMoveOpponentPerspectiveNoMoves(): Unit =
    val game = Game.start(3).success.value
    assertEquals(None, game.playerLastMove(Some(!Black)))

  @Test def testPlayerLastMoveBlackPerspectiveForOpponent(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Move(2, 2, 2, White)).success.value
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!Black)))

  @Test def testPlayerLastMoveWhitePerspectiveForOpponent(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Move(2, 2, 2, White)).success.value
    assertEquals(Some(Position(1, 1, 1)), game.playerLastMove(Some(!White)))

  @Test def testPlayerLastMoveMultipleMovesOpponentPerspective(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Move(3, 3, 3, White)).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
      .makeMove(Move(3, 2, 2, White)).success.value
    assertEquals(Some(Position(3, 2, 2)), game.playerLastMove(Some(!Black)))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!White)))

  @Test def testPlayerLastMoveWithPassOpponentPerspective(): Unit =
    val game = Game.start(3).success.value
      .makeMove(Move(1, 1, 1, Black)).success.value
      .makeMove(Pass(White)).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
    assertEquals(None, game.playerLastMove(Some(!Black)))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!White)))

package go3d.client.gdx

import go3d.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestCursorLogic:

  @Test def testPlayerLastMoveNoPlayerColor(): Unit =
    val game = Game.start(3).makeMove(Move(1, 1, 1, Black))
    assertEquals(None, game.playerLastMove(None))

  @Test def testPlayerLastMoveNoMoves(): Unit =
    val game = Game.start(3)
    assertEquals(None, game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveBlackOneMove(): Unit =
    val game = Game.start(3).makeMove(Move(2, 2, 2, Black))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveWhiteOneMove(): Unit =
    val game = Game.start(3).makeMove(Move(2, 2, 2, Black)).makeMove(Move(3, 3, 3, White))
    assertEquals(Some(Position(3, 3, 3)), game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveBlackTwoMoves(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))
      .makeMove(Move(3, 3, 3, White))
      .makeMove(Move(2, 2, 2, Black))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))

  @Test def testPlayerLastMoveWhiteTwoMoves(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))
      .makeMove(Move(3, 3, 3, White))
      .makeMove(Move(2, 2, 2, Black))
      .makeMove(Move(3, 2, 2, White))
    assertEquals(Some(Position(3, 2, 2)), game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveWithPass(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))
      .makeMove(Pass(White))
      .makeMove(Move(2, 2, 2, Black))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(Black)))
    assertEquals(None, game.playerLastMove(Some(White)))

  @Test def testPlayerLastMoveOpponentPerspectiveNoPlayerColor(): Unit =
    val game = Game.start(3).makeMove(Move(1, 1, 1, Black))
    assertEquals(None, game.playerLastMove(None))

  @Test def testPlayerLastMoveOpponentPerspectiveNoMoves(): Unit =
    val game = Game.start(3)
    assertEquals(None, game.playerLastMove(Some(!Black)))

  @Test def testPlayerLastMoveBlackPerspectiveForOpponent(): Unit =
    val game = Game.start(3).makeMove(Move(1, 1, 1, Black)).makeMove(Move(2, 2, 2, White))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!Black)))

  @Test def testPlayerLastMoveWhitePerspectiveForOpponent(): Unit =
    val game = Game.start(3).makeMove(Move(1, 1, 1, Black)).makeMove(Move(2, 2, 2, White))
    assertEquals(Some(Position(1, 1, 1)), game.playerLastMove(Some(!White)))

  @Test def testPlayerLastMoveMultipleMovesOpponentPerspective(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))
      .makeMove(Move(3, 3, 3, White))
      .makeMove(Move(2, 2, 2, Black))
      .makeMove(Move(3, 2, 2, White))
    assertEquals(Some(Position(3, 2, 2)), game.playerLastMove(Some(!Black)))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!White)))

  @Test def testPlayerLastMoveWithPassOpponentPerspective(): Unit =
    val game = Game.start(3)
      .makeMove(Move(1, 1, 1, Black))
      .makeMove(Pass(White))
      .makeMove(Move(2, 2, 2, Black))
    assertEquals(None, game.playerLastMove(Some(!Black)))
    assertEquals(Some(Position(2, 2, 2)), game.playerLastMove(Some(!White)))

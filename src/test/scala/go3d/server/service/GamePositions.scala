package go3d.server.service

/** Move sequences, black first and alternating, after which one more move is illegal. */
object GamePositions:
  type Position = (Int, Int, Int)

  /** On a board of size 3 white surrounds the corner 1,1,1: black playing there is suicide. */
  val BeforeSuicide: List[Position] =
    List((3, 3, 3), (2, 1, 1), (3, 3, 2), (1, 2, 1), (3, 2, 3), (1, 1, 2))

  /** The position of `TestGame.testPossibleMovesWithKo` on a board of size 5: white's last move
    * captures 2,2,3, so black playing there again is a ko.
    */
  val BeforeKo: List[Position] = List(
    (2, 2, 3),
    (2, 2, 4),
    (2, 3, 2),
    (2, 3, 3),
    (2, 1, 2),
    (2, 1, 3),
    (3, 2, 2),
    (3, 2, 3),
    (1, 2, 2),
    (1, 2, 3),
    (2, 2, 1),
    (2, 2, 2)
  )

  /** The positions with the token of the player who makes each move. */
  def withTokens(
      black: String,
      white: String,
      positions: List[Position]
  ): List[(String, Position)] =
    positions.zipWithIndex.map((position, i) => (if i % 2 == 0 then black else white, position))

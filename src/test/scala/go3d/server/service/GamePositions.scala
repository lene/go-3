package go3d.server.service

/** Move sequences, black first and alternating, after which one more move is illegal. */
object GamePositions:
  type Position = (Int, Int, Int)

  /** On a board of size 3 white surrounds the corner 1,1,1: black playing there is suicide. */
  val BeforeSuicide: List[Position] =
    List((3, 3, 3), (2, 1, 1), (3, 3, 2), (1, 2, 1), (3, 2, 3), (1, 1, 2))

  /** Black, white, black on a board of size 3, none of them capturing. White then passes and black
    * passes: black has 2 points and white 1, so the winner is black with a komi below 1 and white
    * with a larger one.
    */
  val BlackLeadsByOne: List[Position] = List((2, 2, 2), (1, 1, 1), (3, 3, 3))

  /** No stones: black passes and white passes, 0 points to 0, so the sign of the komi decides
    * the winner, black with -0.5 and white with 0.5.
    */
  val EmptyBoard: List[Position] = List()

  /** The position of `TestGame.testPossibleMovesWithKo` on a board of size 5: white's last move
    * captures 2,2,3, so black playing there again is a ko. Black has 5 stones and no territory.
    * White has 6 stones, one capture and the captured point 2,2,3 as territory, so the score is 5
    * to 7. With the capture counted it would be 5 to 8, so a komi of -2.5 gives the winner black,
    * and white if captures were counted; a komi of -1.5 gives white either way.
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

  /** The smallest odd board size, at least 3, on which all of the positions exist. */
  def boardSize(positions: List[Position]): Int =
    val largest = positions.flatMap { case (x, y, z) => List(x, y, z) }.maxOption.getOrElse(0)
    val atLeastMinimum = largest.max(3)
    if atLeastMinimum % 2 == 0 then atLeastMinimum + 1 else atLeastMinimum

  /** The positions with the token of the player who makes each move. */
  def withTokens(
      black: String,
      white: String,
      positions: List[Position]
  ): List[(String, Position)] =
    positions.zipWithIndex.map((position, i) => (if i % 2 == 0 then black else white, position))

  /** The tokens of the two players who end the game by passing after `positions`, in order. */
  def passingTokens(black: String, white: String, positions: List[Position]): List[String] =
    if positions.length % 2 == 0 then List(black, white) else List(white, black)

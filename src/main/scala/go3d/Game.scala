package go3d

import collection.mutable
import scala.util.{Failure, Success, Try}

object Game:
  def start(size: Int): Try[Game] = start(size, Komi.Default)

  /** A game on an empty board of `size`; fails for an invalid size, then for an invalid komi. */
  def start(size: Int, komi: Double): Try[Game] =
    for
      goban <- Goban.start(size)
      validKomi <- Komi.validate(komi, size)
    yield Game(size, goban, Array(), Map[Int, Array[Move]](), validKomi)

class Game(
    val size: Int,
    val goban: Goban,
    val moves: Array[Move | Pass],
    val captures: Map[Int, Array[Move]],
    val komi: Double
) extends GoGame:

  /** The stones that `color` captured; an empty capture array, as a decoded game can have, has
    * none.
    */
  def captures(color: Color): Int =
    captures.values.filter(_.headOption.exists(_.color != color)).flatten.size

  /** The stones that the last move captured. `captures` is keyed by the index of the capturing
    * move, and a map has no order to take the "last" entry from.
    */
  def lastCapture: Array[Move] = captures.getOrElse(moves.length - 1, Array[Move]())

  override def equals(obj: Any): Boolean =
    obj match
      case g: Game => goban == g.goban && toString == g.toString && komi == g.komi
      case _ => false

  def at(pos: Position): Color = goban.at(pos)
  def at(x: Int, y: Int, z: Int): Color = at(new Position(x, y, z))

  def isOver: Boolean =
    moves.length >= size * size * size || (
      moves.length >= 2 &&
        ((moves.last, moves(moves.length - 2)) match
          case (_: Pass, _: Pass) => true
          case _ => false)
    )

  /** The game after its first `count` moves, as they were played: the moves are not checked again,
    * so a game recorded under earlier rules replays as it was played.
    */
  def atMove(count: Int): Try[Game] =
    Game.start(size, komi).map(start => moves.take(count).foldLeft(start)(_.replayed(_)))

  def isTurn(color: Color): Boolean =
    if moves.isEmpty then color == Black else color != moves.last.color

  def makeMove(move: Move | Pass): Try[Game] =
    move match
      case _: Pass => Success(replayed(move))
      case m: Move => checkValid(m).map(_ => replayed(m))

  /** The game after `move`, without checking that the move is legal. */
  private[go3d] def replayed(move: Move | Pass): Game =
    move match
      case _: Pass => Game(size, goban, moves.appended(move), captures, komi)
      case m: Move =>
        val newboard = setStone(m)
        Game(size, newboard.goban, moves.appended(move), newboard.captures, komi)

  def checkValid(move: Move): Try[Unit] =
    if !isDifferentPlayer(move.color) then Failure(WrongTurn(move))
    else
      goban.checkValid(move).flatMap { _ =>
        if isKo(move) then Failure(Ko(move)) else Success(())
      }

  override def toString: String =
    var out = "\n"
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += goban.at(x, y, z).toString
        if z < size then out += "|"
        else if y == 1 then
          out += " " + White.toString * captures(Black) // Black captures White stones
        else if y == 3 then
          out += " " + Black.toString * captures(White) // White captures Black stones
      out += "\n"
    for (move, caps) <- captures.toSeq.sortBy(x => x._1) do out += s"$move: ${caps.toList}\n"
    out += score.toString
    out

  def setStone(move: Move): Game = doCaptures(move, goban.setStone(move))

  def hasLiberties(move: Move): Boolean = goban.hasLiberties(move)

  def connectedStones(move: Move): Set[Move] = goban.connectedStones(move)

  def possibleMoves(color: Color): List[Position] =
    if !isDifferentPlayer(color) || moves.length >= size * size * size then List()
    else if moves.isEmpty && color == White then List()
    else if moves.isEmpty && color == Black then goban.allPositions.toList
    else goban.emptyPositions.toList.filter(isPossibleMove(_, color))

  def moveColor: Color = if moves.isEmpty then Black else !moves.last.color

  /** The score of each player: the stones on the board plus the empty areas that only that
    * player's stones border (Tromp-Taylor area scoring).
    *
    * Captures are not added. Under area scoring the points freed by a capture count as territory
    * when only the capturer's stones border them, so adding the captures would count them twice.
    * That reasoning comes from 2D Go and may not hold in 3D, where adding captures back may be
    * needed for more balanced and interesting games.
    *
    * Dead stones are never removed from the board, so a game has to be played until dead groups
    * are captured, or they count for their owner.
    */
  def score: Map[Color, Int] =
    val scores = mutable.Map[Color, Int](Black -> 0, White -> 0)
    for color <- Seq(Black, White) do
      for pos <- goban.allPositions if at(pos) == color do scores(color) = scores(color) + 1
    for area <- emptyAreas do
      boundaryColor(area) match
        case Some(color) => scores(color) = scores(color) + area.size
        case None =>
    scores.toMap

  /** The player with the higher score once the komi is added to White's, or None while the game is
    * not over. The komi is a half-integer and the scores are integers, so there is no tie.
    */
  def winner: Option[Color] =
    if !isOver then None
    else
      val scores = score
      if scores(White) + komi > scores(Black) then Some(White) else Some(Black)

  private def boundaryColor(area: Set[Move]): Option[Color] =
    val boundaryColors = mutable.Set[Color]()
    for stone <- area do
      for neighbor <- goban.neighbors(stone.position) if at(neighbor) != stone.color do
        boundaryColors.add(at(neighbor))
    if boundaryColors.size == 1 then Some(boundaryColors.head) else None

  /** The connected empty regions of the board, each flood-filled once. */
  private def emptyAreas: Set[Set[Move]] =
    val (areas, _) = goban.emptyPositions.foldLeft((Set[Set[Move]](), Set[Position]())) {
      case ((areas, covered), position) =>
        if covered.contains(position) then (areas, covered)
        else
          val area = connectedStones(Move(position, Empty))
          (areas + area, covered ++ area.map(_.position))
    }
    areas

  /** A point with an empty neighbour is never suicide, but it can still be ko. */
  private def isPossibleMove(emptyPos: Position, color: Color): Boolean =
    if !goban.hasEmptyNeighbor(emptyPos) then checkValid(Move(emptyPos, color)).isSuccess
    else !isKo(Move(emptyPos, color))

  private def isDifferentPlayer(color: Color): Boolean = moves.isEmpty || moves.last.color != color

  /** Ko: the last move captured a single stone, and `move` puts it back by capturing only the
    * stone that captured it, which would repeat the position. A retake that captures more is legal.
    */
  private def isKo(move: Move): Boolean =
    lastCapture.length == 1 && lastCapture(0) == move &&
      setStone(move).lastCapture.toList == moves.lastOption.toList

  private def doCaptures(move: Move, board: Goban): Game =
    val newBoard = captureNeighbors(board, board.neighbors(move.position), move.color)
    val newMoves = moves.appended(move)
    val captured = (board - newBoard).toArray
    val newCaptures = if captured.nonEmpty then captures + (moves.length -> captured) else captures
    Game(size, newBoard, newMoves, newCaptures, komi)

  private def captureNeighbors(board: Goban, neighbors: Seq[Position], color: Color): Goban =
    if neighbors.isEmpty then board
    else
      val newBoard = board.checkAndClear(Move(neighbors.last, color))
      captureNeighbors(newBoard, neighbors.dropRight(1), color)

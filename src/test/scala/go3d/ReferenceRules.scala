package go3d

import scala.annotation.tailrec

/** A second, deliberately plain implementation of the rules, for [[TestRulesAgainstReference]]:
  * the stones in a map, and every group found again by flood fill. It is written from the rules,
  * not from [[Game]] and [[Goban]], so a change there that alters a rule shows up as a difference.
  *
  * The rules: Black moves first and the players alternate, passes included. A stone goes on an
  * empty point and captures the opponent groups next to it that have no liberty left. A move that
  * leaves its own group without a liberty is suicide and illegal. Ko: when the last move captured a
  * single stone, putting it back is illegal if it would capture only the stone that captured it.
  * The game is over after two passes in a row or one move per point. The score is the stones on the
  * board plus the empty regions that stones of only one colour border.
  */
final case class ReferenceRules(
    size: Int,
    stones: Map[Position, Color],
    moves: Vector[Move | Pass],
    lastCaptured: Set[Position]
):

  def at(position: Position): Color = stones.getOrElse(position, Empty)

  def toMove: Color = moves.lastOption.fold(Black)(move => !move.color)

  def isOver: Boolean =
    moves.length >= size * size * size ||
      (moves.takeRight(2) match
        case Vector(_: Pass, _: Pass) => true
        case _ => false)

  def pass: ReferenceRules = copy(moves = moves :+ Pass(toMove), lastCaptured = Set())

  /** The game after the player to move puts a stone on `position`, or why that is illegal. */
  def play(position: Position): Either[IllegalMove, ReferenceRules] =
    val move = Move(position, toMove)
    if at(position) != Empty then Left(PositionOccupied(move, at(position)))
    else
      val placed = stones.updated(position, move.color)
      val captured = neighbors(position)
        .filter(at(placed, _) == !move.color)
        .map(group(placed, _))
        .filterNot(hasLiberty(placed, _))
        .flatten
        .toSet
      val board = placed -- captured
      val lastStone = moves.lastOption.flatMap(_.optionalPosition).toSet
      if !hasLiberty(board, group(board, position)) then Left(Suicide(move))
      else if lastCaptured == Set(position) && captured == lastStone then Left(Ko(move))
      else Right(ReferenceRules(size, board, moves :+ move, captured))

  def score: Map[Color, Int] =
    val territory = emptyRegions.flatMap { region =>
      val border = region.flatMap(neighbors).map(at).filter(_ != Empty)
      if border.size == 1 then List(border.head -> region.size) else Nil
    }
    List(Black, White).map { color =>
      color -> (stones.count(_._2 == color) + territory.filter(_._1 == color).map(_._2).sum)
    }.toMap

  /** Every point of the board, computed once for each state that needs it. */
  lazy val points: Seq[Position] =
    for x <- 1 to size; y <- 1 to size; z <- 1 to size yield Position(x, y, z)

  private def emptyRegions: List[Set[Position]] =
    points.filter(at(_) == Empty).foldLeft(List[Set[Position]]()) { (regions, position) =>
      if regions.exists(_.contains(position)) then regions else group(stones, position) :: regions
    }

  private def at(board: Map[Position, Color], position: Position): Color =
    board.getOrElse(position, Empty)

  /** The points connected to `start` through points of its colour, empty points included. */
  private def group(board: Map[Position, Color], start: Position): Set[Position] =
    val color = at(board, start)
    @tailrec
    def fill(frontier: List[Position], found: Set[Position]): Set[Position] =
      frontier match
        case Nil => found
        case position :: rest =>
          val next = neighbors(position).filter(n => at(board, n) == color && !found(n))
          fill(next.toList ++ rest, found ++ next)
    fill(List(start), Set(start))

  private def hasLiberty(board: Map[Position, Color], points: Set[Position]): Boolean =
    points.exists(neighbors(_).exists(at(board, _) == Empty))

  private def neighbors(position: Position): Seq[Position] =
    ReferenceRules.Directions
      .map((dx, dy, dz) => (position.x + dx, position.y + dy, position.z + dz))
      .collect { case (x, y, z) if onBoard(x, y, z, size) => Position(x, y, z) }

object ReferenceRules:
  def start(size: Int): ReferenceRules = ReferenceRules(size, Map(), Vector(), Set())

  private val Directions =
    List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

package go3d

import scala.annotation.tailrec
import scala.annotation.targetName
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Goban:
  def start(size: Int): Try[Goban] =
    if size < MinBoardSize then Failure(BadBoardSize(size, "too small"))
    else if size > MaxBoardSize then Failure(BadBoardSize(size, "too big"))
    else if size % 2 == 0 then Failure(BadBoardSize(size, "even"))
    else Success(new Goban(size, board(size, (_, _, _) => Empty)))

  /** A board of `size` with the colour of `stoneAt` on each point from 1 to `size`; fails for an
    * invalid size or if `stoneAt` throws.
    */
  def tabulate(size: Int)(stoneAt: (Int, Int, Int) => Color): Try[Goban] =
    start(size).map(_ => new Goban(size, board(size, stoneAt)))

  private def board(size: Int, stoneAt: (Int, Int, Int) => Color): IArray[IArray[IArray[Color]]] =
    IArray.tabulate(size + 2, size + 2, size + 2)((x, y, z) =>
      if onBoard(x, y, z, size) then stoneAt(x, y, z) else Sentinel
    )

  // The points and the neighbours of each point depend only on the size of the board, so they
  // are computed once per size instead of once per board, which every move copies.
  private val positionsBySize = TrieMap[Int, IndexedSeq[Position]]()
  private val neighborsBySize = TrieMap[Int, Map[Position, IndexedSeq[Position]]]()

  private[go3d] def positions(size: Int): IndexedSeq[Position] =
    positionsBySize.getOrElseUpdate(
      size,
      for (x <- 1 to size; y <- 1 to size; z <- 1 to size) yield new Position(x, y, z)
    )

  private[go3d] def neighbors(size: Int, position: Position): IndexedSeq[Position] =
    neighborsBySize
      .getOrElseUpdate(size, positions(size).map(p => p -> neighborsOf(size, p)).toMap)
      .getOrElse(position, neighborsOf(size, position))

  private def neighborsOf(size: Int, position: Position): IndexedSeq[Position] =
    for (
      x <- position.x - 1 to position.x + 1;
      y <- position.y - 1 to position.y + 1;
      z <- position.z - 1 to position.z + 1
      if onBoard(x, y, z, size) && (position - new Position(x, y, z)).abs == 1
    ) yield new Position(x, y, z)

/** A board: the stones of each point, indexed by x, then y, then z, with a border of [[Sentinel]]
  * at 0 and `size + 1`. It cannot be changed: [[setStone]] returns a new board, which shares the
  * rows it does not change with this one.
  */
class Goban private (val size: Int, val stones: IArray[IArray[IArray[Color]]]) extends GoGame:

  lazy val areas: Set[Area] = calculateAreas()
  def allPositions: Seq[Position] = Goban.positions(size)

  def at(pos: Position): Color = at(pos.x, pos.y, pos.z)
  def at(x: Int, y: Int, z: Int): Color = stones(x)(y)(z)

  override def equals(obj: Any): Boolean =
    obj match
      case g: Goban =>
        if size != g.size then false
        else allPositions.forall(pos => at(pos) == g.at(pos))
      case _ => false

  @targetName("minus")
  def -(other: Goban): IndexedSeq[Move] =
    if size != other.size then sys.error(s"sizes $size != ${other.size}")
    for (pos <- other.emptyPositions.toIndexedSeq if at(pos) != Empty)
      yield Move(pos, at(pos))

  override def toString: String =
    var out = ""
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += at(x, y, z).toString
        if z < size then out += "|"
      out += "\n"
    out

  def checkValid(move: Move): Try[Unit] =
    if move.x > size || move.y > size || move.z > size then
      Failure(OutsideBoard(move.x, move.y, move.z))
    else if at(move.position) != Empty then Failure(PositionOccupied(move, at(move.position)))
    else if isSuicide(move) then Failure(Suicide(move))
    else Success(())

  def setStone(move: Move): Goban = setStone(move.x, move.y, move.z, move.color)
  def setStone(x: Int, y: Int, z: Int, color: Color): Goban =
    if isOnBoardPlusBorder(x, y, z) then sys.error(s"outside board: $x, $y, $z")
    new Goban(size, stones.updated(x, stones(x).updated(y, stones(x)(y).updated(z, color))))

  def hasLiberties(move: Move): Boolean =
    if !Set(Black, White).contains(move.color) then
      sys.error(s"trying to find liberties for $move - not a stone but ${move.color}")
    hasLibertiesHelper(move, mutable.Set[Position]())

  private def hasLibertiesHelper(move: Move, visited: mutable.Set[Position]): Boolean =
    if at(move.position) != move.color || visited.contains(move.position) then false
    else
      visited += move.position
      val neighboring = neighbors(move.position)
      if neighboring.exists(at(_) == Empty) then true
      else
        val toCheck = neighboring.filter(at(_) == move.color).map(Move(_, move.color)).toSet
        // check if part of a connected area
        hasLibertiesHelper(toCheck, visited)

  @tailrec
  private def hasLibertiesHelper(moves: Set[Move], visited: mutable.Set[Position]): Boolean =
    if moves.isEmpty then false
    else if moves.size == 1 then hasLibertiesHelper(moves.head, visited)
    else if hasLibertiesHelper(moves.head, visited) then true
    else hasLibertiesHelper(moves - moves.head, visited)

  /** The empty points next to a stone of `col`, counted directly rather than group by group. */
  def numLiberties(col: Color): Int =
    if col != Black && col != White then 0
    else emptyPositions.count(hasNeighborOfColor(_, col))

  def hasNeighborOfColor(pos: Position, col: Color): Boolean =
    neighbors(pos).exists(at(_) == col)

  def hasEmptyNeighbor(position: Position): Boolean = hasNeighborOfColor(position, Empty)

  @targetName("numLibertiesMove")
  def numLiberties(area: Set[Move]): Int = numLiberties(area.map(_.position))

  @targetName("numLibertiesPosition")
  def numLiberties(area: Set[Position]): Int = emptyNeighbors(area).size

  private def emptyNeighbors(area: Set[Position]): Set[Position] =
    area.flatMap(neighbors(_)).filter(at(_) == Empty)

  def connectedStones(move: Move): Set[Move] =
    if at(move.position) != move.color then Set()
    else connectedStonesHelper(move, mutable.Set[Position]())

  private def connectedStonesHelper(move: Move, visited: mutable.Set[Position]): Set[Move] =
    if at(move.position) != move.color || visited.contains(move.position) then Set()
    else
      visited += move.position
      var connected = Set(move)
      for position <- neighborsOfColor(move.position, move.color) if !visited.contains(position)
      do connected = connected ++ connectedStonesHelper(Move(position, move.color), visited)
      connected

  def neighbors(position: Position): Seq[Position] = Goban.neighbors(size, position)

  def neighbors(col: Color): Set[Position] =
    val validAreas = areas.filter(_.color == col)
    val validStones = validAreas.foldLeft(Set[Move]())((stones, area) => stones ++ area.stones)
    validStones.flatMap(stone => neighbors(stone.position))

  def neighborsOfColor(position: Position, color: Color): Seq[Position] =
    neighbors(position).filter(at(_) == color)

  def emptyPositions: Seq[Position] = allPositions.filter(at(_) == Empty)

  def checkAndClear(move: Move): Goban =
    if Set(Empty, Sentinel, move.color).contains(at(move.position)) then this
    else
      val opponent = !move.color
      if hasLiberties(Move(move.x, move.y, move.z, opponent)) then this
      else clearListOfPlaces(connectedStones(Move(move.x, move.y, move.z, opponent)), this)

  /** The liberties of every group, found in one pass the first time a point without an empty
    * neighbour is checked for suicide, and then shared by all checks on this board.
    */
  private lazy val groups: Groups = Groups.of(this)

  /** A move is suicide when it leaves its own group without liberties and captures nothing. A
    * point with an empty neighbour never is. Otherwise every neighbour is a stone, and the move is
    * suicide if no own group next to it has a liberty besides this point and every opponent group
    * next to it has one; a group next to the point has another liberty if it has two.
    */
  private[go3d] def isSuicide(move: Move): Boolean =
    !hasEmptyNeighbor(move.position) && neighbors(move.position).forall(p =>
      if at(p) == move.color then groups.liberties(p) == 1 else groups.liberties(p) > 1
    )

  private def calculateAreas(): Set[Area] =
    @tailrec
    def areaFromMoves(moves: Set[Move], areas: Set[Area]): Set[Area] =
      if moves.isEmpty then areas
      else
        val firstArea = connectedStones(moves.head)
        areaFromMoves(
          moves -- firstArea,
          areas + Area(firstArea, numLiberties(firstArea), this)
        )

    val stones = for (p <- allPositions if at(p) == Black || at(p) == White) yield Move(p, at(p))
    areaFromMoves(stones.toSet, Set())

  /** `goban` with the points of `toClear` empty. */
  private def clearListOfPlaces(toClear: Set[Move], goban: Goban): Goban =
    toClear.foldLeft(goban)((board, stone) => board.setStone(stone.x, stone.y, stone.z, Empty))

  private def isOnBoardPlusBorder(x: Int, y: Int, z: Int): Boolean =
    x < 0 || y < 0 || z < 0 || x > size + 1 || y > size + 1 || z > size + 1

def onBoard(x: Int, y: Int, z: Int, size: Int): Boolean =
  x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

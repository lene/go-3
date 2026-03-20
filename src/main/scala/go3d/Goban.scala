package go3d

import scala.annotation.tailrec
import scala.annotation.targetName
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object Goban:
  def start(size: Int): Try[Goban] =
    if size < MinBoardSize then Failure(BadBoardSize(size, "too small"))
    else if size > MaxBoardSize then Failure(BadBoardSize(size, "too big"))
    else if size % 2 == 0 then Failure(BadBoardSize(size, "even"))
    else Success(new Goban(size, initializeBoard(size)))

class Goban private[go3d](val size: Int, val stones: Array[Array[Array[Color]]]) extends GoGame:

  lazy val areas: Set[Area] = calculateAreas()
  private lazy val allNeighbors: Map[Position, Set[Move]] = neighborsMap()
  lazy val allPositions: Seq[Position] =
    for (x <- 1 to size; y <- 1 to size; z <- 1 to size) yield new Position(x, y, z)


  def at(pos: Position): Color = at(pos.x, pos.y, pos.z)
  def at(x: Int, y: Int, z: Int): Color = stones(x)(y)(z)

  override def equals(obj: Any): Boolean =
    obj match
      case g: Goban =>
        if size != g.size then false
        else allPositions.forall(pos => at(pos) == g.at(pos))
      case _ => false

  override def clone(): Goban = new Goban(size, deepCopy(stones))

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
          out += at(x, y, z)
        if z < size then out += "|"
      out += "\n"
    out

  def checkValid(move: Move): Try[Unit] =
    if move.x > size || move.y > size || move.z > size then Failure(OutsideBoard(move.x, move.y, move.z))
    else if at(move.position) != Empty then Failure(PositionOccupied(move, at(move.position)))
    else if isSuicide(move) then Failure(Suicide(move))
    else Success(())

  def setStone(move: Move): Goban = setStone(move.x, move.y, move.z, move.color)
  def setStone(x: Int, y: Int, z: Int, color: Color): Goban =
    if isOnBoardPlusBorder(x, y, z) then sys.error(s"outside board: $x, $y, $z")
    val newStones = deepCopy(stones)
    newStones(x)(y)(z) = color
    new Goban(size, newStones)

  def hasLiberties(move: Move): Boolean =
    if !Set(Black, White).contains(move.color) then
      sys.error(s"trying to find liberties for $move - not a stone but ${move.color}")
    hasLibertiesHelper(move, mutable.Set[Position]())

  private def hasLibertiesHelper(move: Move, visited: mutable.Set[Position]): Boolean =
    if at(move.position) != move.color then return false
    if visited.contains(move.position) then return false
    visited += move.position
    val neighboring = neighbors(move.position)
    if neighboring.exists(at(_) == Empty) then return true
    val toCheck = neighboring.filter(at(_) == move.color).map(Move(_, move.color)).toSet
    // check if part of a connected area
    hasLibertiesHelper(toCheck, visited)

  @tailrec
  private def hasLibertiesHelper(moves: Set[Move], visited: mutable.Set[Position]): Boolean =
    if moves.isEmpty then return false
    if moves.size == 1 then return hasLibertiesHelper(moves.head, visited)
    if hasLibertiesHelper(moves.head, visited) then return true
    hasLibertiesHelper(moves - moves.head, visited)

  def numLiberties(col: Color): Int =
    emptyPositions.toSet.intersect(neighbors(col)).size

  def hasNeighborOfColor(pos: Position, col: Color): Boolean =
    allNeighbors(pos).exists(_.color == col)

  def hasEmptyNeighbor(position: Position): Boolean = hasNeighborOfColor(position, Empty)

  @targetName("numLibertiesMove")
  def numLiberties(area: Set[Move]): Int = numLiberties(area.map(_.position))

  @targetName("numLibertiesPosition")
  def numLiberties(area: Set[Position]): Int = emptyNeighbors(area).size

  private def emptyNeighbors(area: Set[Position]): Set[Position] =
    area.flatMap(pos => allNeighbors(pos)).map(_.position).intersect(emptyPositions.toSet)

  def connectedStones(move: Move): Set[Move] =
    if at(move.position) != move.color then return Set()
    connectedStonesHelper(move, mutable.Set[Position]())

  private def connectedStonesHelper(move: Move, visited: mutable.Set[Position]): Set[Move] =
    if at(move.position) != move.color then return Set()
    if visited.contains(move.position) then return Set()
    visited += move.position
    var connected = Set(move)
    for position <- neighborsOfColor(move.position, move.color) if !visited.contains(position) do
      connected = connected ++ connectedStonesHelper(Move(position, move.color), visited)
    connected

  private def isOnBoard(x: Int, y: Int, z: Int): Boolean = onBoard(x, y, z, size)

  def neighbors(position: Position): Seq[Position] =
    for (
      x <- position.x-1 to position.x+1;
      y <- position.y-1 to position.y+1;
      z <- position.z-1 to position.z+1
      if isNeighbor(position, x, y, z)
    ) yield new Position(x, y, z)

  def neighbors(col: Color): Set[Position] =
    val validAreas = areas.filter(_.color == col)
    val validStones = validAreas.foldLeft(Set[Move]())((stones, area) => stones ++ area.stones)
    val neighborStones = validStones.map(_.position).flatMap(allNeighbors.apply)
    neighborStones.map(_.position)

  def neighborsOfColor(position: Position, color: Color): Seq[Position] =
    neighbors(position).filter(at(_) == color)

  def emptyPositions: Seq[Position] = allPositions.filter(at(_) == Empty)

  private def neighborsMap(): Map[Position, Set[Move]] =
    allPositions.map(pos => pos -> neighbors(pos).map(p => Move(p, at(p))).toSet).toMap

  def checkAndClear(move: Move): Goban =
    if Set(Empty, Sentinel, move.color).contains(at(move.position)) then return this
    val opponent = !move.color
    if hasLiberties(Move(move.x, move.y, move.z, opponent)) then this
    else clearListOfPlaces(connectedStones(Move(move.x, move.y, move.z, opponent)), this)

  private def isSuicide(move: Move): Boolean =
    if hasLiberties(move) then return false
    val wouldBeBoardAfterMove = setStone(move)
    neighbors(move.position).forall(
      p => wouldBeBoardAfterMove.hasLiberties(Move(p, !move.color))
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


  @tailrec
  private def clearListOfPlaces(toClear: Set[Move], goban: Goban): Goban =
    if toClear.isEmpty then return goban
    clearListOfPlaces(toClear.dropRight(1), goban.setStone(Move(toClear.last.position, Empty)))

  private def isNeighbor(position: Position, x: Int, y: Int, z: Int): Boolean =
    isOnBoard(x, y, z) && (position - new Position(x, y, z)).abs == 1

  private def isOnBoardPlusBorder(x: Int, y: Int, z: Int): Boolean =
    x < 0 || y < 0 || z < 0 || x > size + 1 || y > size + 1 || z > size + 1

def initializeBoard(size: Int): Array[Array[Array[Color]]] =
  val tempStones = Array.ofDim[Color](size+2, size+2, size+2)
  for
    x <- 0 to size+1
    y <- 0 to size+1
    z <- 0 to size+1
  do
    tempStones(x)(y)(z) = if onBoard(x, y, z, size) then Empty else Sentinel
  tempStones

def onBoard(x: Int, y: Int, z: Int, size: Int): Boolean =
  x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

def deepCopy[T: ClassTag](elements: Array[Array[T]]): Array[Array[T]] =
  elements.map(_.clone)

def deepCopy[T: ClassTag](elements: Array[Array[Array[T]]]): Array[Array[Array[T]]] =
  elements.map(deepCopy(_))

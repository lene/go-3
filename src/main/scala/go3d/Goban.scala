package go3d

import scala.annotation.{tailrec, targetName}
import scala.reflect.ClassTag

def newGoban(size: Int): Goban = Goban(size, initializeBoard(size))

class Goban(val size: Int, val stones: Array[Array[Array[Color]]]) extends GoGame:

  if size < MinBoardSize then throw BadBoardSize(size, "too small")
  if size > MaxBoardSize then throw BadBoardSize(size, "too big")
  if size % 2 == 0 then throw BadBoardSize(size, "even")

  lazy val areas: Set[Area] = calculateAreas()

  def at(pos: Position): Color = at(pos.x, pos.y, pos.z)
  def at(x: Int, y: Int, z: Int): Color = stones(x)(y)(z)

  override def equals(obj: Any): Boolean =
    obj match
      case g: Goban =>
        if size != g.size then false
        else allPositions.forall(pos => at(pos) == g.at(pos))
      case _ => false

  override def clone(): Goban = Goban(size, deepCopy(stones))

  @targetName("minus")
  def -(other: Goban): IndexedSeq[Move] =
    if size != other.size then throw IllegalArgumentException(s"sizes ${size} != ${other.size}")
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

  def checkValid(move: Move): Unit =
    if move.x > size || move.y > size || move.z > size then throw OutsideBoard(move.x, move.y, move.z)
    if at(move.position) != Empty then throw PositionOccupied(move, at(move.position))
    if isSuicide(move) then throw Suicide(move)

  def setStone(move: Move): Goban = setStone(move.x, move.y, move.z, move.color)
  def setStone(x: Int, y: Int, z: Int, color: Color): Goban =
    if isOnBoardPlusBorder(x, y, z) then throw OutsideBoard(x, y, z)
    val newStones = deepCopy(stones)
    newStones(x)(y)(z) = color
    Goban(size, newStones)

  def hasLiberties(move: Move): Boolean =
    if !Set(Black, White).contains(move.color) then
      throw ColorMismatch(s"trying to find liberties for $move - not a stone but", move.color)
    if at(move.position) != move.color then return false
    var toCheck = Set[Move]()
    for position <- neighbors(move.position) do
      at(position) match
        case Empty => return true
        case move.color => toCheck = toCheck + Move(position, move.color)
        case _ =>
    // check if part of a connected area
    setStone(Move(move.position, Sentinel)).hasLiberties(toCheck)

  def numLiberties(area: Set[Move]): Int = numLiberties(area.map(m => m.position))

  @targetName("numLibertiesPosition")
  def numLiberties(area: Set[Position]): Int = emptyNeighbors(area).size

  def emptyNeighbors(area: Set[Position]): Set[Position] =
    area.foldLeft(Set[Position]())(
      (neighbors, position) => neighbors ++ neighborsOfColor(position, Empty)
    )

  @tailrec
  private def hasLiberties(moves: Set[Move]): Boolean =
    if moves.isEmpty then return false
    if moves.size == 1 then return hasLiberties(moves.head)
    if hasLiberties(moves.head) then return true
    hasLiberties(moves - moves.head)

  def connectedStones(move: Move): Set[Move] =
    if at(move.position) != move.color then return Set()
    // ok, this is not functional style, but to me much clearer than using recursion
    var connected = Set(move)
    for position <- neighborsOfColor(move.position, move.color) if !(connected contains(Move(position, move.color))) do
      connected = connected ++ setStone(Move(move.position, Sentinel)).connectedStones(Move(position, move.color))
    connected

  def isOnBoard(x: Int, y: Int, z: Int): Boolean = onBoard(x, y, z, size)

  def neighbors(position: Position): Seq[Position] =
    for (
      x <- position.x-1 to position.x+1;
      y <- position.y-1 to position.y+1;
      z <- position.z-1 to position.z+1
      if isNeighbor(position, x, y, z)
    ) yield Position(x, y, z)

  def neighborsOfColor(position: Position, color: Color): Seq[Position] = neighbors(position).filter(at(_) == color)

  def emptyPositions: Seq[Position] = for (p <- allPositions if at(p) == Empty) yield p

  def allPositions: Seq[Position] =
    for (x <- 1 to size; y <- 1 to size; z <- 1 to size) yield Position(x, y, z)

  def hasEmptyNeighbor(position: Position): Boolean = neighbors(position).exists(at(_) == Empty)

  def checkAndClear(move: Move): Goban =
    if Set(Empty, Sentinel, move.color).contains(at(move.position)) then return this
    if hasLiberties(Move(move.x, move.y, move.z, !move.color)) then return this
    clearListOfPlaces(connectedStones(Move(move.x, move.y, move.z, !move.color)), this)

  def isSuicide(move: Move): Boolean =
    if hasLiberties(move) then return false
    val wouldBeBoardAfterMove = setStone(move)
    for position <- neighbors(move.position) do
      if !wouldBeBoardAfterMove.hasLiberties(Move(position, !move.color)) then return false
    true

  private def calculateAreas(): Set[Area] =
    @tailrec
    def areaFromMoves(moves: Set[Move], areas: Set[Area]): Set[Area] =
      if moves.isEmpty then areas
      else
        val firstArea = connectedStones(moves.head)
        areaFromMoves(
          moves -- firstArea,
          areas + Area(firstArea, numLiberties(firstArea))
        )

    val stones = for (p <- allPositions if at(p) == Black || at(p) == White) yield Move(p, at(p))
    areaFromMoves(stones.toSet, Set())


  @tailrec
  private def clearListOfPlaces(toClear: Set[Move], goban: Goban): Goban =
    if toClear.isEmpty then return goban
    clearListOfPlaces(toClear.dropRight(1), goban.setStone(Move(toClear.last.position, Empty)))

  private def isNeighbor(position: Position, x: Int, y: Int, z: Int): Boolean =
    isOnBoard(x, y, z) && (position - Position(x, y, z)).abs == 1

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

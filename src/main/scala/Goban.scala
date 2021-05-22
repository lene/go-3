package go3d

import scala.reflect.ClassTag

def initializeBoard(size: Int): Array[Array[Array[Color]]] =
  val tempStones = Array.ofDim[Color](size+2, size+2, size+2)
  for
    x <- 0 to size+1
    y <- 0 to size+1
    z <- 0 to size+1
  do
    tempStones(x)(y)(z) = if onBoard(x, y, z, size) then Color.Empty else Color.Sentinel
  tempStones

def onBoard(x: Int, y: Int, z: Int, size: Int): Boolean =
    x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

def deepCopy[T: ClassTag](elements: Array[Array[T]]): Array[Array[T]] =
  elements.map(_.clone)

def deepCopy[T: ClassTag](elements: Array[Array[Array[T]]]): Array[Array[Array[T]]] =
  elements.map(deepCopy(_))

class Goban(val size: Int, val stones: Array[Array[Array[Color]]]) extends GoGame:

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)

  def at(pos: Position): Color = at(pos.x, pos.y, pos.z)
  def at(x: Int, y: Int, z: Int): Color = stones(x)(y)(z)

  override def clone(): Goban = Goban(size, deepCopy(stones))

  def -(other: Goban): IndexedSeq[Move] =
    if size != other.size then throw IllegalArgumentException(s"sizes ${size} != ${other.size}")
    for (
      x <- 1 to size;
      y <- 1 to size;
      z <- 1 to size
      if other.at(x, y, z) == Color.Empty && at(x, y, z) != Color.Empty
    ) yield Move(x, y, z, at(x, y, z))

  def checkValid(move: Move): Unit =
    if move.x > size || move.y > size || move.z > size then throw OutsideBoard(move.x, move.y, move.z)
    if at(move.position) != Color.Empty then throw PositionOccupied(move, at(move.position))
    if isSuicide(move) then throw Suicide(move)

  def setStone(x: Int, y: Int, z: Int, color: Color): Goban =
    if isOnBoardPlusBorder(x, y, z) then
      throw OutsideBoard(x, y, z)
    val newStones = deepCopy(stones)
    newStones(x)(y)(z) = color
    Goban(size, newStones)

  def setStone(move: Move): Goban = setStone(move.x, move.y, move.z, move.color)

  def hasLiberties(move: Move): Boolean =
    if !Set(Color.Black, Color.White).contains(move.color) then
      throw IllegalArgumentException(
        s"trying to find liberties for $move which is not a stone but ${move.color}"
      )
    if at(move.position) != move.color then return false
    var toCheck = Set[Move]()
    for position <- neighbors(move.position) do
      at(position) match
        case Color.Empty => return true
        case move.color => toCheck = toCheck + Move(position, move.color)
        case _ =>

    // check if part of a connected area
    val tempBoard = setStone(Move(move.position, Color.Sentinel))
    for checking <- toCheck do
      if tempBoard.hasLiberties(checking) then
        return true
    return false

  def connectedStones(move: Move): List[Move] =
    if at(move.position) != move.color then
      throw IllegalArgumentException(
        s"trying to find connected stones to $move but is ${at(move.position)}"
      )

    var area = List(move)
    for position <- neighbors(move.position) if at(position) == move.color do
      area :::= setStone(Move(move.position, Color.Sentinel)).connectedStones(Move(position, move.color))
    return area

  def isOnBoard(x: Int, y: Int, z: Int): Boolean = onBoard(x, y, z, size)

  def neighbors(position: Position): Seq[Position] =
    for (
      x <- position.x-1 to position.x+1;
      y <- position.y-1 to position.y+1;
      z <- position.z-1 to position.z+1
      if isNeighbor(position, x, y, z)
    ) yield Position(x, y, z)

  def hasEmptyNeighbor(position: Position): Boolean =
    for position <- neighbors(position) do if at(position) == Color.Empty then return true
    return false

  def checkAndClear(move: Move): Goban =
    if Set(Color.Empty, Color.Sentinel, move.color).contains(at(move.position)) then return this
    if hasLiberties(Move(move.x, move.y, move.z, !move.color)) then return this
    val area = connectedStones(Move(move.x, move.y, move.z, !move.color))
    var cleared = clone()
    for toClear <- area do
      cleared = cleared.setStone(Move(toClear.position, Color.Empty))
    return cleared

  def isSuicide(move: Move): Boolean =
    if hasLiberties(move) then return false
    val boardAfterMove = setStone(move)
    for position <- neighbors(move.position) do
      if !boardAfterMove.hasLiberties(Move(position, !move.color)) then
        return false
    return true

  private def isNeighbor(position: Position, x: Int, y: Int, z: Int): Boolean =
    isOnBoard(x, y, z) && (position - Position(x, y, z)).abs == 1

  private def isOnBoardPlusBorder(x: Int, y: Int, z: Int): Boolean =
    x < 0 || y < 0 || z < 0 || x > size + 1 || y > size + 1 || z > size + 1

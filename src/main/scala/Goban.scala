package go3d

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

class Goban(val size: Int, val stones: Array[Array[Array[Color]]]) extends GoGame:

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)

  def at(pos: Position): Color = at(pos.x, pos.y, pos.z)
  def at(x: Int, y: Int, z: Int): Color = stones(x)(y)(z)

  def isValid(move: Move): Boolean =
    if move.x > size || move.y > size || move.z > size then false
    else if at(move.position) != Color.Empty then false
    else true

  def setStone(x: Int, y: Int, z: Int, color: Color): Unit =
    if x < 0 || y < 0 || z < 0 || x > size+1 || y > size+1 || z > size+1 then
      throw IllegalArgumentException("Outside board: " + (x, y, z))
    stones(x)(y)(z) = color
  def setStone(move: Move): Unit = setStone(move.x, move.y, move.z, move.color)

  def isOnBoard(x: Int, y: Int, z: Int): Boolean = onBoard(x, y, z, size)

package go3d

import Array._

class Goban(val size: Int, val numPlayers: Int = DefaultPlayers):

  class Position(val x: Int, val y: Int, val z: Int):
    if x < 1 || y < 1 || z < 1
    then throw IllegalArgumentException("coordinate < 1: "+x+", "+y+", "+z)
    if x > size || y > size || z > size
    then throw IllegalArgumentException("coordinate > "+size+": "+x+", "+y+", "+z)

  class Move(val position: Position, val color: Color):
    def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)
  if numPlayers > MaxPlayers then throw IllegalArgumentException("too many players: "+numPlayers)
  if numPlayers < 2 then throw IllegalArgumentException("too few players: "+numPlayers)

  val stones = initializeBoard

  def at(pos: Position): Color = stones(pos.x)(pos.y)(pos.z)

  def newBoard(move: Move): Goban =
    if !isValid(move) then throw IllegalMove()
    val newboard = this
    newboard.stones(move.position.x)(move.position.y)(move.position.z) = move.color
    newboard

  def isValid(move: Move): Boolean =
    at(move.position) == Color.Empty

  override def toString: String =
    var out = ""
    for z <- 1 to size do
      for y <- 0 to size + 1 do
        for x <- 0 to size + 1 do
          out += stones(x)(y)(z)
        out += "\n"
    out

  private def initializeBoard =
    val tempStones = ofDim[Color](size+2, size+2, size+2)
    for x <- 0 to size+1 do
      for y <- 0 to size+1 do
        for z <- 0 to size+1 do
          tempStones(x)(y)(z) = if x*y*z == 0 || x > size || y > size || z > size
            then Color.Sentinel
            else Color.Empty
    tempStones


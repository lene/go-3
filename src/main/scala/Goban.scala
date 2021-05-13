package go3d

import Array._

class Goban(val size: Int, val numPlayers: Int = DefaultPlayers):

  class Position(val x: Int, val y: Int, val z: Int):
    if x < 1 || y < 1 || z < 1 then
      throw IllegalArgumentException("coordinate < 1: "+x+", "+y+", "+z)
    if x > size || y > size || z > size then
      throw IllegalArgumentException("coordinate > "+size+": "+x+", "+y+", "+z)

  class Move(val position: Position, val color: Color):
    def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
    def isPass: Boolean = position == null

  class Pass(override val color: Color) extends Move(null, color)

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)
  if numPlayers > MaxPlayers then throw IllegalArgumentException("too many players: "+numPlayers)
  if numPlayers < 2 then throw IllegalArgumentException("too few players: "+numPlayers)

  val stones = initializeBoard
  var moves: Array[Any] = Array[Any]()

  def at(pos: Position): Color = stones(pos.x)(pos.y)(pos.z)

  def newBoard(move: Move): Goban =
    if gameOver(move) then throw GameOver(this)
    if !isValid(move) then
      throw IllegalMove(move.position.x, move.position.y, move.position.z, move.color)

    val newboard = this
    if !move.isPass then
      newboard.stones(move.position.x)(move.position.y)(move.position.z) = move.color
    newboard.moves = moves.appended(move)
    newboard

  def isValid(move: Move): Boolean =
    if move.isPass then true
    else at(move.position) == Color.Empty &&
      (moves.isEmpty || moves.last.asInstanceOf[Move].color != move.color)

  def gameOver(move: Move) = !moves.isEmpty && move.isPass && moves.last.asInstanceOf[Move].isPass

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


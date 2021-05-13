package go3d

import Array._

class Goban(val size: Int, val numPlayers: Int = DefaultPlayers, val verbose: Boolean = false):

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
    if !move.isPass then newboard.setStone(move)
    newboard.moves = moves.appended(move)
    newboard

  def isValid(move: Move): Boolean =
    if move.isPass then true
    else if move.position.x > size || move.position.y > size || move.position.z > size then false
    else if at(move.position) != Color.Empty then false
    else if isDifferentPlayer(move) then true
    else if isKo(move) then false
    else false

  override def toString: String =
    var out = ""
    for
      z <- 1 to size
      y <- 0 to size + 1
    do
      for x <- 0 to size + 1 do
        out += stones(x)(y)(z)
      out += "\n"
    out

  private def setStone(move: Move): Unit =
    stones(move.position.x)(move.position.y)(move.position.z) = move.color
    checkArea(move)

  private def checkArea(move: Move) =
    for
      x <- move.position.x-1 to move.position.x+1
      y <- move.position.y-1 to move.position.y+1
      z <- move.position.z-1 to move.position.z+1
      if isRelevantForLibertiesChecking(move, x, y, z)
    do
      checkAndClear(Move(Position(x, y, z), move.color))
    checkAndClear(move)  // check the stone just set after all others

  private def isRelevantForLibertiesChecking(move: Move, x: Int, y: Int, z: Int): Boolean =
    (x != move.position.x || y != move.position.y || z != move.position.x) && isOnBoard(x, y, z)

  private def isOnBoard(x: Int, y: Int, z: Int): Boolean =
    x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

  private def checkAndClear(move: Move): Unit = {
    if at(move.position) == move.color then return
    if verbose then println("Found "+move.color+" at "+move.position)
    stones(move.position.x)(move.position.y)(move.position.z) = Color.Empty
  }

  private def initializeBoard: Array[Array[Array[Color]]] =
    val tempStones = ofDim[Color](size+2, size+2, size+2)
    for
      x <- 0 to size+1
      y <- 0 to size+1
      z <- 0 to size+1
    do
      tempStones(x)(y)(z) = if isOnBoard(x, y, z)
      then Color.Empty
      else Color.Sentinel
    tempStones

  private def isDifferentPlayer(move: Move): Boolean =
    moves.isEmpty || moves.last.asInstanceOf[Move].color != move.color

  private def isKo(move: Move): Boolean = false

  private def gameOver(move: Move): Boolean =
    !moves.isEmpty && move.isPass && moves.last.asInstanceOf[Move].isPass

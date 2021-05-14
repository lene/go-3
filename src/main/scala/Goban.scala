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

  def makeMove(move: Move|Pass): Goban =
    val newboard = this
    move match
      case p: Pass => if gameOver(p) then throw GameOver(this)
      case m: Move =>
        if !isValid(m) then throw IllegalMove(m.x, m.y, m.z, m.color)
        newboard.setStone(m)
    newboard.moves = moves.appended(move)
    newboard

  def isValid(move: Move|Pass): Boolean =
    move match
      case p: Pass => true
      case m: Move => isValidMove(m)

  def isValidMove(move: Move): Boolean =
    if move.x > size || move.y > size || move.z > size then false
    else if at(move.position) != Color.Empty then false
    else if isDifferentPlayer(move) then true
    else if isKo(move) then false
    else false

  override def toString: String =
    var out = ""
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += stones(x)(y)(z)
        if z < size then out += "|"
      out += "\n"
    out

  def hasLiberties(move: Move): Boolean =
//    if move.color != Color.Black || move.color != Color.White then throw IllegalArgumentException()
//    if stones(move.x)(move.y)(move.z) != move.color then throw IllegalArgumentException()
    for x <- move.x-1 to move.x+1 by 2 do
      if stones(x)(move.y)(move.z) == Color.Empty then return true
    for y <- move.y-1 to move.y+1 by 2 do
      if stones(move.x)(y)(move.z) == Color.Empty then return true
    for z <- move.z-1 to move.z+1 by 2 do
      if stones(move.x)(move.y)(z) == Color.Empty then return true
    return false

  private def setStone(move: Move): Unit =
    stones(move.x)(move.y)(move.z) = move.color
    checkArea(move)

  private def checkArea(move: Move) =
    for
      x <- move.x-1 to move.x+1
      y <- move.y-1 to move.y+1
      z <- move.z-1 to move.z+1
      if isRelevantForLibertiesChecking(move, x, y, z)
    do
      checkAndClear(Move(Position(x, y, z), move.color))
    checkAndClear(move)  // check the stone just set after all others

  private def isRelevantForLibertiesChecking(move: Move, x: Int, y: Int, z: Int): Boolean =
    (x != move.x || y != move.y || z != move.x) && isOnBoard(x, y, z)

  private def isOnBoard(x: Int, y: Int, z: Int): Boolean =
    x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

  private def checkAndClear(move: Move): Unit = {
    if at(move.position) == move.color then return
    if hasLiberties(move) then return
    if verbose then println("Found "+move.color+" at "+move.position)
    stones(move.x)(move.y)(move.z) = Color.Empty
  }

  private def initializeBoard: Array[Array[Array[Color]]] =
    val tempStones = ofDim[Color](size+2, size+2, size+2)
    for
      x <- 0 to size+1
      y <- 0 to size+1
      z <- 0 to size+1
    do
      tempStones(x)(y)(z) = if isOnBoard(x, y, z) then Color.Empty else Color.Sentinel
    tempStones

  private def isDifferentPlayer(move: Move): Boolean =
    moves.isEmpty || moves.last.asInstanceOf[Move].color != move.color

  private def gameOver(pass: Pass): Boolean =
    !moves.isEmpty && moves.last.isInstanceOf[Pass]

  private def isKo(move: Move): Boolean = false

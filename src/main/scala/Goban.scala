package go3d

import Array._

class Goban(val size: Int, val numPlayers: Int = DefaultPlayers, val verbose: Boolean = false):

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)
  if numPlayers > MaxPlayers then throw IllegalArgumentException("too many players: "+numPlayers)
  if numPlayers < 2 then throw IllegalArgumentException("too few players: "+numPlayers)

  val stones = initializeBoard
  var moves: Array[Move | Pass] = Array()

  def at(pos: Position): Color = stones(pos.x)(pos.y)(pos.z)

  def makeMove(move: Move | Pass): Goban =
    val newboard = this
    if verbose then println(move)
    move match
      case p: Pass => if gameOver(p) then throw GameOver(this)
      case m: Move =>
        if !isValid(m) then throw IllegalMove(m.x, m.y, m.z, m.color)
        newboard.setStone(m)
    newboard.moves = moves.appended(move)
    newboard

  def isValid(move: Move | Pass): Boolean =
    move match
      case p: Pass => true
      case m: Move => isValidMove(m)

  def isValidMove(move: Move): Boolean =
    if move.x > size || move.y > size || move.z > size then false
    else if at(move.position) != Color.Empty then false
    else if isDifferentPlayer(move) then true
    else if isKo(move) then false
    else if isSuicide(move) then false
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
    if !Set(Color.Black, Color.White).contains(move.color) then
      throw IllegalArgumentException(move.toString)
    if at(move.position) != move.color then return false
    var toCheck = Set[Move]()
    for
      x <- move.x-1 to move.x+1
      y <- move.y-1 to move.y+1
      z <- move.z-1 to move.z+1
      if isNeighbor(move, x, y, z)
    do
      stones(x)(y)(z) match
        case Color.Empty => return true
        case move.color => toCheck = toCheck + Move(x, y, z, move.color)
        case _ =>

    // check if part of a connected area
    stones(move.x)(move.y)(move.z) = Color.Sentinel
    for checking <- toCheck do
      if hasLiberties(checking) then
        stones(move.x)(move.y)(move.z) = move.color
        return true

    stones(move.x)(move.y)(move.z) = move.color
    return false

  private def setStone(move: Move): Unit =
    stones(move.x)(move.y)(move.z) = move.color
    checkArea(move)

  private def checkArea(move: Move) =
    for
      x <- move.x-1 to move.x+1
      y <- move.y-1 to move.y+1
      z <- move.z-1 to move.z+1
      if isNeighbor(move, x, y, z)
    do
      checkAndClear(Move(Position(x, y, z), move.color))

  private def isNeighbor(move: Move, x: Int, y: Int, z: Int): Boolean =
    isOnBoard(x, y, z) && (move.position - Position(x, y, z)).abs == 1

  private def isOnBoard(x: Int, y: Int, z: Int): Boolean =
    x > 0 && y > 0 && z > 0 && x <= size && y <= size && z <= size

  private def checkAndClear(move: Move): Unit = {
    if Set(Color.Empty, Color.Sentinel, move.color).contains(at(move.position)) then return
    if hasLiberties(Move(move.x, move.y, move.z, !move.color)) then return
    if verbose then println("Found "+move.color+" at "+move.position)
    for toClear <- connectedStones(Move(move.x, move.y, move.z, !move.color)) do
      stones(toClear.x)(toClear.y)(toClear.z) = Color.Empty
  }

  def connectedStones(move: Move): List[Move] =
    if stones(move.x)(move.y)(move.z) != move.color then
      throw IllegalArgumentException(
        "trying to find connected stones to "+move.toString+" but "+move.position+" contains "+
          stones(move.x)(move.y)(move.z)
      )

    var neighbors = List(move)
    for
      x <- move.x-1 to move.x+1
      y <- move.y-1 to move.y+1
      z <- move.z-1 to move.z+1
      if isNeighbor(move, x, y, z)
      if at(Position(x, y, z)) == move.color
    do {
      stones(move.x)(move.y)(move.z) = Color.Sentinel
      neighbors = neighbors ::: connectedStones(Move(x, y, z, move.color))
      stones(move.x)(move.y)(move.z) = move.color
    }

    return neighbors

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
    moves.isEmpty || (moves.last match
      case m: Move => m.color != move.color
      case p: Pass => p.color != move.color
    )

  private def gameOver(pass: Pass): Boolean =
    !moves.isEmpty && moves.last.isInstanceOf[Pass]

  private def isKo(move: Move): Boolean = false

  private def isSuicide(move: Move): Boolean = false

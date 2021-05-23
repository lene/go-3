package go3d

class Game(val size: Int) extends GoGame:
  var goban = newGoban(size)
  var moves: Array[Move | Pass] = Array()
  val captures = scala.collection.mutable.Map[Int, List[Move]]()

  def captures(color: Color): Int =
    captures.values.filter(_(0).color == color).flatten.size
  def lastCapture: List[Move] = if captures.isEmpty then List() else captures.last._2

  def at(pos: Position): Color = goban.at(pos)
  def at(x: Int, y: Int, z: Int): Color = at(Position(x, y, z))

  def makeMove(move: Move | Pass): Game =
    val newboard = this
    move match
      case p: Pass => if gameOver(p) then throw GameOver(this)
      case m: Move =>
        checkValid(m)
        newboard.setStone(m)
    newboard.moves = moves.appended(move)
    return newboard

  def checkValid(move: Move): Unit =
    goban.checkValid(move)
    if !isDifferentPlayer(move.color) then throw WrongTurn(move)
    if isKo(move) then throw Ko(move)

  override def toString: String =
    var out = ""
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += goban.at(x, y, z)
        if z < size then out += "|"
        else if y == 1 && captures.nonEmpty then out += " "+Color.Black.toString*captures(Color.Black)
        else if y == 3 && captures.nonEmpty then out += " "+Color.White.toString*captures(Color.White)
      out += "\n"
    out

  def setStone(move: Move): Game =
    goban = goban.setStone(move)
    checkArea(move)
    return this

  def hasLiberties(move: Move): Boolean = goban.hasLiberties(move)

  def connectedStones(move: Move): List[Move] = goban.connectedStones(move)

  def possibleMoves(color: Color): List[Position] =
    var moves = List[Position]()
    if !isDifferentPlayer(color) then return moves
    for
      x <- 1 to size
      y <- 1 to size
      z <- 1 to size
      if at(x, y, z) == Color.Empty
    do
      try
        if !goban.hasEmptyNeighbor(Position(x, y, z)) then checkValid(Move(x, y, z, color))
        moves = moves.appended(Position(x, y, z))
      catch
        case e: IllegalMove =>
    moves
  
  private def gameOver(pass: Pass): Boolean =
    moves.nonEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(color: Color): Boolean =
    moves.isEmpty || moves.last.color != color

  private def isKo(move: Move): Boolean =
    captures.nonEmpty && lastCapture.length == 1 && lastCapture(0) == move

  private def checkArea(move: Move): Unit =
    for position <- goban.neighbors(move.position) do
      val capturedStones = checkAndClear(Move(position, move.color))
      if capturedStones.nonEmpty then
        if captures.contains(moves.length) then captures(moves.length) :::= capturedStones
        else captures(moves.length) = capturedStones

  private def checkAndClear(move: Move): List[Move] =
    val old_goban = goban.clone()
    goban = goban.checkAndClear(move)
    return (old_goban-goban).toList

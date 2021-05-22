package go3d

class Game(val size: Int, val verbose: Boolean = false) extends GoGame:
  val goban = Goban(size, initializeBoard(size))
  var moves: Array[Move | Pass] = Array()
  var captures = scala.collection.mutable.Map[Int, List[Move]]()

  def captures(color: Color): Int =
    captures.values.filter(_(0).color == color).flatten.size
  def lastCapture: List[Move] = if captures.isEmpty then List() else captures.last._2

  def at(pos: Position): Color = goban.at(pos)
  def at(x: Int, y: Int, z: Int): Color = at(Position(x, y, z))

  def makeMove(move: Move | Pass): Game =
    val newboard = this
    if verbose then println(move)
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
    if isSuicide(move) then throw Suicide(move)

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

  def setStone(move: Move): Unit =
    goban.setStone(move)
    checkArea(move)

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

  def neighbors(position: Position): Seq[Position] = goban.neighbors(position)

  private def gameOver(pass: Pass): Boolean =
    moves.nonEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(color: Color): Boolean =
    moves.isEmpty || moves.last.color != color

  private def isKo(move: Move): Boolean =
    captures.nonEmpty && lastCapture.length == 1 && lastCapture(0) == move

  private def isSuicide(move: Move): Boolean =
    if hasLiberties(move) then return false
    goban.setStone(move)
    for position <- neighbors(move.position) do
      if !hasLiberties(Move(position, !move.color)) then
        goban.setStone(Move(move.position, Color.Empty))
        return false

    goban.setStone(Move(move.position, Color.Empty))
    return true

  private def checkArea(move: Move): Unit =
    for position <- neighbors(move.position) do
      val capturedStones = checkAndClear(Move(position, move.color))
      if capturedStones.nonEmpty then
        if captures.contains(moves.length) then captures(moves.length) = captures(moves.length) ::: capturedStones
        else captures = captures + (moves.length -> capturedStones)

  private def checkAndClear(move: Move): List[Move] = goban.checkAndClear(move)

package go3d

class Game(val size: Int, val verbose: Boolean = false) extends GoGame:
  val goban = Goban(size, initializeBoard(size))
  var moves: Array[Move | Pass] = Array()
  var captures: Map[Int, List[Move]] = Map()

  def captures(color: Color): Int =
    captures.values.filter(_(0).color == color).flatten.size
  def lastCapture: List[Move] = captures.last._2


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
        else if y == 1 && !captures.isEmpty then out += " "+Color.Black.toString*captures(Color.Black)
        else if y == 3 && !captures.isEmpty then out += " "+Color.White.toString*captures(Color.White)
      out += "\n"
    out

  def setStone(move: Move): Unit =
    goban.setStone(move)
    checkArea(move)

  def connectedStones(move: Move): List[Move] =
    if at(move.position) != move.color then
      throw IllegalArgumentException(
        s"trying to find connected stones to $move but is ${at(move.position)}"
      )

    var area = List(move)
    for position <- neighbors(move.position) if at(position) == move.color do
      goban.setStone(Move(move.position, Color.Sentinel))
      area = area ::: connectedStones(Move(position, move.color))
      goban.setStone(move)

    return area

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
    goban.setStone(Move(move.position, Color.Sentinel))
    for checking <- toCheck do
      if hasLiberties(checking) then
        goban.setStone(move)
        return true

    goban.setStone(move)
    return false

  def neighbors(position: Position): Seq[Position] =
    for (
      x <- position.x-1 to position.x+1;
      y <- position.y-1 to position.y+1;
      z <- position.z-1 to position.z+1
      if isNeighbor(position, x, y, z)
    ) yield Position(x, y, z)

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
        if ! hasEmptyNeighbor(Position(x, y, z)) then checkValid(Move(x, y, z, color))
        moves = moves.appended(Position(x, y, z))
      catch
        case e: IllegalMove =>
    moves

  private def hasEmptyNeighbor(position: Position): Boolean =
    for position <- neighbors(position) do if at(position) == Color.Empty then return true
    return false

  private def gameOver(pass: Pass): Boolean =
    !moves.isEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(color: Color): Boolean =
    moves.isEmpty || moves.last.color != color

  private def isKo(move: Move): Boolean =
    !captures.isEmpty && lastCapture.length == 1 && lastCapture(0) == move

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
      checkAndClear(Move(position, move.color))

  private def isNeighbor(position: Position, x: Int, y: Int, z: Int): Boolean =
    goban.isOnBoard(x, y, z) && (position - Position(x, y, z)).abs == 1

  private def checkAndClear(move: Move): Unit =
    if Set(Color.Empty, Color.Sentinel, move.color).contains(at(move.position)) then return
    if hasLiberties(Move(move.x, move.y, move.z, !move.color)) then return
    if verbose then println(s"Found ${move.color} at ${move.position}")
    val area = connectedStones(Move(move.x, move.y, move.z, !move.color))
    for toClear <- area do
      goban.setStone(Move(toClear.position, Color.Empty))
    captures = captures + (moves.length -> area)

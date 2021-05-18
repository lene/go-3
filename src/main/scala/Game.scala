package go3d

class Game(val size: Int, val verbose: Boolean = false) extends GoGame:
  val goban = Goban(size, initializeBoard(size))
  var moves: Array[Move | Pass] = Array()
  var captures: List[Move] = List()

  def captures(color: Color): Int =
    captures.filter(_.color == color).length

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
    if !isDifferentPlayer(move) then throw IllegalMove("Same player twice: "+move)
    if isKo(move) then throw IllegalMove("Ko: "+move)
    if isSuicide(move) then throw IllegalMove("Suicide: "+move)

  override def toString: String =
    var out = ""
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += goban.at(x, y, z)
        if z < size then out += "|"
        else if y == 1 then out += (" "+Color.Black)*captures(Color.Black)
        else if y == 3 then out += (" "+Color.White)*captures(Color.White)
      out += "\n"
    out

  def setStone(move: Move): Unit =
    goban.setStone(move)
    checkArea(move)

  def connectedStones(move: Move): List[Move] =
    if at(move.position) != move.color then
      throw IllegalArgumentException(
        "trying to find connected stones to "+move.toString+" but is "+ at(move.position)
      )

    var area = List(move)
    for position <- neighbors(move) if at(position) == move.color do
      goban.setStone(Move(move.position, Color.Sentinel))
      area = area ::: connectedStones(Move(position, move.color))
      goban.setStone(move)

    return area

  def hasLiberties(move: Move): Boolean =
    if !Set(Color.Black, Color.White).contains(move.color) then
      throw IllegalArgumentException(
        "trying to find liberties for "+move.toString+" which is not a stone but "+move.color
      )
    if at(move.position) != move.color then return false
    var toCheck = Set[Move]()
    for position <- neighbors(move) do
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

  def neighbors(move: Move): Seq[Position] =
    for (
      x <- move.x-1 to move.x+1;
      y <- move.y-1 to move.y+1;
      z <- move.z-1 to move.z+1
      if isNeighbor(move, x, y, z)
    ) yield Position(x, y, z)

  private def gameOver(pass: Pass): Boolean =
    !moves.isEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(move: Move): Boolean =
    moves.isEmpty || moves.last.color != move.color

  private def isKo(move: Move): Boolean = false

  private def isSuicide(move: Move): Boolean =
    if hasLiberties(move) then return false
    else
      for position <- neighbors(move) do
        if !hasLiberties(Move(position, !move.color)) then return false
    return false  // TODO still needs fixing

  private def checkArea(move: Move): Unit =
    for position <- neighbors(move) do
      checkAndClear(Move(position, move.color))

  private def isNeighbor(move: Move, x: Int, y: Int, z: Int): Boolean = {
    goban.isOnBoard(x, y, z) && (move.position - Position(x, y, z)).abs == 1
  }

  private def checkAndClear(move: Move): Unit = {
    if Set(Color.Empty, Color.Sentinel, move.color).contains(at(move.position)) then return
    if hasLiberties(Move(move.x, move.y, move.z, !move.color)) then return
    if verbose then println("Found "+move.color+" at "+move.position)
    for toClear <- connectedStones(Move(move.x, move.y, move.z, !move.color)) do {
      goban.setStone(Move(toClear.position, Color.Empty))
      captures = captures.appended(toClear)
    }
  }

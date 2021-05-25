package go3d

import collection.mutable

def newGame(size: Int): Game = Game(size, newGoban(size), Array(), Map[Int, List[Move]]())

class Game(val size: Int, val goban: Goban, val moves: Array[Move | Pass],
           val captures: Map[Int, List[Move]]) extends GoGame:

  def captures(color: Color): Int = captures.values.filter(_(0).color == color).flatten.size
  def lastCapture: List[Move] = if captures.isEmpty then List() else captures.last._2

  def at(pos: Position): Color = goban.at(pos)
  def at(x: Int, y: Int, z: Int): Color = at(Position(x, y, z))

  def makeMove(move: Move | Pass): Game =
    move match
      case p: Pass =>
        if gameOver(p) then throw GameOver(this)
        return Game(size, goban, moves.appended(move), captures)
      case m: Move =>
        checkValid(m)
        val newboard = setStone(m)
        return Game(size, newboard.goban, moves.appended(move), newboard.captures)

  def checkValid(move: Move): Unit =
    if !isDifferentPlayer(move.color) then throw WrongTurn(move)
    goban.checkValid(move)
    if isKo(move) then throw Ko(move)

  override def toString: String =
    var out = ""
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += goban.at(x, y, z)
        if z < size then out += "|"
        else if y == 1 then out += " "+Color.Black.toString*captures(Color.Black)
        else if y == 3 then out += " "+Color.White.toString*captures(Color.White)
      out += "\n"
    out

  def setStone(move: Move): Game = doCaptures(move, goban.setStone(move))

  def hasLiberties(move: Move): Boolean = goban.hasLiberties(move)

  def connectedStones(move: Move): List[Move] = goban.connectedStones(move)

  def possibleMoves(color: Color): List[Position] =
    if !isDifferentPlayer(color) then return List()
    if moves.size >= size*size*size then return List()
    return goban.emptyPositions.toList.filter(isPossibleMove(_, color))

  def score: Map[Color, Int] =
    var scores = mutable.Map[Color, Int]().withDefaultValue(0)
    for color <- List(Color.Black, Color.White) do
      for pos <- goban.allPositions if at(pos) == color do scores(color) = scores(color) + 1
      scores(color) = scores(color) - captures(color)
    val emptyAreas = addToConnectedAreas(goban.emptyPositions, Set())
    println(emptyAreas)
    // TODO find boundaries of empty areas
    // TODO add size of empty areas with only one boundary color to score
    return scores.toMap

  private def addToConnectedAreas(emptyPositions: Seq[Position], areas: Set[Set[Move]]): Set[Set[Move]] =
    if emptyPositions.isEmpty then return areas
    val connected = connectedStones(Move(emptyPositions.last, Color.Empty)).toSet
    return addToConnectedAreas(emptyPositions.dropRight(1), areas + connected)

  private def isPossibleMove(emptyPos: Position, color: Color): Boolean =
    try
      if !goban.hasEmptyNeighbor(emptyPos) then checkValid(Move(emptyPos, color))
    catch case e: IllegalMove => return false
    return true

  private def gameOver(pass: Pass): Boolean = moves.nonEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(color: Color): Boolean = moves.isEmpty || moves.last.color != color

  private def isKo(move: Move): Boolean =
    captures.nonEmpty && lastCapture.length == 1 && lastCapture(0) == move

  private def doCaptures(move: Move, board: Goban): Game =
    val newBoard = captureNeighbors(board, board.neighbors(move.position), move.color)
    val captured = (board-newBoard).toList
    val newCaptures = if captured.nonEmpty then captures + (moves.length -> captured) else captures
    return Game(size, newBoard, moves, newCaptures)

  private def captureNeighbors(board: Goban, neighbors: Seq[Position], color: Color): Goban =
    if neighbors.isEmpty then return board
    val newBoard = board.checkAndClear(Move(neighbors.last, color))
    return captureNeighbors(newBoard, neighbors.dropRight(1), color)

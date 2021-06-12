package go3d

import collection.mutable

def newGame(size: Int): Game = Game(size, newGoban(size), Array(), Map[Int, Array[Move]]())

class Game(val size: Int, val goban: Goban, val moves: Array[Move | Pass],
           val captures: Map[Int, Array[Move]]) extends GoGame:

  def captures(color: Color): Int = captures.values.filter(_(0).color == color).flatten.size
  def lastCapture: Array[Move] = if captures.isEmpty then Array() else captures.last._2

  override def equals(obj: Any): Boolean =
    obj match
      case g: Game => goban == g.goban && toString == g.toString
      case _ => false

  def at(pos: Position): Color = goban.at(pos)
  def at(x: Int, y: Int, z: Int): Color = at(Position(x, y, z))

  def isOver: Boolean =
    moves.length >= size * size * size || (
      moves.length >= 2 && moves.last.isInstanceOf[Pass] && moves.init.last.isInstanceOf[Pass]
    )

  def makeMove(move: Move | Pass): Game =
    move match
      case p: Pass =>
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
    var out = "\n"
    for y <- 0 to size + 1 do
      for z <- 1 to size do
        for x <- 0 to size + 1 do
          out += goban.at(x, y, z)
        if z < size then out += "|"
        else if y == 1 then out += " "+Black.toString*captures(Black)
        else if y == 3 then out += " "+White.toString*captures(White)
      out += "\n"
    for (move, caps) <- captures do
      out += s"$move: ${caps.toList}\n"
    out += moves.toList.toString
    out

  def setStone(move: Move): Game = doCaptures(move, goban.setStone(move))

  def hasLiberties(move: Move): Boolean = goban.hasLiberties(move)

  def connectedStones(move: Move): Set[Move] = goban.connectedStones(move)

  def possibleMoves(color: Color): List[Position] =
    if !isDifferentPlayer(color) then return List()
    if moves.size >= size*size*size then return List()
    if moves.isEmpty && color == White then return List()
    if moves.nonEmpty && color == moves.last.color then return List()
    return goban.emptyPositions.toList.filter(isPossibleMove(_, color))

  def score: Map[Color, Int] =
    var scores = mutable.Map[Color, Int]().withDefaultValue(0)
    for color <- List(Black, White) do
      for pos <- goban.allPositions if at(pos) == color do scores(color) = scores(color) + 1
      scores(color) = scores(color) - captures(color)
    val emptyAreas = addToConnectedAreas(goban.emptyPositions, Set())
    for area <- emptyAreas do
      boundaryColor(area) match
        case Some(color) => scores(color) = scores(color) + area.size
        case None =>
    return scores.toMap

  private def boundaryColor(area: Set[Move]): Option[Color] =
    var boundaryColors = mutable.Set[Color]()
    for stone <- area do
      for neighbor <- goban.neighbors(stone.position) if at(neighbor) != stone.color do
        boundaryColors.add(at(neighbor))
    if boundaryColors.size == 1 then return Some(boundaryColors.head)
    return None

  private def addToConnectedAreas(emptyPositions: Seq[Position], areas: Set[Set[Move]]): Set[Set[Move]] =
    if emptyPositions.isEmpty then return areas
    val connected = connectedStones(Move(emptyPositions.last, Empty))
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
    val captured = (board-newBoard).toArray
    val newCaptures = if captured.nonEmpty then captures + (moves.length -> captured) else captures
    return Game(size, newBoard, moves, newCaptures)

  private def captureNeighbors(board: Goban, neighbors: Seq[Position], color: Color): Goban =
    if neighbors.isEmpty then return board
    val newBoard = board.checkAndClear(Move(neighbors.last, color))
    return captureNeighbors(newBoard, neighbors.dropRight(1), color)

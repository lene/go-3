package go3d

import collection.mutable

object Game:
  def start(size: Int): Game = Game(size, Goban.start(size), Array(), Map[Int, Array[Move]]())

class Game(val size: Int, val goban: Goban, val moves: Array[Move | Pass],
           val captures: Map[Int, Array[Move]]) extends GoGame:

  def captures(color: Color): Int = captures.values.filter(_(0).color != color).flatten.size
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
  
  def isTurn(color: Color): Boolean =
    if moves.isEmpty then color == Black else color != moves.last.color

  def makeMove(move: Move | Pass): Game =
    move match
      case _: Pass => Game(size, goban, moves.appended(move), captures)
      case m: Move =>
        checkValid(m)
        val newboard = setStone(m)
        Game(size, newboard.goban, moves.appended(move), newboard.captures)

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
        else if y == 1 then out += " "+White.toString*captures(Black) // Black captures White stones
        else if y == 3 then out += " "+Black.toString*captures(White) // White captures Black stones
      out += "\n"
    for (move, caps) <- captures.toSeq.sortBy(x => x._1) do out += s"$move: ${caps.toList}\n"
    out += score.toString
    out

  def setStone(move: Move): Game = doCaptures(move, goban.setStone(move))

  def hasLiberties(move: Move): Boolean = goban.hasLiberties(move)

  def connectedStones(move: Move): Set[Move] = goban.connectedStones(move)

  def possibleMoves(color: Color): List[Position] =
    if !isDifferentPlayer(color) then return List()
    if moves.length >= size*size*size then return List()
    if moves.isEmpty && color == White then return List()
    if moves.isEmpty && color == Black then return goban.allPositions.toList
    if moves.nonEmpty && color == moves.last.color then return List()
    goban.emptyPositions.toList.filter(isPossibleMove(_, color))

  def moveColor: Color = if moves.isEmpty then Black else !moves.last.color

  def score: Map[Color, Int] =
    val scores = mutable.Map[Color, Int]().withDefaultValue(0)
    for color <- Seq(Black, White) do
      for pos <- goban.allPositions if at(pos) == color do scores(color) = scores(color) + 1
      scores(color) = scores(color) + captures(color)
    val emptyAreas = addToConnectedAreas(goban.emptyPositions, Set())
    for area <- emptyAreas do
      boundaryColor(area) match
        case Some(color) => scores(color) = scores(color) + area.size
        case None =>
    scores.toMap

  private def boundaryColor(area: Set[Move]): Option[Color] =
    val boundaryColors = mutable.Set[Color]()
    for stone <- area do
      for neighbor <- goban.neighbors(stone.position) if at(neighbor) != stone.color do
        boundaryColors.add(at(neighbor))
    if boundaryColors.size == 1 then return Some(boundaryColors.head)
    None

  private def addToConnectedAreas(emptyPositions: Seq[Position], areas: Set[Set[Move]]): Set[Set[Move]] =
    if emptyPositions.isEmpty then return areas
    val connected = connectedStones(Move(emptyPositions.last, Empty))
    addToConnectedAreas(emptyPositions.dropRight(1), areas + connected)

  private def isPossibleMove(emptyPos: Position, color: Color): Boolean =
    try
      if !goban.hasEmptyNeighbor(emptyPos) then checkValid(Move(emptyPos, color))
    catch case e: IllegalMove => return false
    true

  private def gameOver(pass: Pass): Boolean = moves.nonEmpty && moves.last.isInstanceOf[Pass]

  private def isDifferentPlayer(color: Color): Boolean = moves.isEmpty || moves.last.color != color

  private def isKo(move: Move): Boolean =
    captures.nonEmpty && lastCapture.length == 1 && lastCapture(0) == move

  private def doCaptures(move: Move, board: Goban): Game =
    val newBoard = captureNeighbors(board, board.neighbors(move.position), move.color)
    val newMoves = moves.appended(move)
    val captured = (board-newBoard).toArray
    val newCaptures = if captured.nonEmpty then captures + (moves.length -> captured) else captures
    Game(size, newBoard, newMoves, newCaptures)

  private def captureNeighbors(board: Goban, neighbors: Seq[Position], color: Color): Goban =
    if neighbors.isEmpty then return board
    val newBoard = board.checkAndClear(Move(neighbors.last, color))
    captureNeighbors(newBoard, neighbors.dropRight(1), color)

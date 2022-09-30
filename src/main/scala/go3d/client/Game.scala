package go3d.client

import go3d.{Color, Empty, Game, Move, Position}

extension (game: Game)
  def totalNumLiberties(color: Color): Int =
    game.goban.emptyPositions.count(game.hasNeighborOfColor(_, color))

  def hasNeighborOfColor(pos: Position, color: Color): Boolean =
    game.goban.neighbors(pos).exists(game.at(_) == color)

  def getStones(color: Color): Seq[Position] =
    game.moves.filter(m => m.color == color).collect { case m: Move => m }.map(m => m.position).toIndexedSeq

  def getFreeNeighbors(color: Color): Set[Position] =
    game.getStones(color).foldLeft(Set[Position]())(
      (neighbors, position) => neighbors ++ game.goban.neighborsOfColor(position, Empty)
    )

  def getAreas(color: Color): Set[Set[Position]] =
    game.getStones(color).foldLeft(Set[Set[Position]]())(
      (areas, position) => areas + game.connectedStones(Move(position, color)).map(m => m.position)
    )

  def liberties(color: Color, area: Set[Position]): Int =
    area.foreach(p => assert(color == game.at(p)))
    game.goban.numLiberties(area)

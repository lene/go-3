package go3d.client

import go3d.{Color, Empty, Game, Move, Position}

extension (game: Game)
  def totalNumLiberties(col: Color): Int =
//    game.goban.emptyPositions.count(game.hasNeighborOfColor(_, col))
    game.goban.numLiberties(col)

  def hasNeighborOfColor(pos: Position, color: Color): Boolean =
    game.goban.neighbors(pos).exists(game.at(_) == color)

  def getStones(col: Color): Seq[Position] =
    game.moves.filter(m => m.color == col).collect { case m: Move => m }.map(m => m.position).toIndexedSeq

  def getFreeNeighbors(col: Color): Set[Position] =
    game.getStones(col).foldLeft(Set[Position]())(
      (neighbors, position) => neighbors ++ game.goban.neighborsOfColor(position, Empty)
    )

  def getAreas(col: Color): Set[Set[Position]] =
    game.getStones(col).foldLeft(Set[Set[Position]]())(
      (areas, position) => areas + game.connectedStones(Move(position, col)).map(m => m.position)
    )

  def liberties(col: Color, area: Set[Position]): Int =
    area.foreach(p => assert(col == game.at(p)))
    game.goban.numLiberties(area)

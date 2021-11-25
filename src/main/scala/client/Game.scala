package go3d.client

import go3d.{Color, Game, Position}

extension (game: Game)
  def totalNumLiberties(color: Color): Int =
    game.goban.emptyPositions.count(game.hasNeighborOfColor(_, color))

  def hasNeighborOfColor(pos: Position, color: Color): Boolean =
    game.goban.neighbors(pos).exists(game.at(_) == color)

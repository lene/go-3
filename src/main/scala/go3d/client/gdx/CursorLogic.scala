package go3d.client.gdx

import go3d.{Color, Game, Position}

/** Extension methods for calculating cursor positions from game state. */
extension (game: Game)
  def playerLastMove(playerColor: Option[Color]): Option[Position] =
    playerColor.flatMap { color =>
      game.moves.findLast(_.color == color).flatMap(_.optionalPosition)
    }

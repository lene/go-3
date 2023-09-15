package go3d.server

import go3d.{Color, Game}

case class SaveGame(game: Game, players: Map[Color, Player])

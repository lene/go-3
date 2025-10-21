package go3d.server

import go3d.Color
import go3d.Game

case class SaveGame(game: Game, players: Map[Color, Player])

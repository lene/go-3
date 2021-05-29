package go3d.server

import go3d.Color
import scala.collection.mutable

var Players: mutable.Map[String, mutable.Map[Color, Player]] = mutable.Map()

def registerPlayer(color: Color, gameId: String, token: String): Player =
  val player = Player(color, gameId, token)
  if !Players.contains(gameId) then Players(gameId) = mutable.Map()
  if Players(gameId).contains(color) then
    throw RuntimeException(s"color $color already taken for game $gameId")
  Players(gameId)(color) = player
  return player

class Player(val color: Color, val gameId: String, val token: String)

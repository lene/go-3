package go3d.server

import go3d.Color
import scala.collection.mutable

var Players: mutable.Map[String, Map[Color, Player]] = mutable.Map()

def registerPlayer(color: Color, gameId: String, token: String): Player =
  val player = Player(color, gameId, token)
  if !Players.contains(gameId) then Players = Players.concat(Map(gameId -> Map()))
  if Players(gameId).contains(color) then throw DuplicateColor(gameId, color)
  Players(gameId) = Players(gameId) + (color -> player)
  return player

case class Player(val color: Color, val gameId: String, val token: String)

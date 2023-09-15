package go3d.server

import go3d.{Color, Black, White}

import scala.collection.mutable

object Players:

  private val activePlayers: mutable.Map[String, Map[Color, Player]] = mutable.Map()

  def apply(gameId: String): Map[Color, Player] = activePlayers(gameId)
  def get(gameId: String): Option[Map[Color, Player]] = activePlayers.get(gameId)

  def update(gameId: String, players: Map[Color, Player]): Unit = activePlayers(gameId) = players

  def openGames(): Array[String] =
    activePlayers.filter(p => p._2.contains(Black) && !p._2.contains(White)).keys.toArray

  def isDuplicate(gameId: String, color: Color): Boolean =
    activePlayers.contains(gameId) && activePlayers(gameId).contains(color)

  def isReady(gameId: String): Boolean =
    activePlayers.contains(gameId) && activePlayers(gameId).size == 2
    
  def register(gameId: String, color: Color, token: String): Unit =
    if !activePlayers.contains(gameId) then activePlayers(gameId) = Map()
    if isDuplicate(gameId, color) then throw DuplicateColor(gameId, color)
    val player = Player(color, gameId, token)
    activePlayers(gameId) = activePlayers(gameId) + (color -> player)

  def unregister(gameId: String): Unit = activePlayers.remove(gameId)
  
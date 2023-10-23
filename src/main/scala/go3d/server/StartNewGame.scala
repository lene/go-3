package go3d.server

import com.typesafe.scalalogging.LazyLogging

case class StartNewGame(boardSize: Int) extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val gameId = Games.register(boardSize)
    logger.info(s"New game $gameId, size $boardSize".replaceAll("[\r\n]", " "))
    GameCreatedResponse(gameId, boardSize)

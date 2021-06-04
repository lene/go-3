package go3d.server

import go3d.{Game, Color}

class GoResponse
case class ErrorResponse(err: String) extends GoResponse
case class GameCreatedResponse(id: String, size: Int) extends GoResponse
case class RequestDebugInfo(headers: Map[String, String], query: String, pathInfo: String) 
  extends GoResponse
case class PlayerRegisteredResponse(game: Game, color: Color, authToken: String,
                                    debug: RequestDebugInfo) extends GoResponse
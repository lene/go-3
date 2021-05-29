package go3d.server

import go3d.{Game, Color}

class GoResponse
class ErrorResponse(val err: String) extends GoResponse
class GameCreatedResponse(val id: String, val size: Int) extends GoResponse
class RequestDebugInfo(val headers: Map[String, String], val query: String, val pathInfo: String)
  extends GoResponse
class PlayerRegisteredResponse(val game: Game, val color: Color, val authToken: String,
                               val debug: RequestDebugInfo) extends GoResponse
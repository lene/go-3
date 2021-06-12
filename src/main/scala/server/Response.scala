package go3d.server

import go3d.{Color, Game, Position}

import java.util.Collections
import javax.servlet.http.HttpServletRequest

class GoResponse
case class ErrorResponse(err: String) extends GoResponse
case class GameCreatedResponse(id: String, size: Int) extends GoResponse
case class PlayerRegisteredResponse(game: Game, color: Color, authToken: String, ready: Boolean,
                                    debug: RequestInfo) extends GoResponse
case class StatusResponse(game: Game, moves: List[Position], ready: Boolean, debug: RequestInfo) extends GoResponse

package go3d.server

import go3d.Color

class ServerException(val message: String) extends RuntimeException
class MalformedRequest(val pathInfo: String)
  extends ServerException(message = s"bad request: $pathInfo")
class NonexistentGame(val gameId: String, keys: List[String])
  extends ServerException(message = s"game $gameId not found in $keys")
class DuplicateColor(val gameId: String, color: Color)
  extends ServerException(message = s"color $color already registered in game $gameId")

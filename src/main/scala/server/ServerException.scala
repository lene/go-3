package go3d.server

class ServerException(val message: String) extends RuntimeException
class MalformedRequest(val pathInfo: String)
  extends ServerException(message = s"bad request: $pathInfo")
class NonexistentGame(val gameId: String, keys: List[String])
  extends ServerException(message = s"game $gameId not found in $keys")

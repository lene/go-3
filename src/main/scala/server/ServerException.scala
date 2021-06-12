package go3d.server

import go3d.Color

class ServerException(val message: String) extends RuntimeException:
  override def toString = message
class MalformedRequest(val pathInfo: String)
  extends ServerException(message = s"bad request: $pathInfo")
class AuthorizationMissing(headers: Map[String, String])
  extends ServerException(s"authorization missing in headers: $headers")
class AuthorizationMethodWrong(method: String)
  extends ServerException(s"authorization method $method not supported")
class NonexistentGame(val gameId: String, keys: List[String])
  extends ServerException(message = s"game $gameId not found in $keys")
class DuplicateColor(val gameId: String, color: Color)
  extends ServerException(message = s"color $color already registered in game $gameId")
class PlayerNotFoundByToken(gameId: String, token: String)
  extends ServerException(message = s"player with token $token not found in game $gameId")
class NotReadyToSet(gameId: String, token: String)
  extends ServerException(message = s"player with token $token not ready to set in game $gameId")
class ReadSaveGameError(val jsonError: String)
  extends ServerException(message = s"restoring game failed: $jsonError")

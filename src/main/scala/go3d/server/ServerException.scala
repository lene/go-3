package go3d.server

import go3d.{Color, GoException}

class ServerException(override val message: String) extends GoException(message):
  override def toString: String = message

class AuthorizationError(val msg: String) extends ServerException(message = s"authorization $msg")
class AuthorizationMissing(headers: Map[String, String])
  extends AuthorizationError(s"missing in headers: $headers")
class AuthorizationMethodWrong(method: String)
  extends AuthorizationError(s"method $method not supported")
class PlayerNotFoundByToken(gameId: String, token: String)
  extends AuthorizationError(s"for player with token $token not found in game $gameId")

class MalformedRequest(val pathInfo: String)
  extends ServerException(message = s"bad request: $pathInfo")
class RequestTooLong(allowed: Int, actual: Int)
  extends ServerException(s"request too long: $actual > $allowed")
class NonexistentGame(val gameId: String, keys: List[String])
  extends ServerException(message = s"game $gameId not found in $keys")
class DuplicateColor(val gameId: String, color: Color)
  extends ServerException(message = s"color $color already registered in game $gameId")
class NotReadyToSet(gameId: String, color: Color)
  extends ServerException(message = s"player $color not ready to set in game $gameId")
class ReadSaveGameError(val jsonError: String)
  extends ServerException(message = s"restoring game failed: $jsonError")
class JsonDecodeError(msg: String) extends ServerException(message = s"reading json failed: $msg")

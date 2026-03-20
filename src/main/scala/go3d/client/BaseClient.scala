package go3d.client

import go3d.Color
import go3d.Move
import go3d.server.GameCreatedResponse
import go3d.server.PlayerRegisteredResponse
import go3d.server.ServerException
import go3d.server.StatusResponse
import go3d.server.decodeGameCreatedResponse
import go3d.server.decodePlayerRegisteredResponse
import go3d.server.decodeStatusResponse
import io.circe.parser._

import scala.io.Source
import scala.util.{Failure, Success, Try}

case class BaseClient(serverURL: String, id: String, token: Option[String], playerColor: Option[Color]):
  def status: Try[StatusResponse] = getSR(s"$serverURL/status/$id", headers)

  def set(x: Int, y: Int, z: Int): Try[StatusResponse] = getSR(s"$serverURL/set/$id/$x/$y/$z", headers)
  def set(move: Move): Try[StatusResponse] = set(move.x, move.y, move.z)

  def pass: Try[StatusResponse] = getSR(s"$serverURL/pass/$id", headers)

  private[client] def headers: Map[String, String] =
    token.fold(Map())(str => Map("Authentication" -> s"Bearer $str"))

object BaseClient:
  def create(serverURL: String, size: Int, color: Color): Try[BaseClient] =
    getGCR(s"$serverURL/new/$size").flatMap(response => register(serverURL, response.id, color))

  def register(serverURL: String, id: String, color: Color): Try[BaseClient] =
    getPRR(s"$serverURL/register/$id/$color").map(response =>
      BaseClient(serverURL, id, Some(response.authToken), Some(response.color))
    )

def getJson(url: String): Source = Source.fromURL(url)

def getPRR(url: String): Try[PlayerRegisteredResponse] =
  val json = getJson(url).mkString
  decode[PlayerRegisteredResponse](json) match
    case Right(response) => Success(response)
    case Left(error) => Failure(ServerException(error.getMessage))

def getGCR(url: String): Try[GameCreatedResponse] =
  val json = getJson(url).mkString
  decode[GameCreatedResponse](json) match
    case Right(response) => Success(response)
    case Left(error) => Failure(ServerException(error.getMessage))

def getSR(url: String, header: Map[String, String]): Try[StatusResponse] =
  Try(requests.get(url, headers = header, readTimeout = 30000, connectTimeout = 30000)).flatMap { response =>
    val json = response.text()
    decode[StatusResponse](json) match
      case Right(sr) => Success(sr)
      case Left(error) => Failure(ServerException(error.getMessage))
  }

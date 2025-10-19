package go3d.client

import scala.io.Source
import io.circe.parser._

import go3d.{Color, Move}
import go3d.server.{
  StatusResponse, GameCreatedResponse, PlayerRegisteredResponse, ServerException,
  decodeStatusResponse, decodeGameCreatedResponse, decodePlayerRegisteredResponse
}

case class BaseClient(serverURL: String, id: String, token: Option[String], playerColor: Option[Color]):
  def status: StatusResponse = getSR(s"$serverURL/status/$id", headers)

  def set(x: Int, y: Int, z: Int): StatusResponse = getSR(s"$serverURL/set/$id/$x/$y/$z", headers)
  def set(move: Move): StatusResponse = set(move.x, move.y, move.z)

  def pass: StatusResponse = getSR(s"$serverURL/pass/$id", headers)

  private[client] def headers: Map[String, String] =
    token.fold(Map())(str => Map("Authentication" -> s"Bearer $str"))

object BaseClient:
  def create(serverURL: String, size: Int, color: Color): BaseClient =
    val response = getGCR(s"$serverURL/new/$size")
    register(serverURL, response.id, color)

  def register(serverURL: String, id: String, color: Color): BaseClient =
    val response = getPRR(s"$serverURL/register/$id/$color")
    BaseClient(serverURL, id, Some(response.authToken), Some(response.color))

def getJson(url: String): Source = Source.fromURL(url)

def getPRR(url: String): PlayerRegisteredResponse =
  val json = getJson(url).mkString
  decode[PlayerRegisteredResponse](json) match
    case Right(response) => response
    case Left(error) => throw ServerException(error.getMessage)

def getGCR(url: String): GameCreatedResponse =
  val json = getJson(url).mkString
  decode[GameCreatedResponse](json) match
    case Right(response) => response
    case Left(error) => throw ServerException(error.getMessage)

def getSR(url: String, header: Map[String, String]): StatusResponse =
  val response = requests.get(url, headers = header, readTimeout = 30000, connectTimeout = 30000)
  val json = response.text()
  decode[StatusResponse](json) match
    case Right(response) => response
    case Left(error) => throw ServerException(error.getMessage)

package go3d.client

import go3d.{Color, Move}
import go3d.server.{StatusResponse, GameCreatedResponse, PlayerRegisteredResponse, ServerException}
import go3d.server.{
  GoResponse, decodeStatusResponse, decodeGameCreatedResponse, decodePlayerRegisteredResponse
}

import scala.io.Source
import io.circe.parser._

case class BaseClient(serverURL: String, id: String, token: String):
  def status: StatusResponse =
    getSR(s"$serverURL/status/$id", Map("Authentication" -> s"Bearer $token"))

  def set(x: Int, y: Int, z: Int): StatusResponse =
    getSR(s"$serverURL/set/$id/$x/$y/$z", Map("Authentication" -> s"Bearer $token"))
  def set(move: Move): StatusResponse = set(move.x, move.y, move.z)

  def pass: StatusResponse =
    getSR(s"$serverURL/pass/$id", Map("Authentication" -> s"Bearer $token"))

object BaseClient:
  def create(serverURL: String, size: Int, color: Color): BaseClient =
    val response = getGCR(s"$serverURL/new/$size")
    register(serverURL, response.id, color)

  def register(serverURL: String, id: String, color: Color): BaseClient =
    val response = getPRR(s"$serverURL/register/$id/$color")
    BaseClient(serverURL, id, response.authToken)

def getJson(url: String): Source = Source.fromURL(url)

def getPRR(url: String): PlayerRegisteredResponse =
  val json = getJson(url).mkString
  val result = decode[PlayerRegisteredResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def getGCR(url: String): GameCreatedResponse =
  val json = getJson(url).mkString
  val result = decode[GameCreatedResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def getSR(url: String, header: Map[String, String]): StatusResponse =
  val response = requests.get(url, headers = header, connectTimeout = 30000)
  val json = response.text()
  val result = decode[StatusResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

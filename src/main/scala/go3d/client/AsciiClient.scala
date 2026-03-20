package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.BadColor
import go3d.Black
import go3d.Color
import go3d.White
import go3d.server.StatusResponse
import requests._

import scala.annotation.tailrec
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}

object AsciiClient extends InteractiveClient with LazyLogging:

  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"

  @tailrec
  def mainLoop(client: BaseClient): Unit =
    logger.info(
      s"server: ${client.serverURL} game: ${client.id} token: ${client.token.fold("")(str => str)}"
    )
    val status = waitUntilReady(client).get
    logger.info(s"\n${status.game.goban}")
    if status.game.moves.nonEmpty then logger.info(s"last move: ${status.game.moves.last}")
    Try {
      val input = readLine("your input: ")
      val Array(command, args) = (input+" ").split("\\s+", 2)
      val statusResponse: Option[StatusResponse] = command match
        case "set"|"s" => Some(set(client, args).get)
        case "pass"|"p" => Some(pass(client).get)
        case "status"|"st" => Some(getStatus(client).get)
        case "exit" =>
          logger.info("Exiting. If you want to reconnect to the game, enter")
          logger.info(
            s"$$ sbt \"runMain go3d.client.AsciiClient --server ${client.serverURL} --game-id ${client.id} --token ${client.token}\""
          )
          exit(0)
          None
        case _ =>
          logger.warn(
            s"\"$command\" not understood - use \"set|s\", \"pass|p\", \"status|st\" or \"exit\"!"
          )
          None
      statusResponse.foreach(sr => if sr.over then exit(0))
    }.recover {
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => logger.warn(e.message)
      case e: NumberFormatException => logger.warn(s"Not a number: ${e.getMessage}, set again!")
    }
    mainLoop(client)

  def set(client: BaseClient, args: String): Try[StatusResponse] =
    val Array(x, y, z) = args.split("\\s+", 3).map(s => s.trim.toInt)
    logger.info(s"set $x $y $z")
    client.set(x, y, z)

  def pass(client: BaseClient): Try[StatusResponse] = client.pass

  def getStatus(client: BaseClient): Try[StatusResponse] = client.status

def colorFromString(string: String): Try[Color] =
  string.toLowerCase match
    case "@"|"black"|"b" => Success(Black)
    case "o"|"white"|"w" => Success(White)
    case _ => Failure(BadColor(string(0)))

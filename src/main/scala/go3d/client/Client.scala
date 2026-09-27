package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.BadColor
import go3d.Color
import go3d.server.StatusResponse

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import scala.annotation.tailrec
import scala.util.{Success, Try}

trait ClientTrait:
  def mainLoop(client: BaseClient): Try[Unit]
  def parseArgs(args: Array[String]): Try[BaseClient]
  def waitUntilReady(client: BaseClient): Try[StatusResponse]
  def init(): Unit

abstract class Client extends ClientTrait with LazyLogging:

  protected def getPlayerColor(serverURL: String, gameId: String, token: String): Option[Color] =
    logger.info(s"getPlayerColor called with serverURL=$serverURL, gameId=$gameId, token=$token")
    val tempClient = BaseClient(serverURL, gameId, Some(token), None)
    val color = tempClient.status.map(_.playerColor).getOrElse(None)
    logger.info(s"getPlayerColor returning: $color")
    color

  def main(args: Array[String]): Unit =
    parseArgs(args).flatMap { client =>
      init()
      mainLoop(client)
    }.recover {
      case e: UnknownHostException => exit(s"unknown host: ${e.getMessage}", 1)
      case e: ConnectException => exit(s"connection problem: ${e.getMessage}", 1)
      case e: NumberFormatException => exit(s"not a number: ${e.getMessage}", 1)
      case e: IOException => exit(s"${e.getMessage}", 1)
      case _: BadColor => exit(s"not a color, must be either black/b/@ or white/w/O", 1)
      case e: NoSuchElementException => exit(s"missing argument: --${e.getMessage}", 1)
      case e: IllegalArgumentException => exit(s"missing argument: ${e.getMessage}", 1)
      case e: Throwable => exit(s"unexpected error: ${e.getMessage} ${e.getStackTrace.mkString("\n")}", 1)
    }

  def init(): Unit = {}

  /// Polls the server every `waitMs` until the game is ready for `client`, calling `onWait` with
  /// the last status before each wait.
  @tailrec
  protected final def pollUntilReady(
    client: BaseClient, status: StatusResponse, waitMs: Int, onWait: StatusResponse => Unit
  ): Try[StatusResponse] =
    if status.ready then Success(status)
    else
      onWait(status)
      Thread.sleep(waitMs)
      client.status match
        case Success(next) => pollUntilReady(client, next, waitMs, onWait)
        case failure => failure

  protected def exceptionToParam(e: NoSuchElementException): String =
      "--" + e.getMessage //.substring("key not found: ".length).replace('_', '-')

  def exit(message: String, status: Int): Unit =
    if message.nonEmpty then logger.info(message)
    System.exit(status)
  def exit(status: Int): Unit = exit("", status)

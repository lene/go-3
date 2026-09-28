package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.server.StatusResponse
import org.rogach.scallop._
import org.rogach.scallop.exceptions.RequiredOptionNotFound

import java.util.NoSuchElementException
import scala.util.{Failure, Success, Try}

class ClientCLIConf(arguments: Seq[String]) extends ScallopConf(arguments):
  val size = opt[Int](required = false)
  val color = opt[String](required = false)
  val gameId = opt[String](required = false)
  val token = opt[String](required = false)
  val url = opt[String](required = false)
  val server = opt[String](required = false)
  val port = opt[Int](required = false)
  val cursorFadeSeconds = opt[Float](required = false, default = Some(10.0f))
  val replay      = toggle(default = Some(false))
  val from        = opt[Int](required = false, default = Some(0))
  val to          = opt[Int](required = false, default = Some(Int.MaxValue))
  val replaySpeed = opt[Float](name = "replay-speed", required = false, default = Some(1.0f))
  requireOne(size, gameId)
  mutuallyExclusive(url, server)
  mutuallyExclusive(url, port)
  dependsOnAll(size, List(color))
  dependsOnAll(token, List(gameId))
  dependsOnAll(from,        List(replay))
  dependsOnAll(to,          List(replay))
  dependsOnAll(replaySpeed, List(replay))
  dependsOnAll(replay,      List(gameId))
  verify()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def onError(e: Throwable): Unit = e match
    case RequiredOptionNotFound(optionName) => throw NoSuchElementException(optionName)
    case other => throw other

abstract case class InteractiveClient(pollInterval: Int = 500) extends Client with LazyLogging:

  def parseArgs(args: Array[String]): Try[BaseClient] =
    Try(new ClientCLIConf(args.toList)).flatMap { conf =>
      Client.serverUrl(conf.url.toOption, conf.server.toOption, conf.port.toOption)
        .flatMap(connect(conf, _))
    }

  private def connect(conf: ClientCLIConf, serverURL: String): Try[BaseClient] =
    if conf.size.isSupplied then
      colorFromString(conf.color())
        .flatMap(color => BaseClient.create(serverURL, conf.size(), color))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        Success(BaseClient(
          serverURL, conf.gameId(), conf.token.toOption,
          getPlayerColor(serverURL, conf.gameId(), conf.token())
        ))
      else if conf.color.isSupplied then
        colorFromString(conf.color())
          .flatMap(color => BaseClient.register(serverURL, conf.gameId(), color))
      else Success(BaseClient(serverURL, conf.gameId(), None, None))
    else Failure(RuntimeException("Must provide either size or gameId"))

  override def waitUntilReady(client: BaseClient): Try[StatusResponse] =
    val spinner = Iterator.continually("/-\\|".toList).flatten
    client.status.flatMap(
      pollUntilReady(client, _, pollInterval, _ => print("\b" + spinner.next().toString))
    )

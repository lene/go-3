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
  val server = opt[String](required = true)
  val port = opt[Int](required = true)
  val cursorFadeSeconds = opt[Float](required = false, default = Some(10.0f))
  requireOne(size, gameId)
  dependsOnAll(size, List(color))
  dependsOnAll(token, List(gameId))
  verify()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def onError(e: Throwable): Unit = e match
    case RequiredOptionNotFound(optionName) => throw NoSuchElementException(optionName)
    case other => throw other

abstract case class InteractiveClient(pollInterval: Int = 500) extends Client with LazyLogging:

  def parseArgs(args: Array[String]): Try[BaseClient] =
    Try {
      val conf = new ClientCLIConf(args.toList)
      val serverURL = s"http://${conf.server()}:${conf.port()}"
      if conf.size.isSupplied then
        colorFromString(conf.color()).flatMap(color => BaseClient.create(serverURL, conf.size(), color)).get
      else if conf.gameId.isSupplied then
        if conf.token.isSupplied then
          BaseClient(
            serverURL, conf.gameId(), conf.token.toOption,
            getPlayerColor(serverURL, conf.gameId(), conf.token())
          )
        else if conf.color.isSupplied then
          colorFromString(conf.color()).flatMap(color => BaseClient.register(serverURL, conf.gameId(), color)).get
        else BaseClient(serverURL, conf.gameId(), None, None)
      else sys.error("Must provide either size or gameId")
    }

  override def waitUntilReady(client: BaseClient): Try[StatusResponse] =
    client.status.flatMap { initialStatus =>
      var status = initialStatus
      var index = 0
      while !status.ready do
        index = index + 1
        print("\b" + "/-\\|"(index % 4))
        Thread.sleep(pollInterval)
        status = client.status.get
      Success(status)
    }

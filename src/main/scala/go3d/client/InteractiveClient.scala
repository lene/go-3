package go3d.client

import com.typesafe.scalalogging.LazyLogging

import java.util.NoSuchElementException
import org.rogach.scallop.*
import org.rogach.scallop.exceptions.RequiredOptionNotFound
import go3d.server.{emptyResponse, StatusResponse}
import go3d.Color

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

  override def onError(e: Throwable): Unit = e match
    case RequiredOptionNotFound(optionName) => throw NoSuchElementException(optionName)
    case other => throw other

abstract case class InteractiveClient(pollInterval: Int = 500) extends Client with LazyLogging:

  def parseArgs(args: Array[String]): BaseClient =
    val conf = new ClientCLIConf(args.toList)
    val serverURL = s"http://${conf.server()}:${conf.port()}"
    if conf.size.isSupplied then
      BaseClient.create(serverURL, conf.size(), colorFromString(conf.color()))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        BaseClient(
          serverURL, conf.gameId(), conf.token.toOption,
          getPlayerColor(serverURL, conf.gameId(), conf.token())
        )
      else if conf.color.isSupplied then
        BaseClient.register(serverURL, conf.gameId(), colorFromString(conf.color()))
      else BaseClient(serverURL, conf.gameId(), None, None)
    else
      throw new IllegalArgumentException("Must provide either size or gameId")

  override  def waitUntilReady(client: BaseClient): StatusResponse =
    var status = emptyResponse
    var index = 0
    while !status.ready do
      index = index+1
      print("\b" + "/-\\|"(index % 4))
      status = client.status
      if !status.ready then Thread.sleep(pollInterval)
    status


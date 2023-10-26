package go3d.client

import com.typesafe.scalalogging.LazyLogging
import java.security.SecureRandom
import java.util.NoSuchElementException
import org.rogach.scallop._
import org.rogach.scallop.exceptions.RequiredOptionNotFound
import org.http4s.Status
import requests.RequestFailedException

import go3d.{Game, Position}
import go3d.server.{StatusResponse, emptyResponse}

class BotClientCLIConf(arguments: Seq[String]) extends ScallopConf(arguments):
  val size = opt[Int](required = false)
  val color = opt[String](required = false)
  val gameId = opt[String](required = false)
  val token = opt[String](required = false)
  val server = opt[String](required = true)
  val port = opt[Int](required = true)
  val strategy = opt[String](required = false)
  val maxThinkingTimeMs = opt[Int](required = false, default = Some(0))
  requireOne(size, gameId)
  dependsOnAll(size, List(color))
  dependsOnAll(token, List(gameId))
  verify()

  override def onError(e: Throwable): Unit = e match {
    case RequiredOptionNotFound(optionName) => throw NoSuchElementException(optionName)
    case other => throw other
  }

object BotClient extends Client with LazyLogging:

  private val PULL_WAIT_MS = 10
  var executionTimes: List[Long] = List()
  private val random: SecureRandom = SecureRandom()
  private[client] var strategies: Array[String] = Array()
  private var maxThinkingTimeMs: Int = 0
  private var game: Game = null
  private var strategy: Option[SetStrategy] = None

  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"
  /// --strategy is a comma-separated list of:
  //  closestToCenter|closestToStarPoints|maximizeOwnLiberties|minimizeOpponentLiberties

  def mainLoop(args: Array[String]): Unit =
    logger.info(
      s"server: ${client.serverURL} game: ${client.id} token: ${client.token.fold("")((str) => str)}"
    )
    val status = waitUntilReady()
    game = status.game
    if args.nonEmpty then strategy = Some(SetStrategy(game.size, strategies, maxThinkingTimeMs))
    logger.info(s"Move: ${game.moves.length} ${executionTimeString}")
    var over = false
    val startTime = System.currentTimeMillis()
    try
      over = makeOneMove(status)
    catch
      case _: Exit => exit(0)
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => checkFailedRequest(e)
    finally
      executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    if !over then mainLoop(Array())
    else logger.info(s"${client.status.game}")

  private def checkFailedRequest(e: RequestFailedException): Unit =
    logger.warn(e.message)
    if e.response.statusCode == Status.Gone.code then exit(0)
    mainLoop(Array())


  private def makeOneMove(status: StatusResponse): Boolean =
    val possible = strategy.map(_.narrowDown(status.moves, game)).getOrElse(status.moves)
    if possible.nonEmpty then
      val setPosition = randomMove(possible)
      val status = client.set(setPosition.x, setPosition.y, setPosition.z)
      if status.over then
        logger.info(s"Game over: ${status.game}")
        exit(0)
      game = status.game
      false
    else
      val status = client.pass
      if status.over then
        logger.info(s"Game over: ${status.game}")
        exit(0)
        game = status.game
      true

  private def randomMove(possible: Seq[Position]): Position =
    possible(random.nextInt(possible.length))

  def executionTimeString: String =
    if executionTimes.isEmpty then ""
    else
      val last = executionTimes.last
      val avg = executionTimes.sum / executionTimes.length
      f"(${last}ms last/${avg}ms avg)  "

  def parseArgs(args: Array[String]): Unit =
    val conf = new BotClientCLIConf(args.toList)
    val serverURL = s"http://${conf.server()}:${conf.port()}"
    if conf.size.isSupplied then
      client = BaseClient.create(serverURL, conf.size(), colorFromString(conf.color()))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        client = BaseClient(serverURL, conf.gameId(), conf.token.toOption)
      else client = BaseClient.register(serverURL, conf.gameId(), colorFromString(conf.color()))
    strategies = conf.strategy().split(',')
    maxThinkingTimeMs = conf.maxThinkingTimeMs()

  def waitUntilReady(): StatusResponse =
    var status = emptyResponse
    while !status.ready do
      status = client.status
      if status.over then
        logger.info(s"Game over: ${status.game}")
        exit(0)
      if !status.ready then Thread.sleep(PULL_WAIT_MS)
    status

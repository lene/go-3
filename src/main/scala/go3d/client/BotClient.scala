package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.Game
import go3d.Position
import go3d.server.StatusResponse
import org.http4s.Status
import org.rogach.scallop._
import org.rogach.scallop.exceptions.RequiredOptionNotFound
import requests.RequestFailedException

import java.security.SecureRandom
import java.util.NoSuchElementException

class BotClientCLIConf(arguments: Seq[String]) extends ScallopConf(arguments):
  val size = opt[Int](required = false)
  val color = opt[String](required = false)
  val gameId = opt[String](required = false)
  val token = opt[String](required = false)
  val server = opt[String](required = true)
  val port = opt[Int](required = true)
  val strategy = opt[String](required = false)
  val maxThinkingTimeMs = opt[Int](required = false, default = Some(0))
  val parallel = opt[Boolean](required = false, default = Some(false))
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
  private var parallel: Boolean = false

  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"
  /// --strategy is a comma-separated list of:
  ///   closestToCenter|closestToStarPoints|maximizeOwnLiberties|minimizeOpponentLiberties

  def mainLoop(client: BaseClient): Unit =
    logger.info(
      s"server: ${client.serverURL} game: ${client.id} token: ${client.token.fold("")((str) => str)}"
    )
    val status = waitUntilReady(client)
    var game = status.game
    val strategy = if strategies.nonEmpty then Some(SetStrategy(game.size, strategies, maxThinkingTimeMs)) else None
    logger.info(s"Move: ${game.moves.length} ${executionTimeString}")
    var over = false
    val startTime = System.currentTimeMillis()
    try
      val (newOver, newGame) = makeOneMove(client, status, game, strategy)
      over = newOver
      game = newGame
    catch
      case _: Exit => exit(0)
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => checkFailedRequest(client, e)
    finally
      executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    if !over then mainLoopRecursive(client, game, strategy)
    else logger.info(s"${client.status.game}")

  private def mainLoopRecursive(client: BaseClient, game: Game, strategy: Option[SetStrategy]): Unit =
    logger.info(s"Move: ${game.moves.length} ${executionTimeString}")
    var updatedGame = game
    var over = false
    val startTime = System.currentTimeMillis()
    try
      val status = client.status
      val (newOver, newGame) = makeOneMove(client, status, updatedGame, strategy)
      over = newOver
      updatedGame = newGame
    catch
      case _: Exit => exit(0)
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => checkFailedRequest(client, e)
    finally
      executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    if !over then mainLoopRecursive(client, updatedGame, strategy)
    else logger.info(s"${client.status.game}")

  private def checkFailedRequest(client: BaseClient, e: RequestFailedException): Unit =
    logger.warn(e.message)
    if e.response.statusCode == Status.Gone.code then exit(0)
    mainLoop(client)


  private def makeOneMove(client: BaseClient, status: StatusResponse, game: Game, strategy: Option[SetStrategy]): (Boolean, Game) =
    val possible = strategy.map(_.narrowDown(status.moves, game)).getOrElse(status.moves)
    if possible.nonEmpty then
      val setPosition = randomMove(possible)
      val newStatus = client.set(setPosition.x, setPosition.y, setPosition.z)
      if newStatus.over then
        logger.info(s"Game over: ${newStatus.game}")
        exit(0)
      (false, newStatus.game)
    else
      val newStatus = client.pass
      if newStatus.over then
        logger.info(s"Game over: ${newStatus.game}")
        exit(0)
      (true, newStatus.game)

  private def randomMove(possible: Seq[Position]): Position =
    possible(random.nextInt(possible.length))

  def executionTimeString: String =
    if executionTimes.isEmpty then ""
    else
      val last = executionTimes.last
      val avg = executionTimes.sum / executionTimes.length
      f"(${last}ms last/${avg}ms avg)  "

  def parseArgs(args: Array[String]): BaseClient =
    val conf = new BotClientCLIConf(args.toList)
    val serverURL = s"http://${conf.server()}:${conf.port()}"
    strategies = conf.strategy.toOption.fold(Array.empty[String])(_.split(','))
    maxThinkingTimeMs = conf.maxThinkingTimeMs()
    parallel = conf.parallel()

    if conf.size.isSupplied then
      BaseClient.create(serverURL, conf.size(), colorFromString(conf.color()))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        val playerColor = if conf.gameId().nonEmpty && conf.token().nonEmpty then
          getPlayerColor(serverURL, conf.gameId(), conf.token())
        else None
        BaseClient(
          serverURL, conf.gameId(), conf.token.toOption, playerColor
        )
      else BaseClient.register(serverURL, conf.gameId(), colorFromString(conf.color()))
    else
      throw new IllegalArgumentException("Must provide either size or gameId")

  def waitUntilReady(client: BaseClient): StatusResponse =
    var status = client.status
    while !status.ready do
      if status.over then
        logger.info(s"Game over: ${status.game}")
        exit(0)
      Thread.sleep(PULL_WAIT_MS)
      status = client.status
    status

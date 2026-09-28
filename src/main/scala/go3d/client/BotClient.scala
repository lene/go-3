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
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class BotClientCLIConf(arguments: Seq[String]) extends ScallopConf(arguments):
  val size = opt[Int](required = false)
  val color = opt[String](required = false)
  val gameId = opt[String](required = false)
  val token = opt[String](required = false)
  val url = opt[String](required = false)
  val server = opt[String](required = false)
  val port = opt[Int](required = false)
  val strategy = opt[String](required = false)
  val maxThinkingTimeMs = opt[Int](required = false, default = Some(0))
  val parallel = opt[Boolean](required = false, default = Some(false))
  val pollIntervalMs = opt[Int](required = false, default = Some(10))
  requireOne(size, gameId)
  mutuallyExclusive(url, server)
  mutuallyExclusive(url, port)
  dependsOnAll(size, List(color))
  dependsOnAll(token, List(gameId))
  verify()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def onError(e: Throwable): Unit = e match {
    case RequiredOptionNotFound(optionName) => throw NoSuchElementException(optionName)
    case other => throw other
  }

object BotClient extends Client with LazyLogging:

  // set from --poll-interval-ms, like the other options kept in this object
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private[client] var pollIntervalMs: Int = 10
  var executionTimes: List[Long] = List()
  private val random: SecureRandom = SecureRandom()
  private[client] var strategies: Array[String] = Array()
  private var maxThinkingTimeMs: Int = 0
  private var parallel: Boolean = false

  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"
  /// --url https://... replaces --server and --port, e.g. for the API Gateway of the Lambda server;
  /// --poll-interval-ms sets how often to poll while waiting for the opponent (default 10)
  /// --strategy is a comma-separated list of:
  ///   closestToCenter|closestToStarPoints|maximizeOwnLiberties|minimizeOpponentLiberties

  def mainLoop(client: BaseClient): Try[Unit] =
    logger.info(
      s"server: ${client.serverURL} game: ${client.id} token: ${client.token.fold("")((str) => str)}"
    )
    waitUntilReady(client).flatMap { status =>
      val game = status.game
      val strategy =
        if strategies.nonEmpty then Some(SetStrategy(game.size, strategies, maxThinkingTimeMs))
        else None
      playMoves(client, Some(status), game, strategy)
    }

  /// Plays until the game is over. `readyStatus` is a status already known to be ready, so the
  /// first move does not have to poll the server again.
  @tailrec
  private def playMoves(
    client: BaseClient, readyStatus: Option[StatusResponse], game: Game,
    strategy: Option[SetStrategy]
  ): Try[Unit] =
    logger.info(s"Move: ${game.moves.length} ${executionTimeString}")
    val startTime = System.currentTimeMillis()
    val result = readyStatus.fold(waitUntilReady(client))(Success(_))
      .flatMap(status => makeOneMove(client, status, game, strategy))
      .recoverWith {
        case _: InterruptedException => exit(1); Success((true, game))
        case e: RequestFailedException => checkFailedRequest(client, e).map(_ => (true, game))
      }
    executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    result match
      case Success((over, newGame)) =>
        if !over then playMoves(client, None, newGame, strategy)
        else Success(logger.info(s"${client.status.map(_.game).getOrElse(game)}"))
      case Failure(e) => Failure(e)

  private def checkFailedRequest(client: BaseClient, e: RequestFailedException): Try[Unit] =
    logger.warn(e.message)
    if e.response.statusCode == Status.Gone.code then exit(0)
    mainLoop(client)

  private[client] def makeOneMove(
    client: BaseClient, status: StatusResponse, game: Game, strategy: Option[SetStrategy]
  ): Try[(Boolean, Game)] =
    strategy.fold[Try[Seq[Position]]](Success(status.moves))(_.narrowDown(status.moves, game))
      .flatMap { possible =>
        val move =
          if possible.nonEmpty then
            val setPosition = randomMove(possible)
            client.set(setPosition.x, setPosition.y, setPosition.z)
          else client.pass
        move.map { newStatus =>
          if newStatus.over then
            logger.info(s"Game over: ${newStatus.game}")
            exit(0)
          // a single pass does not end the game; keep playing until the opponent passes too
          (false, newStatus.game)
        }
      }

  private def randomMove(possible: Seq[Position]): Position =
    possible(random.nextInt(possible.length))

  def executionTimeString: String =
    if executionTimes.isEmpty then ""
    else
      val last = executionTimes.last
      val avg = executionTimes.sum / executionTimes.length
      f"(${last}ms last/${avg}ms avg)  "

  def parseArgs(args: Array[String]): Try[BaseClient] =
    Try(new BotClientCLIConf(args.toList)).flatMap { conf =>
      strategies = conf.strategy.toOption.fold(Array.empty[String])(_.split(','))
      maxThinkingTimeMs = conf.maxThinkingTimeMs()
      parallel = conf.parallel()
      pollIntervalMs = conf.pollIntervalMs()
      Client.serverUrl(conf.url.toOption, conf.server.toOption, conf.port.toOption)
        .flatMap(connect(conf, _))
    }

  private def connect(conf: BotClientCLIConf, serverURL: String): Try[BaseClient] =
    if conf.size.isSupplied then
      colorFromString(conf.color())
        .flatMap(color => BaseClient.create(serverURL, conf.size(), color))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        val playerColor = if conf.gameId().nonEmpty && conf.token().nonEmpty then
          getPlayerColor(serverURL, conf.gameId(), conf.token())
        else None
        Success(BaseClient(serverURL, conf.gameId(), conf.token.toOption, playerColor))
      else
        colorFromString(conf.color())
          .flatMap(color => BaseClient.register(serverURL, conf.gameId(), color))
    else Failure(RuntimeException("Must provide either size or gameId"))

  def waitUntilReady(client: BaseClient): Try[StatusResponse] =
    client.status.flatMap(pollUntilReady(client, _, pollIntervalMs, exitIfOver))

  private def exitIfOver(status: StatusResponse): Unit =
    if status.over then
      logger.info(s"Game over: ${status.game}")
      exit(0)

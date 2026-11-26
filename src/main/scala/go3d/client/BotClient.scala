package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.Game
import go3d.Move
import go3d.Pass
import go3d.Position
import go3d.server.IdGenerator
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
  val gameId = opt[String](required = false, validate = IdGenerator.isValidId)
  val token = opt[String](required = false)
  val url = opt[String](required = false)
  val server = opt[String](required = false)
  val port = opt[Int](required = false)
  val strategy = opt[String](required = false)
  val maxThinkingTimeMs = opt[Int](required = false, default = Some(0))
  val parallel = opt[Boolean](required = false, default = Some(false))
  val pollIntervalMs = opt[Int](required = false, default = Some(10))
  val modelStore = opt[String](required = false, default = Some(BotClient.DefaultModelStore))
  val simulations =
    opt[Int](required = false, default = Some(BotClient.DefaultSimulations), validate = _ > 0)
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

/** Picks the next move from the current status: the position of a stone, or None to pass. */
type MoveChoice = StatusResponse => Try[Option[Position]]

object BotClient extends Client with LazyLogging:

  val DefaultModelStore: String = "models"
  val DefaultSimulations: Int = 800

  // set from --poll-interval-ms, like the other options kept in this object
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private[client] var pollIntervalMs: Int = 10
  var executionTimes: List[Long] = List()
  private val random: SecureRandom = SecureRandom()
  private[client] var strategies: Array[String] = Array()
  private var maxThinkingTimeMs: Int = 0
  private var parallel: Boolean = false
  // set from --model-store and --simulations, like the other options kept in this object
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private[client] var engineSettings: MoveEngineSettings =
    MoveEngineSettings(DefaultModelStore, DefaultSimulations)

  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX
  ///   --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX
  ///   --token XXXXX"
  /// --url https://... replaces --server and --port, e.g. for the API Gateway of the Lambda server;
  /// --poll-interval-ms sets how often to poll while waiting for the opponent (default 10)
  /// --strategy is a comma-separated list of:
  ///   closestToCenter|closestToStarPoints|maximizeOwnLiberties|minimizeOpponentLiberties
  /// or a move engine on its own, e.g. model:3 for generation 3 of the AlphaZero engine, which
  /// needs the engine on the classpath: sbt "engine/runMain go3d.client.BotClient ..."
  /// --model-store is the directory of the model generations (default models), --simulations
  /// the search simulations per move (default 800)

  /// A game that ends without an error exits with code 0, as one that ends on a move does.
  override def main(args: Array[String]): Unit =
    super.main(args)
    exit(0)

  /// The move choice is made once, from the first status, before waiting for the opponent, so
  /// that a strategy that cannot play fails at once and a model is loaded only once.
  def mainLoop(client: BaseClient): Try[Unit] =
    val token = client.token.fold("")((str) => str)
    logger.info(s"server: ${client.serverURL} game: ${client.id} token: $token")
    for
      status <- client.status
      choice <- moveChoice(status.game)
      ready <- pollFrom(client, status)
      played <- playMoves(client, Some(ready), choice)
    yield played

  /// The move choice of the --strategy options for `game`: a move engine, or the heuristics.
  private[client] def moveChoice(game: Game): Try[MoveChoice] =
    strategies.find(MoveEngine.isEngineStrategy) match
      case Some(strategy) if strategies.length == 1 =>
        MoveEngine.create(strategy, engineSettings, game).map(engineChoice)
      case Some(strategy) =>
        Failure(MoveEngineError(s"$strategy cannot be combined with other strategies"))
      case None =>
        val strategy =
          if strategies.nonEmpty then Some(SetStrategy(game.size, strategies, maxThinkingTimeMs))
          else None
        Success(heuristicChoice(strategy))

  /// A random one of the server's possible moves that `strategy` keeps, or a pass if none is left.
  private[client] def heuristicChoice(strategy: Option[SetStrategy]): MoveChoice =
    status =>
      strategy.fold[Try[Seq[Position]]](Success(status.moves))(
        _.narrowDown(status.moves, status.game)
      ).map(possible => if possible.nonEmpty then Some(randomMove(possible)) else None)

  /// The move that `engine` chooses in the game of the status.
  private[client] def engineChoice(engine: MoveEngine): MoveChoice =
    status =>
      engine.move(status.game).map {
        case move: Move => Some(move.position)
        case _: Pass => None
      }

  /// Plays until the game is over. `readyStatus` is a status already known to be ready, so the
  /// first move does not have to poll the server again. Every move is chosen from the game of
  /// the status that is ready, which has the opponent's last move.
  @tailrec
  private[client] def playMoves(
      client: BaseClient,
      readyStatus: Option[StatusResponse],
      choice: MoveChoice
  ): Try[Unit] =
    val startTime = System.currentTimeMillis()
    val result = readyStatus.fold(waitUntilReady(client))(Success(_))
      .flatMap { status =>
        logger.info(s"Move: ${status.game.moves.length} ${executionTimeString}")
        // a server may report a game that is over as ready, e.g. the Lambda's status
        if status.over then Success(true) else makeOneMove(client, status, choice)
      }
      .recoverWith {
        case _: InterruptedException => exit(1); Success(true)
        case e: RequestFailedException => checkFailedRequest(client, e, choice).map(_ => true)
      }
    executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    result match
      case Success(over) =>
        if !over then playMoves(client, None, choice)
        else Success(client.status.foreach(status => logger.info(s"${status.game}")))
      case Failure(e) => Failure(e)

  private def checkFailedRequest(
      client: BaseClient,
      e: RequestFailedException,
      choice: MoveChoice
  ): Try[Unit] =
    logger.warn(e.message)
    if e.response.statusCode == Status.Gone.code then exit(0)
    playMoves(client, None, choice)

  /// Sends the move that `choice` picks for `status`. A move that ends the game exits, so the
  /// result is false: the game goes on.
  private[client] def makeOneMove(
      client: BaseClient,
      status: StatusResponse,
      choice: MoveChoice
  ): Try[Boolean] =
    choice(status).flatMap { position =>
      position.fold(client.pass)(p => client.set(p.x, p.y, p.z)).map { newStatus =>
        if newStatus.over then
          logger.info(s"Game over: ${newStatus.game}")
          exit(0)
        // a single pass does not end the game; keep playing until the opponent passes too
        false
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
      engineSettings = MoveEngineSettings(conf.modelStore(), conf.simulations())
      Client.serverUrl(conf.url.toOption, conf.server.toOption, conf.port.toOption)
        .flatMap(connect(conf, _))
    }

  private def connect(conf: BotClientCLIConf, serverURL: String): Try[BaseClient] =
    if conf.size.isSupplied then
      colorFromString(conf.color())
        .flatMap(color => BaseClient.create(serverURL, conf.size(), color))
    else if conf.gameId.isSupplied then
      if conf.token.isSupplied then
        val playerColor =
          if conf.token().nonEmpty then getPlayerColor(serverURL, conf.gameId(), conf.token())
          else None
        Success(BaseClient(serverURL, conf.gameId(), conf.token.toOption, playerColor))
      else
        colorFromString(conf.color())
          .flatMap(color => BaseClient.register(serverURL, conf.gameId(), color))
    else Failure(RuntimeException("Must provide either size or gameId"))

  def waitUntilReady(client: BaseClient): Try[StatusResponse] =
    client.status.flatMap(pollFrom(client, _))

  /// Polls from `status` until the game is ready for `client`, exiting if it is over meanwhile.
  private def pollFrom(client: BaseClient, status: StatusResponse): Try[StatusResponse] =
    pollUntilReady(client, status, pollIntervalMs, exitIfOver)

  private def exitIfOver(status: StatusResponse): Unit =
    if status.over then
      logger.info(s"Game over: ${status.game}")
      exit(0)

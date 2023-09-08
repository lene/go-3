package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.{Game, Position}
import go3d.server.{StatusResponse, emptyResponse}

import java.security.SecureRandom
import requests.RequestFailedException

import scala.annotation.tailrec

object BotClient extends Client with LazyLogging:

  private val PULL_WAIT_MS = 10
  var executionTimes: List[Long] = List()
  private val random: SecureRandom = SecureRandom()
  private var strategies: Array[String] = Array()
  private var game: Game = null

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
    logger.info(s"Move: ${game.moves.length} ${executionTimeString}")
    var over = false
    val startTime = System.currentTimeMillis()
    try
      over = makeOneMove(status)
    catch
      case _: Exit => exit(0)
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => logger.warn(e.message); mainLoop(Array())
    finally
      executionTimes = executionTimes.appended(System.currentTimeMillis() - startTime)
    if !over then mainLoop(Array())
    else logger.info(s"${client.status.game}")

  private def makeOneMove(status: StatusResponse): Boolean =
    val strategy = SetStrategy(game, strategies)
    val possible = strategy.narrowDown(status.moves, strategies)
    if possible.nonEmpty then
      val setPosition = randomMove(possible)
      game = client.set(setPosition.x, setPosition.y, setPosition.z).game
      false
    else
      game = client.pass.game
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
    val options = nextOption(Map(), args.toList)
    val serverURL = s"http://${options("server")}:${options("port")}"
    if options.contains("size") && options.contains("game_id") then
      throw IllegalArgumentException("--size and --game-id are mutually exclusive")
    if options.contains("size") then
      client = BaseClient.create(
        serverURL, options("size").asInstanceOf[Int],
        colorFromString(options("color").asInstanceOf[String])
      )
    else if options.contains("game_id") then
      if options.contains("token") then
        client = BaseClient(
          serverURL, options("game_id").asInstanceOf[String], 
          Some(options("token").asInstanceOf[String])
        )
      else client = BaseClient.register(
        serverURL, options("game_id").asInstanceOf[String],
        colorFromString(options("color").asInstanceOf[String])
      )
    else throw IllegalArgumentException("Either --size or --game-id must be supplied")
    strategies = options("strategy").asInstanceOf[String].split(',')

  @tailrec def nextOption(map : OptionMap, list: List[String]) : OptionMap =
    list match
      case Nil => map
      case "--size" :: value :: tail =>
        nextOption(map ++ Map("size" -> value.toInt), tail)
      case "--color" :: value :: tail =>
        nextOption(map ++ Map("color" -> value), tail)
      case "--game-id" :: value :: tail =>
        nextOption(map ++ Map("game_id" -> value), tail)
      case "--token" :: value :: tail =>
        nextOption(map ++ Map("token" -> value), tail)
      case "--server" ::  value :: tail =>
        nextOption(map ++ Map("server" -> value), tail)
      case "--port" :: value :: tail =>
        nextOption(map ++ Map("port" -> value.toInt), tail)
      case "--strategy" :: value :: tail =>
        nextOption(map ++ Map("strategy" -> value), tail)
      case option :: tail =>
        logger.error(s"Unknown option $option")
        System.exit(1)
        map

  def waitUntilReady(): StatusResponse =
    var status = emptyResponse
    while !status.ready do
      status = client.status
      if !status.ready then Thread.sleep(PULL_WAIT_MS)
    status

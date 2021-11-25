package go3d.client

import client.SetStrategy
import go3d.{BadColor, Black, Color, Game, Position, White}
import go3d.client.{BaseClient, Exit}
import go3d.server.StatusResponse

import scala.util.Random
import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import scala.io.StdIn.readLine
import requests._

object BotClient extends Client:

  val random = Random()
  var strategies: Array[String] = Array[String]()
  var game: Game = null

  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.BotClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"
  /// --strategy is a comma-separated list of:
  //  closestToCenter|closestToStarPoints|maximizeOwnLiberties|minimizeOpponentLiberties

  def mainLoop(args: Array[String]): Unit =
    print(s"server: ${client.serverURL} game: ${client.id} token: ${client.token}  ")
    val status = waitUntilReady()
    game = status.game
    println(s"\b \n${game.goban}")
    var over = false
    try
      val strategy = SetStrategy(game, strategies)
      val possible = status.moves
      if possible.nonEmpty then
        val setPosition = randomMove(strategy.narrowDown(possible, strategies))
        game = client.set(setPosition.x, setPosition.y, setPosition.z).game
      else
        over = true
        game = client.pass.game
    catch
      case e: Exit => exit(0)
      case e: InterruptedException => exit(1)
      case e: RequestFailedException => println(e)
    if !over then mainLoop(Array())
    else println(client.status.game)

  def randomMove(possible: Seq[Position]): Position =
    possible(random.nextInt(possible.length))

  def parseArgs(args: Array[String]) =
    val options = nextOption(Map(), args.toList)
    val serverURL = s"http://${options("server")}:${options("port")}"
    if options.contains("size") then
      client = BaseClient.create(
        serverURL, options("size").asInstanceOf[Int],
        colorFromString(options("color").asInstanceOf[String])
      )
    else if options.contains("game_id") then
      if options.contains("token") then
        client = BaseClient(
          serverURL, options("game_id").asInstanceOf[String], options("token").asInstanceOf[String]
        )
      else client = BaseClient.register(
        serverURL, options("game_id").asInstanceOf[String],
        colorFromString(options("color").asInstanceOf[String])
      )
    else throw IllegalArgumentException("Either --size or --game-id must be supplied")
    strategies = options("strategy").asInstanceOf[String].split(',')

  def nextOption(map : OptionMap, list: List[String]) : OptionMap =
    def isSwitch(s : String) = (s(0) == '-')
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
        println("Unknown option "+option)
        System.exit(1)
        return map

  def waitUntilReady(): StatusResponse =
    var status = StatusResponse(null, null, false, null)
    while !status.ready do
      status = client.status
      if !status.ready then Thread.sleep(10)
    return status

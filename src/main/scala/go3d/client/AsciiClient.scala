package go3d.client

import go3d.server.StatusResponse
import go3d.{BadColor, Black, Color, White}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import scala.io.StdIn.readLine
import requests._

import scala.annotation.tailrec

class Exit extends RuntimeException

object AsciiClient extends Client:

  var gameId: String = ""
  var token: String = ""

  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"

  @tailrec
  def mainLoop(args: Array[String]): Unit =
    print(s"server: ${client.serverURL} game: ${client.id} token: ${client.token}  ")
    val status = waitUntilReady()
    println(s"\b \n${status.game.goban}")
    try
      val input = readLine("your input: ")
      val Array(command, args) = (input+" ").split("\\s+", 2)
      command match
        case "set"|"s" => set(args)
        case "pass"|"p" => pass
        case "status"|"st" => getStatus
        case "exit" =>
          println("Exiting. If you want to reconnect to the game, enter")
          println(s"$$ sbt \"runMain go3d.client.AsciiClient --server ${client.serverURL} --game-id ${client.id} --token ${client.token}\"")
          throw Exit()
        case _ => println(
          s"\"$command\" not understood - use \"set|s\", \"pass|p\", \"status|st\" or \"exit\"!"
        )
    catch
      case e: Exit => exit(0)
      case e: InterruptedException => exit(1)
      case e: RequestFailedException => println(e)
    mainLoop(Array())

  def parseArgs(args: Array[String]): Unit =
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


  @tailrec
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
      case option :: tail =>
          println("Unknown option "+option)
          System.exit(1)
          map

  def set(args: String): StatusResponse =
    val Array(x, y, z) = args.split("\\s+", 3).map(s => s.trim.toInt)
    println(s"set $x $y $z")
    client.set(x, y, z)

  def pass: StatusResponse = client.pass

  def getStatus: StatusResponse = client.status
  
  def waitUntilReady(): StatusResponse =
    var status = StatusResponse(null, null, false, null)
    var index = 0
    while !status.ready do
      index = index+1
      print("\b" + "/-\\|"(index % 4))
      status = client.status
      if !status.ready then Thread.sleep(500)
    status

def colorFromString(string: String): Color =
  string.toLowerCase match
    case "@"|"black"|"b" => Black
    case "o"|"white"|"w" => White
    case _ => throw BadColor(string(0))
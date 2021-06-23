package go3d.client

import go3d.server.StatusResponse
import go3d.{BadColor, Black, Color, White}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import scala.io.StdIn.readLine
import requests._

class Exit extends RuntimeException

object AsciiClient:

  type OptionMap = Map[String, Int|String]
  var gameId: String = ""
  var token: String = ""
  var client: BaseClient =null

  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --size ## --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --color [b|w]"
  /// sbt "runMain go3d.client.AsciiClient --server $SERVER --port #### --game-id XXXXXX --token XXXXX"
  def main(args: Array[String]): Unit =
    try
      parseArgs(args)
    catch
      case e: UnknownHostException => exit(s"unknown host: ${e.getMessage}", 1)
      case e: ConnectException => exit(s"connection problem: ${e.getMessage}", 1)
      case e: NumberFormatException => exit(s"not a number: ${e.getMessage}", 1)
      case e: IOException => exit(s"${e.getMessage}", 1)
      case e: BadColor => exit(s"not a color, must be either black/b/@ or white/w/O", 1)
      case e: NoSuchElementException => exit(s"missing argument: ${exceptionToParam(e)}", 1)
      case e: IllegalArgumentException => exit(s"missing argument: ${e.getMessage}", 1)
    mainLoop(args)

  private def exceptionToParam(e: NoSuchElementException): String =
    "--" + e.getMessage.substring("key not found: ".length).replace('_', '-')

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
          return map

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

  def set(args: String): StatusResponse =
    val Array(x, y, z) = args.split("\\s+", 3).map(s => s.trim.toInt)
    println(s"set $x $y $z")
    client.set(x, y, z)

  def pass: StatusResponse = client.pass

  def getStatus: StatusResponse = client.status

  def exit(message: String, status: Int): Unit =
    if message.length > 0 then println(message)
    System.exit(status)
  def exit(status: Int): Unit = exit("", status)

  def waitUntilReady(): StatusResponse =
    var status = StatusResponse(null, null, false, null)
    var index = 0
    while !status.ready do
      index = index+1
      print("\b" + "/-\\|"(index % 4))
      status = client.status
      if !status.ready then Thread.sleep(500)
    return status

def colorFromString(string: String): Color =
  string.toLowerCase match
    case "@"|"black"|"b" => Black
    case "o"|"white"|"w" => White
    case _ => throw BadColor(string(0))
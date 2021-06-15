package go3d.client

import go3d.server.StatusResponse
import go3d.{BadColor, Black, Color, White}

import scala.io.StdIn.readLine

class Exit extends RuntimeException

object AsciiClient:

  type OptionMap = Map[String, Int|String]
  var gameId: String = ""
  var token: String = ""
  var client: BaseClient =null

  /// sbt "runMain go3d.client.AsciiClient"
  def main(args: Array[String]): Unit =
    val options = nextOption(Map(), args.toList)
    if options.contains("size") then
      client = BaseClient.create(
        s"http://${options("server")}:${options("port")}",
        options("size").asInstanceOf[Int],
        colorFromString(options("color").asInstanceOf[String])
      )
    else if options.contains("game_id") then
      client = BaseClient.register(
        s"http://${options("server")}:${options("port")}", options("game_id").asInstanceOf[String],
        colorFromString(options("color").asInstanceOf[String])
      )
    else
      println("Either size for new game or ID of existing game must be supplied")
      exit(1)
    mainLoop(args)

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
      case "--server" ::  value :: tail =>
        nextOption(map ++ Map("server" -> value), tail)
      case "--port" :: value :: tail =>
        nextOption(map ++ Map("port" -> value.toInt), tail)
      case option :: tail =>
          println("Unknown option "+option)
          System.exit(1)
          return map

  def mainLoop(args: Array[String]): Unit =
    print(client.id)
    val status = waitUntilReady()
    println(status)
    try
      val input = readLine("your input: ")
      val Array(command, args) = (input+" ").split("\\s+", 2)
      command match
        case "set" => set(args)
        case "pass" => pass(args)
        case "status" => getStatus(args)
        case "exit"|"" => throw Exit()
        case _ => println("sorry but nope")
    catch
      case e: Exit => exit(0)
      case e: InterruptedException => exit(1)
    mainLoop(Array())

  def set(args: String): StatusResponse =
    println(s"args: \"$args\"")
    val Array(x, y, z) = args.split("\\s+", 3).map(s => s.trim.toInt)
    println(s"set $x $y $z")
    client.set(x, y, z)

  def pass(args: String): StatusResponse = client.pass

  def getStatus(args: String): StatusResponse = client.status

  def exit(status: Int): Unit =
    println("bye.")
    System.exit(status)

  def waitUntilReady(): StatusResponse =
    val status = client.status
    if status.ready then return status
    else
      Thread.sleep(500)
      return waitUntilReady()

def colorFromString(string: String): Color =
  string.toLowerCase match
    case "@"|"black"|"b" => Black
    case "o"|"white"|"w" => White
    case _ => throw BadColor(string(0))
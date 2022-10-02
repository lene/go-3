package go3d.client

import go3d.server.StatusResponse
import go3d.{BadColor, Black, Color, White}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import scala.io.StdIn.readLine
import requests._

import scala.annotation.tailrec

class Exit extends RuntimeException

object AsciiClient extends InteractiveClient:

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
      case _: Exit => exit(0)
      case _: InterruptedException => exit(1)
      case e: RequestFailedException => println(e)
    mainLoop(Array())

  def set(args: String): StatusResponse =
    val Array(x, y, z) = args.split("\\s+", 3).map(s => s.trim.toInt)
    println(s"set $x $y $z")
    client.set(x, y, z)

  def pass: StatusResponse = client.pass

  def getStatus: StatusResponse = client.status

def colorFromString(string: String): Color =
  string.toLowerCase match
    case "@"|"black"|"b" => Black
    case "o"|"white"|"w" => White
    case _ => throw BadColor(string(0))
    
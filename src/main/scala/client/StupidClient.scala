package go3d.client

import go3d.{BadColor, Black, Color, White}
import go3d.client.AsciiClient.{OptionMap, client, exit, getStatus, mainLoop, pass, set, waitUntilReady}
import go3d.client.{BaseClient, Exit}
import go3d.server.StatusResponse

import scala.util.Random
import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import scala.io.StdIn.readLine
import requests._

object StupidClient:

  val random = Random()
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
    var over = false
    try
      val possible = status.moves
      if possible.nonEmpty then
        val setPosition = possible(random.nextInt(possible.length))
        client.set(setPosition.x, setPosition.y, setPosition.z)
      else
        over = true
        client.pass
    catch
      case e: Exit => exit(0)
      case e: InterruptedException => exit(1)
      case e: RequestFailedException => println(e)
    if !over then mainLoop(Array())
    else println(client.status.game)

  def waitUntilReady(): StatusResponse =
    var status = StatusResponse(null, null, false, null)
    while !status.ready do
      status = client.status
      if !status.ready then Thread.sleep(10)
    return status

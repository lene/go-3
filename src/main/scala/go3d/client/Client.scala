package go3d.client

import go3d.BadColor
import go3d.server.StatusResponse

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}

trait ClientTrait:
  type OptionMap = Map[String, Int|String]
  var client: BaseClient = null
  def mainLoop(args: Array[String]): Unit
  def parseArgs(args: Array[String]): Unit
  def nextOption(map : OptionMap, list: List[String]) : OptionMap
  def waitUntilReady(): StatusResponse
  def init(): Unit

abstract class Client extends ClientTrait:

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
    init()
    mainLoop(args)

  def init(): Unit = {}

  protected def exceptionToParam(e: NoSuchElementException): String =
      "--" + e.getMessage.substring("key not found: ".length).replace('_', '-')

  def exit(message: String, status: Int): Unit =
    if message.nonEmpty then println(message)
    System.exit(status)
  def exit(status: Int): Unit = exit("", status)


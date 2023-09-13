package go3d.client

import com.typesafe.scalalogging.LazyLogging
import go3d.server.{StatusResponse, emptyResponse}

abstract case class InteractiveClient(pollInterval: Int = 500) extends Client with LazyLogging:

  def nextOption(map : OptionMap, list: List[String]) : OptionMap =
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
      case option :: _ =>
        logger.error(s"Unknown option $option")
        exit(1)
        map

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
          serverURL, options("game_id").asInstanceOf[String],
          Some(options("token").asInstanceOf[String])
        )
      else if options.contains("color") then
        client = BaseClient.register(
        serverURL, options("game_id").asInstanceOf[String],
        colorFromString(options("color").asInstanceOf[String])
      )
      else client = BaseClient(
        serverURL, options("game_id").asInstanceOf[String], None
      )
    else throw IllegalArgumentException("Either --size or --game-id must be supplied")


  override  def waitUntilReady(): StatusResponse =
    var status = emptyResponse
    var index = 0
    while !status.ready do
      index = index+1
      print("\b" + "/-\\|"(index % 4))
      status = client.status
      if !status.ready then Thread.sleep(pollInterval)
    status


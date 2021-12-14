package go3d

import server.GoServer

import scala.io.StdIn.readLine
import scala.util.Random

val Step = 500
val DefaultBoardSize = 5

def replayGame(goban: Game, moves: List[Move | Pass], delayMs: Int, verbose: Boolean = false): Unit =
  var board = goban
  for move <- moves do
    board = board.makeMove(move)
    if verbose then
      println(s"$move\n$goban")
      Thread.sleep(delayMs)
  if !verbose then println(goban)

def randomGame(size: Int): Unit =
  val random = new Random
  var game = newGame(size)
  var color = Black
  val t0 = System.nanoTime()
  var tStep0 = t0
  while game.possibleMoves(color).nonEmpty && game.moves.length <= size*size*size do
    val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
    game = game.makeMove(move)
    if game.moves.length % Step == 0 || game.moves.length == size*size*size then
      val stepMs = (System.nanoTime()-tStep0)/1000000
      Console.println(s"${game.moves.length}/${size*size*size} (${stepMs/Step}ms/move)")
      tStep0 = System.nanoTime()
    color = !color
  val totalMs = (System.nanoTime()-t0)/1000000
  Console.println(s"overall: ${totalMs/1000.0}s, ${totalMs/(size*size*size)}ms/move")
  println(game)
  println(game.score)

def playGame(boardSize: Int): Unit =
  val game = newGame(boardSize)
  try
    val finished_game = makeMove(game, Black)
    println(finished_game.score)
  catch case e: GameOver => println(s"game over. score: ${e.game.score}")

def makeMove(game: Game, color: Color): Game =
  if game.moves.size >= game.size*game.size*game.size then return game
  println(game)
  val move = readMove(
    s"move ${game.moves.size+1} - $color set stone at: ", game.possibleMoves(color).toSet, color
  )
  return makeMove(game.makeMove(move), !color)

def readMove(message: String, possibleMoves: Set[Position], color: Color): Move| Pass =
  val input = readLine(message)
  try
    val pos = Position(input.split(" ").map(s => s.toInt))
    if !(possibleMoves contains(pos)) then throw IllegalMove(s"$pos not in $possibleMoves")
    Move(pos, color)
  catch
    case e: IllegalMove =>
      println(e.message)
      readMove(message, possibleMoves, color)
    case e: NumberFormatException =>
      if e.getMessage.endsWith("p\"") then return Pass(color)
      println(s"Not a number: ${e.getMessage}")
      readMove(message, possibleMoves, color)
    case e: ArrayIndexOutOfBoundsException =>
      println(s"Not three numbers, only ${e.getMessage}")
      readMove(message, possibleMoves, color)
    case e: InterruptedException =>
      println("Goodbye.")
      System.exit(1)
      Move(1, 1, 1, Black)

object Runner:
  type OptionMap = Map[String, Int|String]
  val DefaultServerPort = 3333
  val DefaultSaveDir = "./Go3D-Savegames"

  def nextOption(map : OptionMap, list: List[String]) : OptionMap =
    def isSwitch(s : String) = (s(0) == '-')
    list match
      case Nil => map
      case "--benchmark" :: value :: tail =>
        nextOption(map ++ Map("benchmark_size" -> value.toInt), tail)
      case "--benchmark" :: tail =>
        nextOption(map ++ Map("benchmark_size" -> DefaultBoardSize), tail)
      case "--new-game" :: value :: tail =>
        nextOption(map ++ Map("game_size" -> value.toInt), tail)
      case "--new-game" :: tail =>
        nextOption(map ++ Map("game_size" -> DefaultBoardSize), tail)
      case "--server" :: tail =>
        nextOption(map ++ Map("server" -> 0), tail)
      case "--port" :: value :: tail =>
        nextOption(map ++ Map("port" -> value.toInt), tail)
      case "--save-dir" :: value :: tail =>
        nextOption(map ++ Map("save_dir" -> value), tail)
      case option :: tail =>
        if option.matches("\\d+") then
          nextOption(map ++ Map("benchmark_size" -> option.toInt), tail)
        else
          println("Unknown option "+option)
          System.exit(1)
          return map

  def main(args: Array[String]): Unit =
    val options = nextOption(Map(), args.toList)
    if options.contains("benchmark_size") then
      randomGame(options("benchmark_size").asInstanceOf[Int])
    else if options.contains("game_size") then
      playGame(options("game_size").asInstanceOf[Int])
    else if options.contains("server") then {
      val port = options.getOrElse("port", DefaultServerPort).asInstanceOf[Int]
      val saveDir = options.getOrElse("save_dir", DefaultSaveDir).asInstanceOf[String]
      GoServer.loadGames(saveDir)
      GoServer.run(port)
    } else randomGame(DefaultBoardSize)
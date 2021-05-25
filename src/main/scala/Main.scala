package go3d

import scala.util.Random

val Step = 500

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
  var color = Color.Black
  val t0 = System.nanoTime()
  var tStep0 = t0
  while !game.possibleMoves(color).isEmpty && game.moves.length <= size*size*size do
    val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
    game = game.makeMove(move)
    if game.moves.size % Step == 0 || game.moves.size == size*size*size then
      val stepMs = (System.nanoTime()-tStep0)/1000000
      Console.println(s"${game.moves.size}/${size*size*size} (${stepMs/Step}ms/move)")
      tStep0 = System.nanoTime()
    color = !color
  val totalMs = (System.nanoTime()-t0)/1000000
  Console.println(s"overall: ${totalMs/1000.0}s, ${totalMs/(size*size*size)}ms/move")
  println(game)
  println(game.score)

object Runner {
  def main(args: Array[String]): Unit = {
    val boardSize = if args.isEmpty then 5 else args(0).toInt
    randomGame(boardSize)
  }
}

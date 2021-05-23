import go3d._

import scala.util.Random
import scala.App

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
  while !game.possibleMoves(color).isEmpty && game.moves.length <= size*size*size do
    val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
    game = game.makeMove(move)
    Console.print(s"${game.moves.length} ")
    Console.flush()
    color = !color
  println("\n"+game)

object Runner {
  def main(args: Array[String]): Unit = {
    val boardSize = if args.isEmpty then 5 else args(0).toInt
    randomGame(boardSize)
  }
}

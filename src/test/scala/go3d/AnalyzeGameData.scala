package go3d

import go3d.server.{*, given}
import io.circe.parser.decode
import scala.io.Source

/**
 * Utility to replay saved game data and analyze specific moves.
 *
 * Usage:
 *   sbt Test/compile && sbt "Test/runMain go3d.AnalyzeGameData <json-file> [start-move] [end-move]"
 *
 * Examples:
 *   sbt "Test/runMain go3d.AnalyzeGameData src/test/resources/data/04hfPW.json"
 *   sbt "Test/runMain go3d.AnalyzeGameData src/test/resources/data/04hfPW.json 280 290"
 */
object AnalyzeGameData:

  def main(args: Array[String]): Unit =
    if args.length < 1 then
      System.err.println("Usage: AnalyzeGameData <json-file> [start-move] [end-move]")
      System.err.println("  json-file: Path to saved game JSON file")
      System.err.println("  start-move: Optional - first move to analyze in detail (default: 0)")
      System.err.println("  end-move: Optional - last move to analyze in detail (default: last move)")
      sys.exit(1)

    val jsonFile = args(0)
    val source = Source.fromFile(jsonFile)
    val jsonContent = try source.mkString finally source.close()
    val saveGameResult = decode[go3d.server.SaveGame](jsonContent)

    if saveGameResult.isLeft then
      System.err.println(s"Failed to decode JSON: $saveGameResult")
      sys.exit(1)

    val saveGame = saveGameResult.toOption.get
    val totalMoves = saveGame.game.moves.length

    val startMove = if args.length >= 2 then args(1).toInt else 0
    val endMove = if args.length >= 3 then args(2).toInt else totalMoves - 1

    println(s"Analyzing game from $jsonFile")
    println(s"Game ID: ${saveGame.gameId}")
    println(s"Board size: ${saveGame.game.size}")
    println(s"Total moves: $totalMoves")
    println(s"Detailed analysis: moves $startMove to $endMove")
    println("=" * 80)

    var game = Game.start(saveGame.game.size)

    for (moveOrPass, index) <- saveGame.game.moves.zipWithIndex do
      val detailed = index >= startMove && index <= endMove

      if detailed then
        println(s"\n=== Before Move $index: $moveOrPass ===")

        moveOrPass match
          case move: Move =>
            val colorAtPos = game.at(move.position)
            println(s"Position ${move.position}: currently $colorAtPos")

            val neighbors = game.goban.neighbors(move.position)
            println(s"Neighbors of ${move.position}:")
            for n <- neighbors do
              println(s"  $n: ${game.at(n)}")

            if game.captures.nonEmpty then
              println(s"Last capture: ${game.lastCapture.mkString(", ")}")

          case _ =>

      // Make the move
      try
        moveOrPass match
          case move: Move =>
            game = game.makeMove(move)
            if detailed then
              println(s"\n=== After Move $index ===")
              println(s"Captures this move: ${game.lastCapture.mkString(", ")}")
              println(s"Total Black captures: ${game.captures(Black)}")
              println(s"Total White captures: ${game.captures(White)}")

          case pass: Pass =>
            game = game.makeMove(pass)
            if detailed then
              println(s"\n=== After Move $index ===")
              println(s"Pass by ${pass.color}")
      catch
        case e: Exception =>
          println(s"\nERROR at move $index: ${e.getClass.getSimpleName}: ${e.getMessage}")
          println(s"Move: $moveOrPass")
          println(s"Game stopped at move $index")
          sys.exit(1)

    println(s"\n" + "=" * 80)
    println(s"Successfully replayed all $totalMoves moves")
    println(s"Final score: ${game.score}")

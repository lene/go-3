package go3d

import go3d.server.{*, given}
import io.circe.parser.decode
import scala.io.Source

/**
 * Measure the time taken for each move in a saved game to identify slow moves.
 *
 * Usage:
 *   sbt "Test/runMain go3d.MeasureMoveTimes <json-file> [threshold-ms]"
 *
 * Examples:
 *   sbt "Test/runMain go3d.MeasureMoveTimes src/test/resources/data/gNoDpK.json"
 *   sbt "Test/runMain go3d.MeasureMoveTimes src/test/resources/data/gNoDpK.json 100"
 */
object MeasureMoveTimes:

  def main(args: Array[String]): Unit =
    if args.length < 1 then
      System.err.println("Usage: MeasureMoveTimes <json-file> [threshold-ms]")
      System.err.println("  json-file: Path to saved game JSON file")
      System.err.println("  threshold-ms: Optional - only show moves slower than this (default: 50ms)")
      sys.exit(1)

    val jsonFile = args(0)
    val thresholdMs = if args.length >= 2 then args(1).toInt else 50

    val source = Source.fromFile(jsonFile)
    val jsonContent = try source.mkString finally source.close()
    val saveGameResult = decode[go3d.server.SaveGame](jsonContent)

    if saveGameResult.isLeft then
      System.err.println(s"Failed to decode JSON: $saveGameResult")
      sys.exit(1)

    val saveGame = saveGameResult.toOption.get
    val totalMoves = saveGame.game.moves.length

    println(s"Analyzing move times from $jsonFile")
    println(s"Board size: ${saveGame.game.size}")
    println(s"Total moves: $totalMoves")
    println(s"Threshold: ${thresholdMs}ms")
    println("=" * 80)

    var game = Game.start(saveGame.game.size)
    var slowMoves = 0
    var totalTime = 0L

    for (moveOrPass, index) <- saveGame.game.moves.zipWithIndex do
      val startTime = System.nanoTime()

      try
        moveOrPass match
          case move: Move =>
            game = game.makeMove(move)
          case pass: Pass =>
            game = game.makeMove(pass)

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000
        totalTime += durationMs

        if durationMs >= thresholdMs then
          slowMoves += 1
          val captureInfo = if game.lastCapture.nonEmpty then
            s" (captured ${game.lastCapture.length} stones)"
          else ""
          println(f"Move $index%3d: $moveOrPass - ${durationMs}%5d ms$captureInfo")
      catch
        case e: Exception =>
          println(s"\nERROR at move $index: ${e.getClass.getSimpleName}: ${e.getMessage}")
          println(s"Move: $moveOrPass")
          sys.exit(1)

    println(s"\n" + "=" * 80)
    println(f"Total time: ${totalTime}ms (avg ${totalTime.toDouble/totalMoves}%.2fms per move)")
    println(s"Slow moves (>= ${thresholdMs}ms): $slowMoves")
    println(s"Final score: ${game.score}")

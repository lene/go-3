package go3d.probe

import go3d.Black
import go3d.Game
import go3d.White
import go3d.server.Player
import go3d.server.SaveGame
import go3d.server.given
import io.circe.syntax.EncoderOps

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.tailrec

/** The sample games of a run, saved as `SaveGame` JSON in the archive layout of the server, so
  * that `go-server --save-dir <out>` serves them to the viewer.
  */
object SampleRecords:
  /** About this many games of each pairing are saved: the first ones. */
  val PerPairing: Int = 3

  /** The game number is a six digit number in the id, see [[ProbeOptions.MaxGamesPerPairing]]. */
  private val GameRadix = 1_000_000
  private val Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
  private val IdLength = 6

  def isSample(game: Int): Boolean = game < PerPairing

  /** The id of the record of a game: a valid game id of the server that no other game of a run has,
    * and that does not depend on which strategies or sizes a run plays. It counts the size, the
    * strategies in [[ProbeOptions.StrategyNames]] and the game number.
    */
  def recordId(size: Int, black: String, white: String, game: Int): String =
    val strategies = ProbeOptions.StrategyNames.length
    val pairing = (size * strategies + ProbeOptions.StrategyNames.indexOf(black)) * strategies +
      ProbeOptions.StrategyNames.indexOf(white)
    base62(pairing.toLong * GameRadix + game)

  /** The last six base 62 digits of `number`. */
  private def base62(number: Long): String =
    @tailrec
    def digits(rest: Long, count: Int, acc: String): String =
      if count == IdLength then acc
      else
        digits(
          rest / Alphabet.length,
          count + 1,
          s"${Alphabet((rest % Alphabet.length).toInt)}$acc"
        )
    digits(number, 0, "")

  /** The record of a finished game: both players have the id of the record as their game id. */
  def saveGame(game: Game, id: String): SaveGame =
    SaveGame(game, Map(Black -> Player(Black, id), White -> Player(White, id)))

  /** Writes the records to `<outDir>/archive/<id>.json`. */
  def write(outDir: Path, records: Seq[(String, SaveGame)]): Unit =
    val archive = Files.createDirectories(outDir.resolve("archive"))
    records.foreach { (id, record) =>
      Files.write(
        archive.resolve(s"$id.json"),
        record.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    }

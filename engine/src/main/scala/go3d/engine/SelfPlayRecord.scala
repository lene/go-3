package go3d.engine

import go3d.{Black, Color, Game, White}
import go3d.server.given
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.util.Try
import scala.util.hashing.MurmurHash3

/** The record of a self-play game for training (`docs/self-play-records.md`): the game of
  * generation `generation` with `simulations` per move from the game seed `seed`, and the policy
  * to learn at each of its moves. Komi, winner and score are those of `game`.
  */
final case class SelfPlayRecord(
    generation: Int,
    simulations: Int,
    seed: Long,
    game: Game,
    policies: Vector[IndexedSeq[Double]]
)

object SelfPlayRecord:
  private val Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
  private val IdLength = 6
  private val IdCount = BigInt(Alphabet.length).pow(IdLength)

  given Encoder[SelfPlayRecord] = record =>
    val game = record.game
    Json.obj(
      "contract_version" -> Json.fromInt(ModelContract.Version),
      "size" -> Json.fromInt(game.size),
      "generation" -> Json.fromInt(record.generation),
      "komi" -> Json.fromDoubleOrNull(game.komi),
      "scoring_rule" -> Json.fromString(ModelContract.ScoringRule),
      "simulations" -> Json.fromInt(record.simulations),
      "seed" -> Json.fromLong(record.seed),
      "game" -> game.asJson,
      "policies" -> record.policies.map(_.map(Json.fromDoubleOrNull)).asJson,
      "winner" -> game.winner.fold(Json.Null)(winner => Json.fromString(colorName(winner))),
      "score" -> scoreJson(game.score)
    )

  private def scoreJson(score: Map[Color, Int]): Json =
    Json.obj(Seq(Black, White).map(color => colorName(color) -> Json.fromInt(score(color)))*)

  private def colorName(color: Color): String = if color == Black then "black" else "white"

  /** The file of the record with `id` in `dir`. */
  def path(dir: Path, id: String): Path = dir.resolve(s"$id.json")

  /** Writes `record` to [[path]] and returns that path. */
  def write(dir: Path, id: String, record: SelfPlayRecord): Try[Path] =
    Try(Files.write(
      path(dir, id),
      record.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    ))

  /** The seed of game `number` of a run, from the run's `seed`, size and generation, so that a
    * game does not depend on the order in which games are played. Two hashes make 64 bits.
    */
  def gameSeed(seed: Long, size: Int, generation: Int, number: Int): Long =
    val key = s"$seed/$size/$generation/$number"
    (MurmurHash3.stringHash(key, 1).toLong << 32) |
      (MurmurHash3.stringHash(key, 2).toLong & 0xffffffffL)

  /** The id of game `number` of a run: six base 62 digits, a valid game id of the server. The
    * games of a run count up from a start that the run's seed, size and generation give, so the
    * ids of a run differ, and runs with other seeds are unlikely to reuse them.
    */
  def gameId(seed: Long, size: Int, generation: Int, number: Int): String =
    val start = BigInt(MurmurHash3.stringHash(s"$seed/$size/$generation")).mod(IdCount)
    base62(((start + number) mod IdCount).toLong)

  /** The last six base 62 digits of `number`. */
  private def base62(number: Long): String =
    @tailrec
    def digits(rest: Long, count: Int, acc: String): String =
      if count == IdLength then acc
      else
        val digit = Alphabet((rest % Alphabet.length).toInt)
        digits(rest / Alphabet.length, count + 1, s"$digit$acc")
    digits(number, 0, "")

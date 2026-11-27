package go3d.engine

import go3d.{Black, Color, Game, White}
import go3d.server.{IdGenerator, given}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
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

  /** Writes `record` to [[path]] and returns that path. The record is written to a temporary
    * file next to it and renamed, so that a run killed while it writes leaves no partial record
    * for `--resume` to skip.
    */
  def write(dir: Path, id: String, record: SelfPlayRecord): Try[Path] =
    val target = path(dir, id)
    val partial = dir.resolve(s".$id.json.tmp")
    Try {
      Files.write(partial, record.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
    }

  /** The seed of game `number` of a run, from the run's `seed`, size and generation. */
  def gameSeed(seed: Long, size: Int, generation: Int, number: Int): Long =
    Parallel.seed(s"$seed/$size/$generation/$number")

  /** The id of game `number` of a run: six base 62 digits, a valid game id of the server. The
    * games of a run count up from a start that the run's seed, size and generation give, so the
    * ids of a run differ, and runs with other seeds are unlikely to reuse them.
    */
  def gameId(seed: Long, size: Int, generation: Int, number: Int): String =
    val start = MurmurHash3.stringHash(s"$seed/$size/$generation").toLong
    IdGenerator.base62Id(start + number)

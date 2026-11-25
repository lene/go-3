package go3d.engine

import go3d.client.MoveEngineError

import scala.util.{Failure, Success, Try}

/** The metadata of a model that the engine checks (`docs/model-contract.md`): a model that
  * differs does not fail, it plays legal but meaningless moves, so it is refused.
  */
object ModelContract:
  val Version: String = "1"
  val ScoringRule: String = "area"
  val KomiKey: String = "komi"

  /** The metadata values that the engine and a game of `size` need, in the order of the contract.
    */
  def expected(size: Int): Seq[(String, String)] =
    Seq(
      "contract_version" -> Version,
      "board_size" -> size.toString,
      "scoring_rule" -> ScoringRule,
      "input_planes" -> Encoding.InputPlanes.toString,
      "actions" -> Encoding.actionCount(size).toString
    )

  /** The komi of a model with `metadata` for a game of `size`, or a failure that names every
    * expected value that differs.
    */
  def check(metadata: Map[String, String], size: Int): Try[Double] =
    val mismatches = expected(size).collect {
      case (key, value) if !metadata.get(key).contains(value) =>
        s"$key is ${metadata.getOrElse(key, "missing")}, expected $value"
    }
    if mismatches.nonEmpty then Failure(MoveEngineError(mismatches.mkString(", ")))
    else
      metadata.get(KomiKey).flatMap(_.toDoubleOption).fold[Try[Double]](
        Failure(MoveEngineError(s"$KomiKey is missing or not a number"))
      )(Success(_))

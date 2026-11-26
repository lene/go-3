package go3d.server

import go3d.{BadKomi, Komi}

import scala.util.{Failure, Success, Try}

/** The komi of a new game as both servers read it from the query of `/new/{size}`. */
object KomiQuery:
  /** The name of the query parameter that carries the komi when a game is created. */
  val Parameter: String = "komi"

  /** The komi for a game of `size` from the raw values of the [[Parameter]]: [[Komi.Default]] if
    * the parameter is absent. A parameter without a value (an empty list of values or one empty
    * value), several values, text that is not a plain decimal number and an invalid komi fail with
    * [[BadKomi]].
    */
  def fromValues(values: Option[Seq[String]], size: Int): Try[Double] =
    values match
      case None => Success(Komi.Default)
      case Some(Seq(text)) => Komi.parse(text, size)
      case Some(_) => Failure(BadKomi())

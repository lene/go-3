package go3d

import java.math.BigDecimal as JavaBigDecimal
import scala.util.{Failure, Success, Try}

/** The komi of a game: the points added to White's score, to make up for Black's first move.
  *
  * A valid komi is a half-integer, so that a game with integer scores never ends in a tie, and has
  * a magnitude of at most size³, the number of points on the board. That is the most a player can
  * score, as [[Game.score]] counts only stones and territory.
  * Validation is exact: a [[java.math.BigDecimal]] has no rounding and does not overflow, whatever
  * the size, whereas double arithmetic would turn a text like 6.5000000000000001 into 6.5.
  */
object Komi:
  /** The komi of a game created without one. */
  val Default: Double = 0.5

  private val Syntax = """-?\d+(\.\d+)?""".r
  private val Two = JavaBigDecimal.valueOf(2L)

  /** `komi` if it is a valid komi for a game of `size`, else a [[BadKomi]] failure. */
  def validate(komi: Double, size: Int): Try[Double] =
    if komi.isFinite && isValid(JavaBigDecimal(komi), size) then Success(komi)
    else Failure(BadKomi())

  /** The komi for a game of `size` from `text`, a plain decimal number; any other text and an
    * invalid komi fail with [[BadKomi]].
    */
  def parse(text: String, size: Int): Try[Double] =
    if !Syntax.matches(text) then Failure(BadKomi())
    else
      val komi = JavaBigDecimal(text)
      if isValid(komi, size) then Success(komi.doubleValue) else Failure(BadKomi())

  /** Twice a half-integer is an odd integer. The bound is computed in `BigDecimal`, so an
    * unvalidated size cannot overflow it.
    */
  private def isValid(komi: JavaBigDecimal, size: Int): Boolean =
    val twice = komi.multiply(Two)
    val twiceBound = JavaBigDecimal.valueOf(size.toLong).pow(3).multiply(Two)
    val isOdd = Try(twice.toBigIntegerExact).toOption.exists(_.testBit(0))
    isOdd && twice.abs.compareTo(twiceBound) <= 0

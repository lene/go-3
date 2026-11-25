package go3d.engine

import scala.annotation.tailrec
import scala.util.Random

/** Samples of a symmetric Dirichlet distribution, the exploration noise of self-play.
  *
  * A sample is a vector of independent Gamma(α, 1) samples, normalised to sum to 1. Self-play
  * uses α well below 1, where most Gamma samples are tiny, so they are drawn and normalised as
  * logarithms: a sum of values that all underflow to 0 would be 0.
  */
object Dirichlet:

  /** A sample of `count` values from the symmetric Dirichlet distribution with `alpha > 0`. */
  def sample(alpha: Double, count: Int, random: Random): Vector[Double] =
    val logs = Vector.fill(count)(logGamma(alpha, random))
    val largest = logs.max
    val weights = logs.map(log => math.exp(log - largest))
    val total = weights.sum
    weights.map(_ / total)

  /** The logarithm of a Gamma(shape, 1) sample. Below shape 1 it is a Gamma(shape + 1) sample
    * times U^(1 / shape) for a uniform U in (0, 1] (Marsaglia and Tsang).
    */
  private def logGamma(shape: Double, random: Random): Double =
    if shape < 1 then
      logGamma(shape + 1, random) + math.log(1.0 - random.nextDouble()) / shape
    else math.log(gammaFromOne(shape, random))

  /** A Gamma(shape, 1) sample for `shape >= 1`, by the rejection method of Marsaglia and Tsang
    * (2000).
    */
  @tailrec
  private def gammaFromOne(shape: Double, random: Random): Double =
    val d = shape - 1.0 / 3
    val c = 1 / math.sqrt(9 * d)
    val x = random.nextGaussian()
    val v = math.pow(1 + c * x, 3)
    if v > 0 && math.log(1.0 - random.nextDouble()) < x * x / 2 + d - d * v + d * math.log(v)
    then d * v
    else gammaFromOne(shape, random)

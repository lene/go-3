package go3d.probe

/** The stop rule that turns the metrics into the input of the gate. The user approved it before
  * the first run: the rule fires when the flip rate, pooled over the pairings of a size, is at
  * least [[FlipRatePercent]] percent of the games at any of the sizes 5, 7 and 9. Every other
  * metric is reported and is no threshold.
  *
  * The rule does not decide anything. If it fires, the user decides whether to run the size-5
  * rules lab; if it does not, area scoring ships.
  */
object StopRule:
  val FlipRatePercent: Int = 10

  /** What the rule says about the sizes that were played: for each size whether its flip rate
    * reached the threshold, and from that whether the rule fired and for which sizes.
    */
  final case class Outcome(reached: Map[Int, Boolean]):
    /** The sizes that reached the threshold, smallest first. */
    def firingSizes: List[Int] = reached.toList.sortBy(_._1).collect { case (size, true) => size }

    def fired: Boolean = firingSizes.nonEmpty

  def evaluate(flipsBySize: Map[Int, Rate]): Outcome =
    Outcome(flipsBySize.map((size, flips) => size -> flips.atLeastPercent(FlipRatePercent)))

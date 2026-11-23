package go3d.probe

import scala.annotation.tailrec

/** A rating model with a first-move term, fitted to who won as Black and as White.
  *
  * The chance that Black wins a game is `logistic(strength(black) - strength(white) + firstMove)`.
  * The first-move term is what Black gains by moving first, so strength is not mistaken for it, and
  * the strengths of the strategies are what remains. The fit is the maximum likelihood of the games
  * with a small ridge penalty: without it the strengths of a strategy that never loses would grow
  * without bound, and only differences of strengths mean anything.
  */
object Rating:

  /** The outcome of the games in which `black` played Black against `white`. */
  final case class PairCounts(black: String, white: String, blackWins: Int, games: Int)

  /** `strengths` and `firstMove` are in logits: the difference of two strengths is the log odds
    * that one of them beats the other, `firstMove` the log odds that Black beats an equal player.
    */
  final case class Fit(strengths: Map[String, Double], firstMove: Double):
    def blackWinsBetweenEquals: Double = logistic(firstMove)

    /** The chance that `a` beats `b` when `a` plays Black. */
    def chanceOfBlack(a: String, b: String): Double =
      logistic(strengths.getOrElse(a, 0.0) - strengths.getOrElse(b, 0.0) + firstMove)

  private val Ridge = 1e-3
  private val MaxIterations = 100
  private val Tolerance = 1e-10

  /** A difference of 1 logit in the Elo scale, where 400 points are a factor of 10 in the odds. */
  val EloPerLogit: Double = 400.0 / math.log(10.0)

  def logistic(x: Double): Double = 1.0 / (1.0 + math.exp(-x))

  /** The fit to the games of `cells`; the strengths of their strategies sum to nearly zero. */
  def fit(cells: Seq[PairCounts]): Fit =
    val names = cells.flatMap(cell => Seq(cell.black, cell.white)).distinct.sorted
    val firstMoveIndex = names.length
    val index = names.zipWithIndex.toMap
    val directions =
      cells.map(cell => direction(index(cell.black), index(cell.white), names.length))
    val solution = newton(cells.zip(directions), Vector.fill(names.length + 1)(0.0), MaxIterations)
    Fit(names.zip(solution).toMap, solution(firstMoveIndex))

  /** The vector `x` with `x . parameters` the log odds of Black winning a game: +1 for Black's
    * strength, -1 for White's (nothing for a strategy against itself), +1 for the first move.
    */
  private def direction(black: Int, white: Int, strategies: Int): Vector[Double] =
    Vector.tabulate(strategies + 1) { i =>
      (if i == black then 1.0 else 0.0) - (if i == white then 1.0 else 0.0) +
        (if i == strategies then 1.0 else 0.0)
    }

  @tailrec
  private def newton(
      cells: Seq[(PairCounts, Vector[Double])],
      parameters: Vector[Double],
      iterations: Int
  ): Vector[Double] =
    val (gradient, curvature) = cells.foldLeft(penalty(parameters)) {
      case ((g, h), (cell, x)) =>
        val chance = logistic(dot(x, parameters))
        val weight = cell.games * chance * (1.0 - chance)
        (
          add(g, scale(x, cell.blackWins - cell.games * chance)),
          h.zip(outer(x)).map((row, update) => add(row, scale(update, weight)))
        )
    }
    val step = solve(curvature, gradient)
    val next = add(parameters, step)
    if iterations <= 1 || step.forall(math.abs(_) < Tolerance) then next
    else newton(cells, next, iterations - 1)

  /** The gradient of the penalty, and the curvature that the penalty adds to the Hessian. */
  private def penalty(parameters: Vector[Double]): (Vector[Double], Vector[Vector[Double]]) =
    val size = parameters.length
    (
      parameters.map(-Ridge * _),
      Vector.tabulate(size)(i => Vector.tabulate(size)(j => if i == j then Ridge else 0.0))
    )

  private def dot(a: Vector[Double], b: Vector[Double]): Double = a.zip(b).map(_ * _).sum
  private def add(a: Vector[Double], b: Vector[Double]): Vector[Double] = a.zip(b).map(_ + _)
  private def scale(a: Vector[Double], factor: Double): Vector[Double] = a.map(_ * factor)
  private def outer(x: Vector[Double]): Vector[Vector[Double]] = x.map(scale(x, _))

  /** The solution of `matrix * result = rhs` by Gauss-Jordan elimination with partial pivoting. */
  private def solve(matrix: Vector[Vector[Double]], rhs: Vector[Double]): Vector[Double] =
    val n = rhs.length
    val augmented = matrix.zip(rhs).map((row, value) => row :+ value)
    val reduced = (0 until n).foldLeft(augmented) { (rows, column) =>
      val pivot = (column until n).maxBy(row => math.abs(rows(row)(column)))
      val swapped = rows.updated(column, rows(pivot)).updated(pivot, rows(column))
      val pivotRow = swapped(column)
      swapped.zipWithIndex.map { (row, r) =>
        if r == column then row
        else add(row, scale(pivotRow, -row(column) / pivotRow(column)))
      }
    }
    Vector.tabulate(n)(i => reduced(i)(n) / reduced(i)(i))

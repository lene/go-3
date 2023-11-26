package go3d.client

import go3d.{Black, Color, Game, Goban, Move, Position}

import scala.collection.parallel.CollectionConverters._
import scala.annotation.tailrec

object SetStrategy:
  def create(
    gameSize: Int, strategies: Array[String], maxThinkingTimeMs: Int = 0, parallel: Boolean = false
  ): SetStrategy =
    if parallel then ParallelSetStrategy(gameSize, strategies, maxThinkingTimeMs)
    else SetStrategy(gameSize, strategies, maxThinkingTimeMs)

case class SetStrategy(gameSize: Int, strategies: Array[String], maxThinkingTimeMs: Int = 0):

  private val thinkingTimeLimiter = ThinkingTimeLimiter(maxThinkingTimeMs)

  def narrowDown(possible: Seq[Position], game: Game): Seq[Position] =
    @tailrec
    def iterateThroughStrategies(
        possible: Seq[Position], game: Game, strategies: Array[String]
    ): Seq[Position] =
      if strategies.isEmpty || possible.isEmpty then possible
      else
        val nextPossible = strategies.head match
          case "random" => possible
          case "closestToCenter" => closestToCenter(possible)
          case "onStarPoints" => onStarPoints(possible)
          case "closestToStarPoints" => closestToStarPoints(possible)
          case "maximizeOwnLiberties" => maximizeOwnLiberties(possible, game)
          case "minimizeOpponentLiberties" => minimizeOpponentLiberties(possible, game)
          case "maximizeDistance" => maximizeDistance(possible, game)
          case "prioritiseCapture" => prioritiseCapture(possible, game)
          case s => throw IllegalArgumentException(s"narrowDown(): $s not implemented")
        iterateThroughStrategies(nextPossible, game, strategies.tail)

    val subset = thinkingTimeLimiter.subsetToSatisfyMaxThinkingTime(possible)
    thinkingTimeLimiter.recordThinkingTime({iterateThroughStrategies(subset, game, strategies)})


  def closestToCenter(possible: Seq[Position]): Seq[Position] =
    val center = Position(gameSize/2+1, gameSize/2+1, gameSize/2+1)
    bestBy(possible, p => (center - p).abs)

  def onStarPoints(possible: Seq[Position]): Seq[Position] =
    val possibleSet = possible.toSet
    val firstMatchingSetOfStarpoints: Set[Position] = StarPoints(gameSize).asSetsByPriority.find(
      _.intersect(possibleSet).nonEmpty  // find first set of star points intersecting with possible
    ).getOrElse(possibleSet)             // if no set of star points found, leave possible unchanged
    firstMatchingSetOfStarpoints.intersect(possibleSet).toSeq

  def closestToStarPoints(possible: Seq[Position]): Seq[Position] =
    if StarPoints(gameSize).all.toSet.intersect(possible.toSet).nonEmpty
    then onStarPoints(possible)
    else bestBy(possible, minDistanceToPointList(_, StarPoints(gameSize).all))

  def maximizeOwnLiberties(possible: Seq[Position], game: Game): Seq[Position] =
    val color = game.moveColor
    bestBy(possible, p => -game.setStone(Move(p, color)).totalNumLiberties(color))

  def minimizeOpponentLiberties(possible: Seq[Position], game: Game): Seq[Position] =
    val color = game.moveColor
    val possibleMoves = game.getFreeNeighbors(!color).intersect(possible.toSet)
    if possibleMoves.isEmpty
    then possible.toSet.intersect(StarPoints(gameSize).all.toSet).toList
    else bestBy(possibleMoves.toList, p => game.setStone(Move(p, color)).totalNumLiberties(!color))

  def maximizeDistance(possible: Seq[Position], game: Game): Seq[Position] =
    val opponentStones = game.getStones(!game.moveColor)
    if opponentStones.isEmpty
    then possible.toSet.intersect(StarPoints(gameSize).all.toSet).toList
    else bestBy(possible, -minDistanceToPointList(_, opponentStones))

  def prioritiseCapture(possible: Seq[Position], game: Game): Seq[Position] =
    val color = game.moveColor
    val opponentStones = game.getStones(!color)
    if opponentStones.isEmpty
    then possible.toSet.intersect(StarPoints(gameSize).all.toSet).toList
    else
      val opponentNeighbors = game.getFreeNeighbors(!color)
      val possibleMoves = possible.toSet.intersect(opponentNeighbors).toList
      val toUse = if possibleMoves.isEmpty then possible else possibleMoves
      bestBy(toUse, p => minLiberties(game.setStone(Move(p, color)), !color))

  def bestBy[A](values: Seq[A], metric: A => Int): Seq[A] =
    values.groupBy(metric).minBy(_._1)._2

class ParallelSetStrategy(gameSize: Int, strategies: Array[String], maxThinkingTimeMs: Int = 0)
  extends SetStrategy(gameSize, strategies, maxThinkingTimeMs):
  override def bestBy[A](values: Seq[A], metric: A => Int): Seq[A] =
    values.par.groupBy(metric).minBy(_._1)._2.seq

def minBy[A](values: Seq[A], metric: A => Int): Int = values.map(metric).min

def minDistanceToPointList(local: Position, remotes: Seq[Position]): Int =
  remotes.map(p => (p - local).abs).min

def minLiberties(game: Game, color: Color): Int =
  minBy(game.getAreas(color).toList, game.liberties(color, _))



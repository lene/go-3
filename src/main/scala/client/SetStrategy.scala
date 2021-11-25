package go3d.client

import go3d.client.StarPoints
import go3d.{Black, Color, Game, Goban, Move, Position}
/*
    Following lines are disabled, because when running tests instantiating a Logger ouside of the
    Server thread leas to a race condition:
    - if two Loggers are instantiated at the same time in different threads, an error
        java.lang.ClassCastException: class org.slf4j.helpers.SubstituteLogger cannot be cast to
        class ch.qos.logback.classic.Logger (org.slf4j.helpers.SubstituteLogger and
        ch.qos.logback.classic.Logger are in unnamed module of loader
        sbt.internal.LayeredClassLoader @4acf1e69)
      results.
    - if either TestSetStrategy or TestServer finishes before the other is started, the test suite
      succeeds.
    I cannot find how to fix this, so I am disabling logging for now, by using the alternate
    declaration below.
import com.typesafe.scalalogging.Logger
val logger = Logger[SetStrategy]
 */
object logger:
  def info(msg: String): Unit = println(msg)

case class SetStrategy(game: Game, strategies: Array[String]):

  val gameSize = game.size

  def narrowDown(possible: Seq[Position], strategies: Array[String]): Seq[Position] =
    if strategies.isEmpty
    then possible
    else
      val nextPossible = strategies.head match
        case "random" => possible
        case "closestToCenter" => closestToCenter(possible)
        case "closestToStarPoints" => closestToStarPoints(possible)
        case "maximizeOwnLiberties" => maximizeOwnLiberties(possible)
        case "minimizeOpponentLiberties" => minimizeOpponentLiberties(possible)
        case s => throw IllegalArgumentException(s"narrowDown(): $s not implemented")
      narrowDown(nextPossible, strategies.tail)

  def closestToCenter(possible: Seq[Position]): Seq[Position] =
    val center = Position(gameSize/2+1, gameSize/2+1, gameSize/2+1)
    val oneClosest = possible.minBy(p => (center - p).abs)
    val closestDistance = (center - oneClosest).abs
    possible.filter(p => (center - p).abs == closestDistance)

  def closestToStarPoints(possible: Seq[Position]): Seq[Position] =
    val stars = StarPoints(gameSize)
    for (points <- List(stars.corner, stars.midLine, stars.midFace, stars.center))
      val pointsInInput = possible.toSet.intersect(points.toSet)
      if pointsInInput.nonEmpty then return pointsInInput.toList
    val oneClosest = possible.minBy(p => minDistanceToPointList(p, stars.all))
    val closestDistance = minDistanceToPointList(oneClosest, stars.all)
    possible.filter(p => minDistanceToPointList(p, stars.all) == closestDistance)

  def maximizeOwnLiberties(possible: Seq[Position]): Seq[Position] =
    val oneBest = possible.maxBy(p => game.setStone(Move(p, moveColor)).totalNumLiberties(moveColor))
    val maxLiberties = game.setStone(Move(oneBest, moveColor)).totalNumLiberties(moveColor)
    logger.info(s"max liberties: $maxLiberties")
    possible.filter(p => game.setStone(Move(p, moveColor)).totalNumLiberties(moveColor) == maxLiberties)

  def moveColor: Color = if game.moves.isEmpty then Black else !game.moves.last.color

  def minimizeOpponentLiberties(possible: Seq[Position]): Seq[Position] =
    val oneBest = possible.minBy(p => game.setStone(Move(p, moveColor)).totalNumLiberties(!moveColor))
    val minLiberties = game.setStone(Move(oneBest, moveColor)).totalNumLiberties(!moveColor)
    logger.info(s"min liberties: $minLiberties")
    possible.filter(p => game.setStone(Move(p, moveColor)).totalNumLiberties(!moveColor) == minLiberties)

  def maximizeDistance(possible: Seq[Position]): Seq[Position] =
    ???

def minDistanceToPointList(local: Position, remotes: Seq[Position]): Int =
  remotes.map(p => (p - local).abs).min

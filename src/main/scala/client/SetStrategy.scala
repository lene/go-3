package client

import go3d.client.StarPoints
import go3d.{Game, Position}

case class SetStrategy(game: Game, strategies: Array[String]):

  val gameSize = game.size

  def narrowDown(possible: List[Position], strategies: Array[String]): List[Position] =
    if strategies.isEmpty
    then possible
    else
      val nextPossible = strategies.head match
        case "random" => possible
        case "closestToCenter" => closestToCenter(possible)
        case "closestToStarPoints" => closestToStarPoints(possible)
        case "maximizeOwnLiberties" => maximizeOwnLiberties(possible)
        case s => throw IllegalArgumentException(s"narrowDown(): $s not implemented")
      narrowDown(nextPossible, strategies.tail)

  def closestToCenter(possible: List[Position]): List[Position] =
    val center = Position(gameSize/2+1, gameSize/2+1, gameSize/2+1)
    val oneClosest = possible.minBy(p => (center - p).abs)
    val closestDistance = (center - oneClosest).abs
    possible.filter(p => (center - p).abs == closestDistance)

  def closestToStarPoints(possible: List[Position]): List[Position] =
    val stars = StarPoints(gameSize)
    for (points <- List(stars.corner, stars.midLine, stars.midFace, stars.center))
      val pointsInInput = possible.toSet.intersect(points.toSet)
      if pointsInInput.nonEmpty then return pointsInInput.toList
    val oneClosest = possible.minBy(p => minDistanceToPointList(p, stars.all))
    val closestDistance = minDistanceToPointList(oneClosest, stars.all)
    possible.filter(p => minDistanceToPointList(p, stars.all) == closestDistance)

  def minDistanceToPointList(local: Position, remotes: Seq[Position]): Int =
    remotes.map(p => (p - local).abs).min

  def maximizeOwnLiberties(possible: List[Position]): List[Position] =
    ???

  def minimizeOpponentLiberties(possible: List[Position]): List[Position] =
    ???

  def maximizeDistance(possible: List[Position]): List[Position] =
    ???


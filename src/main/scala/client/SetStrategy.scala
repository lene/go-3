package client

import go3d.client.StarPoints
import go3d.{Color, Game, Goban, Move, Position}

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

  def minDistanceToPointList(local: Position, remotes: Seq[Position]): Int =
    remotes.map(p => (p - local).abs).min

  def maximizeOwnLiberties(possible: Seq[Position]): Seq[Position] =
    val color = moveColor(game)
    val oneBest = possible.maxBy(p => totalNumLiberties(game.setStone(Move(p, color)), color))
    val maxLiberties = totalNumLiberties(game.setStone(Move(oneBest, color)), color)
    possible.filter(p => totalNumLiberties(game.setStone(Move(p, color)), color) == maxLiberties)

  def moveColor(game: Game): Color = !game.moves.last.color

  def totalNumLiberties(game: Game, color: Color): Int =
    game.goban.emptyPositions.filter(hasNeighbor(game.goban, _, color)).size

  def hasNeighbor(goban: Goban, pos: Position, color: Color): Boolean =
    goban.neighbors(pos).exists(goban.at(_) == color)

  def minimizeOpponentLiberties(possible: Seq[Position]): Seq[Position] =
    ???

  def maximizeDistance(possible: Seq[Position]): Seq[Position] =
    ???


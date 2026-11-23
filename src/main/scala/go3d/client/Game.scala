package go3d.client

import go3d.Color
import go3d.Empty
import go3d.Game
import go3d.Move
import go3d.Position

extension (game: Game)
  def totalNumLiberties(col: Color): Int =
    game.goban.numLiberties(col)

  def hasNeighborOfColor(pos: Position, color: Color): Boolean =
    game.goban.neighbors(pos).exists(game.at(_) == color)

  def getStones(col: Color): Seq[Position] =
    game.moves.filter(m => m.color == col).collect { case m: Move => m }.map(m =>
      m.position
    ).toIndexedSeq

  def getFreeNeighbors(col: Color): Set[Position] =
    game.getStones(col).foldLeft(Set[Position]())((neighbors, position) =>
      neighbors ++ game.goban.neighborsOfColor(position, Empty)
    )

  def getAreas(col: Color): Set[Set[Position]] = areasOf(col, game.getStones(col))

  /** The groups of `col` at `stones`, each flood-filled once. A point whose stone of `col` is no
    * longer there gives the empty group.
    */
  def areasOf(col: Color, stones: Seq[Position]): Set[Set[Position]] =
    val (areas, _) = stones.foldLeft((Set[Set[Position]](), Set[Position]())) {
      case ((areas, covered), position) =>
        if covered.contains(position) then (areas, covered)
        else
          val area = game.connectedStones(Move(position, col)).map(_.position)
          (areas + area, covered ++ area)
    }
    areas

  def liberties(col: Color, area: Set[Position]): Int =
    area.foreach(p => assert(col == game.at(p)))
    game.goban.numLiberties(area)

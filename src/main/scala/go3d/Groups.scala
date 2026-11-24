package go3d

import scala.annotation.tailrec

/** The liberties of every group on a board, found in one pass: for each stone, the number of
  * distinct empty points next to its group. The legal moves of a position need this for each empty
  * point without an empty neighbour, so one pass replaces a board copy and a flood fill per point.
  *
  * It is a snapshot: [[Goban.stones]] can be changed in place, so it is computed where it is used
  * and not kept with the board. The points are numbered `(x * side + y) * side + z` on the board
  * plus its sentinel border, so the six neighbours of a point are at fixed offsets.
  */
private[go3d] final class Groups private (side: Int, group: Array[Int], liberties: Array[Int]):

  /** The liberties of the group of the stone at `position`, or 0 if there is no stone. */
  def liberties(position: Position): Int =
    val id = group((position.x * side + position.y) * side + position.z)
    if id < 0 then 0 else liberties(id)

private[go3d] object Groups:

  def of(goban: Goban): Groups =
    val side = goban.size + 2
    // `stones` is indexed by x, then y, then z, so flattening it numbers the points as above
    val fill = Fill(goban.stones.flatten.flatten, side)
    fill.all()
    new Groups(side, fill.group, fill.liberties)

  /** The flood fill of [[Groups.of]] over the `colors` of the points, numbered as in [[Groups]]. */
  private final class Fill(colors: Array[Color], side: Int):
    private val offsets = Array(1, -1, side, -side, side * side, -side * side)
    // a group is numbered by its first point, which also indexes its count of liberties
    val group: Array[Int] = Array.fill(colors.length)(-1)
    val liberties: Array[Int] = Array.fill(colors.length)(0)
    // the group whose liberties last counted an empty point, so that each counts once per group
    private val counted = Array.fill(colors.length)(-1)
    private val stack = Array.fill(colors.length)(0)

    /** Fills the group of each stone that is not in a group yet. */
    def all(): Unit =
      for start <- colors.indices do
        if isStone(start) && group(start) < 0 then
          group(start) = start
          stack(0) = start
          fill(1, start)

    private def isStone(point: Int): Boolean = colors(point) == Black || colors(point) == White

    @tailrec
    private def fill(top: Int, id: Int): Unit =
      if top > 0 then fill(visit(stack(top - 1), 0, top - 1, id), id)

    /** Visits the neighbours of `point` from the `k`th on, and returns the new top of `stack`. */
    @tailrec
    private def visit(point: Int, k: Int, top: Int, id: Int): Int =
      if k == offsets.length then top
      else
        val neighbor = point + offsets(k)
        if colors(neighbor) == Empty && counted(neighbor) != id then
          counted(neighbor) = id
          liberties(id) += 1
          visit(point, k + 1, top, id)
        else if colors(neighbor) == colors(id) && group(neighbor) < 0 then
          group(neighbor) = id
          stack(top) = neighbor
          visit(point, k + 1, top + 1, id)
        else visit(point, k + 1, top, id)

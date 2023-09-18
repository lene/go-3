package go3d

case class Area(stones: Set[Move], liberties: Int, goban: Goban):

  val color: Color = validateColor(stones)
  val size: Int = stones.size
  lazy val outerHull: (Position, Position) = outerHullOfSet(stones.map(_.position))
  private lazy val withinOuterHull: Seq[Position] =
    for (
      x <- outerHull(0).x to outerHull(1).x;
      y <- outerHull(0).y to outerHull(1).y;
      z <- outerHull(0).z to outerHull(1).z
    ) yield Position(x, y, z)

  def contains: Position => Boolean = stones.map(_.position).contains

  /// all `Position`s that lie within the `Area` and are not of `Color` `color`
  lazy val inside: Set[Position] = allInsideAreas

  def isAlive: Boolean =
    if inside.size < 2 then false
    else if inside.size > 2 then true
    else !areNeighbors(inside.head, inside.last)

  override def toString: String =
    def areaToString(): String =
      if stones.isEmpty then ""
      else stones.head.color.toString + stones.foldLeft("")((acc, stone) => acc + "/" + stone.position.toString)
    s"[${areaToString()}]"

  private def validateColor(stones: Set[Move]): Color =
    val colors = stones.foldLeft(Set[Color]())((colors, stone) => colors + stone.color)
    if colors.contains(Empty) || colors.size != 1 then throw BadColorsForArea(colors)
    colors.head

  private def outerHullOfSet(stoneSet: Set[Position]): (Position, Position) =
    if stoneSet.isEmpty then throw BadArea(stones)
    if stoneSet.size == 1 then (stoneSet.head, stoneSet.head)
    else
      val (subHullMin, subHullMax) = outerHullOfSet(stoneSet.tail)
      val currentStone = stoneSet.head
      (
        Position(
          if currentStone.x < subHullMin.x then currentStone.x else subHullMin.x,
          if currentStone.y < subHullMin.y then currentStone.y else subHullMin.y,
          if currentStone.z < subHullMin.z then currentStone.z else subHullMin.z),
        Position(
          if currentStone.x > subHullMax.x then currentStone.x else subHullMax.x,
          if currentStone.y > subHullMax.y then currentStone.y else subHullMax.y,
          if currentStone.z > subHullMax.z then currentStone.z else subHullMax.z)
      )

  def allInsideAreas: Set[Position] =
    withinOuterHull.filter(
      p => goban.at(p) != color
    ).map(
      insideArea(_)
    ).foldLeft(Set.empty)(
      (acc, area) => acc ++ area
    )

  def insideArea(position: Position, alreadyFoundPaths: Set[Position] = Set.empty): Set[Position] =
    if goban.at(position) == color then throw BadColorsForArea(Set(color))
    val neighborsToConsider = goban.neighbors(position).
      filter(pos => goban.at(pos) != color).
      filter(!alreadyFoundPaths.contains(_))
    if neighborsToConsider.exists(p => onBorderOfAreaButNotBoard(p)) then Set.empty
    else if neighborsToConsider.isEmpty then alreadyFoundPaths + position
    else insideArea(neighborsToConsider.head, alreadyFoundPaths + position ++ neighborsToConsider)

  def onBorderOfAreaButNotBoard(position: Position): Boolean =
    if outerHull(0).x == 1 && outerHull(1).x == goban.size ||
      outerHull(0).y == 1 && outerHull(1).y == goban.size ||
      outerHull(0).z == 1 && outerHull(1).z == goban.size then false // outer hull equals board
    else if position.x == 1 || position.x == goban.size ||
      position.y == 1 || position.y == goban.size ||
      position.z == 1 || position.z == goban.size then false // on border of board
    else position.x == outerHull(0).x || position.x == outerHull(1).x ||
      position.y == outerHull(0).y || position.y == outerHull(1).y ||
      position.z == outerHull(0).z || position.z == outerHull(1).z  // on border of outer hull

def areNeighbors(pos1: Position, pos2: Position): Boolean = (pos1-pos2).abs == 1

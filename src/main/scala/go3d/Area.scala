package go3d

case class Area(stones: Set[Move], liberties: Int):

  val color: Color = validateColor(stones)
  val size: Int = stones.size
  lazy val outerHull: (Position, Position) = outerHull(stones.map(_.position))
  private lazy val withinOuterHull: Seq[Position] =
    for (
      x <- outerHull._1.x to outerHull._2.x;
      y <- outerHull._1.y to outerHull._2.y;
      z <- outerHull._1.z to outerHull._2.z
    ) yield Position(x, y, z)


  /// all `Position`s that lie within the `Area` and are not of `Color` `color`
  lazy val inside: Set[Position] =
    withinOuterHull.filter(isInside).toSet

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

  private def outerHull(stoneSet: Set[Position]): (Position, Position) =
    if stoneSet.isEmpty then throw BadArea(stones)
    if stoneSet.size == 1 then (stoneSet.head, stoneSet.head)
    else
      val (subHullMin, subHullMax) = outerHull(stoneSet.tail)
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

  private def areNeighbors(value1: Position, value2: Position): Boolean =
    value1.x == value2.x && (((value1.y - value2.y).abs == 1) || ((value1.z - value2.z).abs == 1)) ||
      value1.y == value2.y && (((value1.x - value2.x).abs == 1) || ((value1.z - value2.z).abs == 1)) ||
      value1.z == value2.z && (((value1.x - value2.x).abs == 1) || ((value1.y - value2.y).abs == 1))

  private def isInside(position: Position): Boolean =
    ???
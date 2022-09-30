package go3d

case class Area(stones: Set[Move], liberties: Int):

  private val colors = stones.foldLeft(Set[Color]())((colors, stone) => colors + stone.color)
  if colors.contains(Empty) || colors.size != 1 then throw BadColorsForArea(colors)
  val color: Color = colors.head

  override def toString: String =
    def areaToString(): String =
      if stones.isEmpty then ""
      else stones.head.color.toString + stones.foldLeft("")((acc, stone) => acc + "/" + stone.position.toString)
    s"[${areaToString()}]"

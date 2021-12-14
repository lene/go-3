package go3d.client

import go3d.Position

case class StarPoints(size: Int):

  val centerPos: Int = size/2+1
  val distToCenter: Int = centerPos/2

  val corner: Seq[Position] = alignedToCenter(0)
  val midLine: Seq[Position] = alignedToCenter(1)
  val midFace: Seq[Position] = alignedToCenter(2)
  val center: Seq[Position] = Vector(Position(centerPos, centerPos, centerPos))
  val all: Seq[Position] = corner ++ midLine ++ midFace ++ center

  private def alignedToCenter(count: Int): Seq[Position] =
    for (
      i <- centerPos-distToCenter to centerPos+distToCenter by distToCenter;
      j <- centerPos-distToCenter to centerPos+distToCenter by distToCenter;
      k <- centerPos-distToCenter to centerPos+distToCenter by distToCenter
      if List(i, j, k).count(_ == centerPos) == count
    ) yield Position(i, j, k)

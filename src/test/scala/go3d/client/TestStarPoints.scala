package go3d.client

import go3d._
import org.junit.Test

class TestStarPoints:
  @Test def testCorners(): Unit =
    assertPositionsEqual(
      List((1, 1, 1), (1, 1, 3), (1, 3, 1), (1, 3, 3), (3, 1, 1), (3, 1, 3), (3, 3, 1), (3, 3, 3)),
      StarPoints(3).corner
    )

  @Test def testMidLines(): Unit =
    assertPositionsEqual(
      List(
        (1, 1, 2), (1, 2, 1), (1, 2, 3), (1, 3, 2), (2, 1, 1), (2, 1, 3), (2, 3, 1), (2, 3, 3),
        (3, 1, 2), (3, 2, 1), (3, 2, 3), (3, 3, 2)
      ),
      StarPoints(3).midLine
    )

  @Test def testMidFaces(): Unit =
    assertPositionsEqual(
      List((1, 2, 2), (2, 1, 2), (2, 2, 1), (2, 2, 3), (2, 3, 2), (3, 2, 2)),
      StarPoints(3).midFace
    )

  @Test def testCenter(): Unit =
    assertPositionsEqual(List((2, 2, 2)), StarPoints(3).center)

  @Test def testAll(): Unit =
    assertPositionsEqual(
      List(
        (1, 1, 1), (1, 1, 3), (1, 3, 1), (1, 3, 3), (3, 1, 1), (3, 1, 3), (3, 3, 1), (3, 3, 3),
        (1, 1, 2), (1, 2, 1), (1, 2, 3), (1, 3, 2), (2, 1, 1), (2, 1, 3), (2, 3, 1), (2, 3, 3),
        (3, 1, 2), (3, 2, 1), (3, 2, 3), (3, 3, 2),
        (1, 2, 2), (2, 1, 2), (2, 2, 1), (2, 2, 3), (2, 3, 2), (3, 2, 2),
        (2, 2, 2)
      ),
      StarPoints(3).all
    )


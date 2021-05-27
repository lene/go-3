package go3d.testing

import go3d._
import org.junit.{Assert, Test}

class TestNeighbors:

  @Test def testNeighborsCorner(): Unit =
    val empty = newGoban(TestSize)
    val cornerNeighbors = empty.neighbors(Position(1, 1, 1))
    assertPositionsEqual(List((1, 1, 2), (1, 2, 1), (2, 1, 1)), cornerNeighbors)

  @Test def testNeighborsEdge(): Unit =
    val empty = newGoban(TestSize)
    val edgeNeighbors = empty.neighbors(Position(1, 1, 2))
    assertPositionsEqual(List((1, 1, 1), (1, 1, 3), (1, 2, 2), (2, 1, 2)), edgeNeighbors)

  @Test def testNeighborsFace(): Unit =
    val empty = newGoban(TestSize)
    val faceNeighbors = empty.neighbors(Position(1, 2, 2))
    assertPositionsEqual(List((1, 2, 1), (1, 2, 3), (1, 1, 2), (1, 3, 2), (2, 2, 2)), faceNeighbors)

  @Test def testNeighborsInterior(): Unit =
    val empty = newGoban(TestSize)
    val interiorNeighbors = empty.neighbors(Position(2, 2, 2))
    assertPositionsEqual(
      List((2, 2, 1), (2, 2, 3), (2, 1, 2), (2, 3, 2), (1, 2, 2), (3, 2, 2)), interiorNeighbors
    )

  @Test def testNeighborsOuterCorner(): Unit =
    val empty = newGoban(TestSize)
    val cornerNeighbors = empty.neighbors(Position(TestSize, TestSize, TestSize))
    assertPositionsEqual(
      List((TestSize, TestSize, TestSize-1), (TestSize, TestSize-1, TestSize),
        (TestSize-1, TestSize, TestSize)),
      cornerNeighbors
    )

  @Test def testNeighborsOuterEdge(): Unit =
    val empty = newGoban(TestSize)
    val edgeNeighbors = empty.neighbors(Position(TestSize, TestSize, TestSize-1))
    assertPositionsEqual(
      List((TestSize, TestSize, TestSize), (TestSize, TestSize, TestSize-2),
        (TestSize, TestSize-1, TestSize-1), (TestSize-1, TestSize, TestSize-1)),
      edgeNeighbors
    )

  @Test def testNeighborsOuterFace(): Unit =
    val empty = newGoban(TestSize)
    val faceNeighbors = empty.neighbors(Position(TestSize, TestSize-1, TestSize-1))
    assertPositionsEqual(
      List((TestSize, TestSize-1, TestSize), (TestSize, TestSize-1, TestSize-2),
        (TestSize, TestSize, TestSize-1), (TestSize, TestSize-2, TestSize-1),
        (TestSize-1, TestSize-1, TestSize-1)),
      faceNeighbors
    )

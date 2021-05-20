package go3d.testing

import go3d._
import org.junit.{Assert, Test}

class TestNeighbors:
  @Test def testNeighborsCornerEmpty(): Unit =
    val empty = Game(TestSize)
    val cornerNeighbors = empty.neighbors(Position(1, 1, 1))
    Assert.assertEquals(cornerNeighbors.toString, 3, cornerNeighbors.length)
    Assert.assertTrue(cornerNeighbors.contains(Position(1, 1, 2)))
    Assert.assertTrue(cornerNeighbors.contains(Position(1, 2, 1)))
    Assert.assertTrue(cornerNeighbors.contains(Position(2, 1, 1)))

  @Test def testNeighborsEdgeEmpty(): Unit =
    val empty = Game(TestSize)
    val edgeNeighbors = empty.neighbors(Position(1, 1, 2))
    Assert.assertEquals(edgeNeighbors.toString, 4, edgeNeighbors.length)
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 1, 1)))
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 1, 3)))
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 2, 2)))
    Assert.assertTrue(edgeNeighbors.contains(Position(2, 1, 2)))

  @Test def testNeighborsFaceEmpty(): Unit =
    val empty = Game(TestSize)
    val faceNeighbors = empty.neighbors(Position(1, 2, 2))
    Assert.assertEquals(faceNeighbors.toString, 5, faceNeighbors.length)
    Assert.assertTrue(faceNeighbors.contains(Position(1, 2, 1)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 2, 3)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 1, 2)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 3, 2)))
    Assert.assertTrue(faceNeighbors.contains(Position(2, 2, 2)))

  @Test def testNeighborsInteriorEmpty(): Unit =
    val empty = Game(TestSize)
    val interiorNeighbors = empty.neighbors(Position(2, 2, 2))
    Assert.assertEquals(interiorNeighbors.toString, 6, interiorNeighbors.length)
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 2, 1)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 2, 3)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 1, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 3, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(1, 2, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(3, 2, 2)))

  @Test def testNeighborsCorner(): Unit =
    val empty = Game(TestSize)
    val cornerNeighbors = empty.neighbors(Position(1, 1, 1))
    Assert.assertEquals(cornerNeighbors.toString, 3, cornerNeighbors.length)
    Assert.assertTrue(cornerNeighbors.contains(Position(1, 1, 2)))
    Assert.assertTrue(cornerNeighbors.contains(Position(1, 2, 1)))
    Assert.assertTrue(cornerNeighbors.contains(Position(2, 1, 1)))

  @Test def testNeighborsEdge(): Unit =
    val empty = Game(TestSize)
    val edgeNeighbors = empty.neighbors(Position(1, 1, 2))
    Assert.assertEquals(edgeNeighbors.toString, 4, edgeNeighbors.length)
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 1, 1)))
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 1, 3)))
    Assert.assertTrue(edgeNeighbors.contains(Position(1, 2, 2)))
    Assert.assertTrue(edgeNeighbors.contains(Position(2, 1, 2)))

  @Test def testNeighborsFace(): Unit =
    val empty = Game(TestSize)
    val faceNeighbors = empty.neighbors(Position(1, 2, 2))
    Assert.assertEquals(faceNeighbors.toString, 5, faceNeighbors.length)
    Assert.assertTrue(faceNeighbors.contains(Position(1, 2, 1)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 2, 3)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 1, 2)))
    Assert.assertTrue(faceNeighbors.contains(Position(1, 3, 2)))
    Assert.assertTrue(faceNeighbors.contains(Position(2, 2, 2)))

  @Test def testNeighborsInterior(): Unit =
    val empty = Game(TestSize)
    val interiorNeighbors = empty.neighbors(Position(2, 2, 2))
    Assert.assertEquals(interiorNeighbors.toString, 6, interiorNeighbors.length)
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 2, 1)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 2, 3)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 1, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(2, 3, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(1, 2, 2)))
    Assert.assertTrue(interiorNeighbors.contains(Position(3, 2, 2)))

  @Test def testNeighborsOuterCorner(): Unit =
    val empty = Game(TestSize)
    val cornerNeighbors = empty.neighbors(Position(TestSize, TestSize, TestSize))
    Assert.assertEquals(cornerNeighbors.toString, 3, cornerNeighbors.length)
    Assert.assertTrue(cornerNeighbors.contains(Position(TestSize, TestSize, TestSize-1)))
    Assert.assertTrue(cornerNeighbors.contains(Position(TestSize, TestSize-1, TestSize)))
    Assert.assertTrue(cornerNeighbors.contains(Position(TestSize-1, TestSize, TestSize)))

  @Test def testNeighborsOuterEdge(): Unit =
    val empty = Game(TestSize)
    val edgeNeighbors = empty.neighbors(Position(TestSize, TestSize, TestSize-1))
    Assert.assertEquals(edgeNeighbors.toString, 4, edgeNeighbors.length)
    Assert.assertTrue(edgeNeighbors.contains(Position(TestSize, TestSize, TestSize)))
    Assert.assertTrue(edgeNeighbors.contains(Position(TestSize, TestSize, TestSize-2)))
    Assert.assertTrue(edgeNeighbors.contains(Position(TestSize, TestSize-1, TestSize-1)))
    Assert.assertTrue(edgeNeighbors.contains(Position(TestSize-1, TestSize, TestSize-1)))

  @Test def testNeighborsOuterFace(): Unit =
    val empty = Game(TestSize)
    val faceNeighbors = empty.neighbors(Position(TestSize, TestSize-1, TestSize-1))
    Assert.assertEquals(faceNeighbors.toString, 5, faceNeighbors.length)
    Assert.assertTrue(faceNeighbors.contains(Position(TestSize, TestSize-1, TestSize)))
    Assert.assertTrue(faceNeighbors.contains(Position(TestSize, TestSize-1, TestSize-2)))
    Assert.assertTrue(faceNeighbors.contains(Position(TestSize, TestSize, TestSize-1)))
    Assert.assertTrue(faceNeighbors.contains(Position(TestSize, TestSize-2, TestSize-1)))
    Assert.assertTrue(faceNeighbors.contains(Position(TestSize-1, TestSize-1, TestSize-1)))

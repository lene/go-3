package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestGoban:

  @Test def testGameCtorBasic(): Unit =
    val goban = Goban.start(TestSize)
    Assertions.assertEquals(TestSize, goban.size)

  @Test def testMemoryAllocation(): Unit =
    val goban = Goban.start(TestSize)
    Assertions.assertEquals(TestSize+2, goban.stones.length)
    Assertions.assertEquals(TestSize+2, goban.stones(0).length)
    Assertions.assertEquals(TestSize+2, goban.stones(TestSize+1).length)
    Assertions.assertEquals(TestSize+2, goban.stones(0)(0).length)
    Assertions.assertEquals(TestSize+2, goban.stones(TestSize+1)(TestSize+1).length)

  @Test def testSentinels(): Unit =
    val goban = Goban.start(TestSize)
    Assertions.assertEquals(Sentinel, goban.stones(0)(0)(0))
    Assertions.assertEquals(Sentinel, goban.stones(TestSize+1)(TestSize+1)(TestSize+1))
    for x <- 1 to TestSize do
      Assertions.assertEquals(Sentinel, goban.stones(x)(0)(0))
      Assertions.assertEquals(Sentinel, goban.stones(x)(TestSize+1)(TestSize+1))
      for y <- 1 to TestSize do
        Assertions.assertEquals(Sentinel, goban.stones(x)(y)(0))
        Assertions.assertEquals(Sentinel, goban.stones(x)(y)(TestSize+1))
        for z <- 1 to TestSize do Assertions.assertEquals(Empty, goban.stones(x)(y)(z))

  @Test def testBoardSizeTooSmall(): Unit =
    Assertions.assertThrows(classOf[BadBoardSize], () => Goban.start(1))

  @Test def testBoardSizeTooBig(): Unit =
    Assertions.assertThrows(classOf[BadBoardSize], () => Goban.start(MaxBoardSize+2))

  @Test def testBoardSizeEven(): Unit =
    Assertions.assertThrows(classOf[BadBoardSize], () => Goban.start(4))

  @Test def testEmptyBoardAt(): Unit =
    val empty = Goban.start(TestSize)
    for p <- empty.allPositions do Assertions.assertEquals(Empty, empty.at(p))

  @Test def testAtWithIntsOnBorder(): Unit =
    val empty = Goban.start(TestSize)
    for x <- 0 to TestSize+1
      y <- 0 to TestSize+1 by TestSize+1
      z <- 0 to TestSize+1 by TestSize+1
    do
      Assertions.assertEquals(Sentinel, empty.at(x, y, z))

  @Test def testSetStoneWithMove(): Unit =
    val board = Goban.start(TestSize).setStone(Move(2, 2, 2, Black))
    Assertions.assertEquals(board.at(Position(2, 2, 2)), Black)

  @Test def testSetStoneWithMoveOutsideBoard(): Unit =
    val empty = Goban.start(TestSize)
    Assertions.assertThrows(
      classOf[OutsideBoard], () => empty.setStone(Move(TestSize+2, TestSize+2, TestSize+2, Black))
    )

  @Test def testSetStoneWithInts(): Unit =
    val board = Goban.start(TestSize).setStone(2, 2, 2, Black)
    Assertions.assertEquals(board.at(Position(2, 2, 2)), Black)

  @Test def testSetStoneWithIntsOnBorder(): Unit =
    val empty = Goban.start(TestSize).setStone(0, 0, 0, Sentinel)
    Assertions.assertEquals(empty.at(0, 0, 0), Sentinel)

  @Test def testSetStoneWithIntsOutsideBoard(): Unit =
    val empty = Goban.start(TestSize)
    Assertions.assertThrows(
      classOf[OutsideBoard], () => empty.setStone(TestSize+2, TestSize+2, TestSize+2, Black)
    )

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = Goban.start(TestSize).setStone(Move(2, 2, 2, Black))
    Assertions.assertThrows(classOf[PositionOccupied], () => board.checkValid(Move(2, 2, 2, White)))

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = Goban.start(TestSize)
    Assertions.assertThrows(
      classOf[OutsideBoard], () => empty.checkValid(Move(TestSize+1, 2, 2, White))
    )
    Assertions.assertThrows(
      classOf[OutsideBoard], () => empty.checkValid(Move(2, TestSize+1, 2, White))
    )
    Assertions.assertThrows(
      classOf[OutsideBoard], () => empty.checkValid(Move(2, 2, TestSize+1, White))
    )

  @Test def testDeepCopy2D(): Unit =
    val original = Array(Array(1, 2), Array(3,4))
    val cloned = deepCopy(original)
    cloned(0)(0) = 9
    Assertions.assertEquals(1, original(0)(0))

  @Test def testDeepCopy3D(): Unit =
    val original = Array(Array(Array(1, 2), Array(3,4)), Array(Array(5, 6), Array(7,8)))
    val cloned = deepCopy(original)
    cloned(0)(0)(0) = 9
    Assertions.assertEquals(1, original(0)(0)(0))

  @Test def testClone(): Unit =
    val original = Goban.start(TestSize)
    val cloned = original.clone()
    cloned.setStone(1, 1, 1, Black)
    Assertions.assertEquals(Empty, original.at(1, 1, 1))

  @Test def testSetListOfStones(): Unit =
    val goban = setListOfStones(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assertions.assertEquals(
        move.color, goban.at(move.position), move.toString+"\n"+goban.toString
      )

  @Test def testFromStrings(): Unit =
    val goban = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
      ))
      checkStonesOnBoard(goban, 
        List(Move(2, 1, 1, Black), Move(1, 2, 1, Black), Move(3, 2, 1, Black), 
          Move(2, 3, 1, Black), Move(2, 2, 2, Black))
      )

  @Test def testEqualGobansAreEqual(): Unit =
    val goban1 = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    val goban2 = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    Assertions.assertEquals(goban1, goban2)

  @Test def testUnequalGobansAreNotEqual(): Unit =
    val goban1 = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    val goban2 = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             | @ |"""
    ))
    Assertions.assertNotEquals(goban1, goban2)

  @Test def testGobansIsNotEqualToSomethingDifferent(): Unit =
    val goban1 = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    Assertions.assertNotEquals(goban1, "some random thing")

  @Test def testNumLibertiesAllNeighborsFreeBlack(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          | @ |
          |   |"""
    ))
    Assertions.assertEquals(6, goban.numLiberties(Set(Position(2, 2, 2))))

  @Test def testNumLibertiesAllNeighborsFreeWhite(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          | O |
          |   |"""
    ))
    Assertions.assertEquals(6, goban.numLiberties(Set(Position(2, 2, 2))))

  @Test def testNumLibertiesAllNeighborsFreeOnFace(): Unit =
    val goban = fromStrings(Map(
      1 ->
        """   |
          | @ |
          |   |"""
    ))
    Assertions.assertEquals(5, goban.numLiberties(Set(Position(2, 2, 1))))

  @Test def testNumLibertiesAllNeighborsFreeOnEdge(): Unit =
    val goban = fromStrings(Map(
      1 ->
        """   |
          |@  |
          |   |"""
    ))
    Assertions.assertEquals(4, goban.numLiberties(Set(Position(2, 1, 1))))

  @Test def testNumLibertiesAllNeighborsFreeInCorner(): Unit =
    val goban = fromStrings(Map(
      1 ->
        """@   |
          |   |
          |   |"""
    ))
    Assertions.assertEquals(3, goban.numLiberties(Set(Position(1, 1, 1))))

  @Test def testNumLibertiesOfFriendlyNeighborsAdded(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          |@@ |
          |   |"""
    ))
    Assertions.assertEquals(9, goban.numLiberties(Set(Position(2, 2, 2), Position(1, 2, 2))))

  @Test def testNumLibertiesOfFriendlyAndEnemyNeighborsAdded(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          |@@O|
          |   |"""
    ))
    Assertions.assertEquals(8, goban.numLiberties(Set(Position(2, 2, 2), Position(1, 2, 2))))
    Assertions.assertEquals(4, goban.numLiberties(Set(Position(3, 2, 2))))

  @Test def testNumLibertiesWithSeparateArea(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """  @|
          |@@O|
          |   |"""
    ))
    Assertions.assertEquals(8, goban.numLiberties(Set(Position(2, 2, 2), Position(1, 2, 2))))
    Assertions.assertEquals(3, goban.numLiberties(Set(Position(3, 2, 2))))
    Assertions.assertEquals(3, goban.numLiberties(Set(Position(3, 1, 2))))

  @Test def testNumLibertiesTotal1Stone(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          | @ |
          |   |"""
    ))
    Assertions.assertEquals(6, goban.numLiberties(Black))
    Assertions.assertEquals(0, goban.numLiberties(White))

  @Test def testNumLibertiesTotal2DifferentStones(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          | @O|
          |   |"""
    ))
    Assertions.assertEquals(5, goban.numLiberties(Black))
    Assertions.assertEquals(4, goban.numLiberties(White))

  @Test def testNumLiberties3SeparateAreas(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """  @|
          |@@O|
          |   |"""
    ))
    Assertions.assertEquals(10, goban.numLiberties(Black))
    Assertions.assertEquals(3, goban.numLiberties(White))

  @Test def testFreeNeighbors1Stone(): Unit =
    val goban = fromStrings(Map(
      2 ->
        """   |
          | @ |
          |   |"""
    ))
    Assertions.assertEquals(
      Set(Position(1, 2, 2), Position(2, 2, 1), Position(2, 1, 2), Position(2, 2, 3), Position(2, 3, 2), Position(3, 2, 2)),
      goban.neighbors(Black)
    )

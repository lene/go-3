package go3d

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestGroups:

  @Test def testAGroupCountsALibertyItSharesOnce(): Unit =
    val goban = Goban.start(3).success.value
      .setStone(Move(1, 1, 1, Black))
      .setStone(Move(2, 1, 1, Black))
      .setStone(Move(1, 2, 1, White))
    val groups = Groups.of(goban)
    // (1 1 2), (3 1 1), (2 2 1) and (2 1 2); (1 2 1) is White's
    Assertions.assertEquals(4, groups.liberties(Position(1, 1, 1)))
    Assertions.assertEquals(4, groups.liberties(Position(2, 1, 1)))
    // (2 2 1), (1 3 1) and (1 2 2)
    Assertions.assertEquals(3, groups.liberties(Position(1, 2, 1)))
    Assertions.assertEquals(0, groups.liberties(Position(3, 3, 3)))

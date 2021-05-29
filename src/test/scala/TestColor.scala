package go3d.testing

import org.junit.{Assert, Test}
import go3d.{Black, White, Empty, Sentinel}

class TestColor:
  @Test def testColorsNotEqual(): Unit =
    Assert.assertNotEquals(Black, White)
    Assert.assertNotEquals(Black, Empty)
    Assert.assertNotEquals(White, Empty)
    Assert.assertNotEquals(Black, Sentinel)
    Assert.assertNotEquals(White, Sentinel)
    Assert.assertNotEquals(Empty, Sentinel)

  @Test def testToString(): Unit =
    Assert.assertEquals(" ", Empty.toString)
    Assert.assertEquals("@", Black.toString)
    Assert.assertEquals("O", White.toString)
    Assert.assertEquals("·", Sentinel.toString)
    // we don't care about the string representation of the other values
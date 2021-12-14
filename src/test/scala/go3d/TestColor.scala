package go3d

import org.junit.{Assert, Test}

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

  @Test def tesAllowedColors(): Unit =
    Color(' ')
    Color('@')
    Color('O')
    Color('·')
  
  @Test def testBadColor(): Unit = assertThrows[BadColor]({Color('+')})

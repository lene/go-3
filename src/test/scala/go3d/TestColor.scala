package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestColor:
  @Test def testColorsNotEqual(): Unit =
    Assertions.assertNotEquals(Black, White)
    Assertions.assertNotEquals(Black, Empty)
    Assertions.assertNotEquals(White, Empty)
    Assertions.assertNotEquals(Black, Sentinel)
    Assertions.assertNotEquals(White, Sentinel)
    Assertions.assertNotEquals(Empty, Sentinel)

  @Test def testToString(): Unit =
    Assertions.assertEquals(" ", Empty.toString)
    Assertions.assertEquals("@", Black.toString)
    Assertions.assertEquals("O", White.toString)
    Assertions.assertEquals("·", Sentinel.toString)
    // we don't care about the string representation of the other values

  @Test def tesAllowedColors(): Unit =
    Color(' ')
    Color('@')
    Color('O')
    Color('·')
  
  @Test def testBadColor(): Unit = Assertions.assertThrows(classOf[BadColor], () =>Color('+'))

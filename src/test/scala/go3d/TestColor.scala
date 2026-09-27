package go3d

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

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

  @Test def testAllowedColors(): Unit =
    Color(' ').success.value
    Color('@').success.value
    Color('O').success.value
    Color('·').success.value

  @Test def testBadColor(): Unit =
    Assertions.assertInstanceOf(classOf[BadColor], Color('+').failure.exception)

  @Test def testUnaryNot(): Unit =
    Assertions.assertEquals(White, !Black)
    Assertions.assertEquals(Black, !White)
    Assertions.assertThrows(classOf[RuntimeException], () => !Empty)
    Assertions.assertThrows(classOf[RuntimeException], () => !Sentinel)

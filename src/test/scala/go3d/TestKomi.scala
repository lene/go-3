package go3d

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.util.Try

class TestKomi:

  private def fromText(text: String, size: Int): Try[Double] =
    Komi.parse(text, size)

  private def assertBadKomi(result: Try[Double], description: String): Unit =
    Assertions.assertInstanceOf(classOf[BadKomi], result.failure.exception, description)

  @Test def testDefaultKomiIsHalf(): Unit =
    Assertions.assertEquals(0.5, Komi.Default)

  @Test def testHalfIntegersAreParsed(): Unit =
    val expected = Map(
      "0.5" -> 0.5,
      "-0.5" -> -0.5,
      "1.5" -> 1.5,
      "6.5" -> 6.5,
      "-6.5" -> -6.5,
      "6.50" -> 6.5,
      "06.5" -> 6.5,
      "26.5" -> 26.5,
      "-26.5" -> -26.5
    )
    for (text, komi) <- expected do Assertions.assertEquals(komi, fromText(text, 3).success.value)

  @Test def testIntegersAreRejected(): Unit =
    for text <- List("0", "7", "-7", "6.0", "6.00", "-0", "0.0", "100") do
      assertBadKomi(fromText(text, 5), text)

  @Test def testNonDecimalTextIsRejected(): Unit =
    val texts = List(
      "abc",
      "NaN",
      "Infinity",
      "-Infinity",
      "6.5f",
      "6.5d",
      "1e2",
      "1E2",
      "6.5e0",
      ".5",
      "6.",
      "",
      " ",
      " 6.5",
      "6.5 ",
      "+6.5",
      "--6.5",
      "6,5",
      "0x6",
      "6.5.5",
      "6..5",
      "-",
      "-.5",
      "٦.٥",
      "6.5\n",
      "−6.5"
    )
    for text <- texts do assertBadKomi(fromText(text, 5), "[" + text + "]")

  @Test def testTextBeyondDoublePrecisionIsRejected(): Unit =
    assertBadKomi(fromText("6.5000000000000001", 5), "rounds to 6.5 as a double")
    assertBadKomi(fromText("6.4999999999999999", 5), "rounds to 6.5 as a double")
    assertBadKomi(fromText("6." + "0" * 40 + "1", 5), "tiny fraction")

  @Test def testLongTextOfHalfIntegerIsAccepted(): Unit =
    Assertions.assertEquals(6.5, fromText("6." + "5" + "0" * 40, 5).success.value)

  @Test def testHugeNumberIsRejected(): Unit =
    assertBadKomi(fromText("9" * 400 + ".5", 25), "far above the board")
    assertBadKomi(fromText("-" + "9" * 400 + ".5", 25), "far below the board")

  @Test def testMagnitudeIsBoundedByBoardPoints(): Unit =
    Assertions.assertEquals(26.5, fromText("26.5", 3).success.value)
    assertBadKomi(fromText("27.5", 3), "27.5 on 3")
    assertBadKomi(fromText("-27.5", 3), "-27.5 on 3")
    Assertions.assertEquals(27.5, fromText("27.5", 5).success.value)
    Assertions.assertEquals(124.5, fromText("124.5", 5).success.value)
    assertBadKomi(fromText("125.5", 5), "125.5 on 5")
    Assertions.assertEquals(15624.5, fromText("15624.5", 25).success.value)
    assertBadKomi(fromText("15625.5", 25), "15625.5 on 25")

  @Test def testBoundDoesNotOverflowForLargeSizes(): Unit =
    // 1291^3 does not fit an Int
    Assertions.assertEquals(2_100_000_000.5, fromText("2100000000.5", 1291).success.value)
    Assertions.assertEquals(0.5, fromText("0.5", Int.MaxValue).success.value)
    assertBadKomi(fromText("1" + "0" * 30 + ".5", Int.MaxValue), "above the cube of max int")

  @Test def testNoKomiIsValidForSizeWithoutPoints(): Unit =
    assertBadKomi(fromText("0.5", 0), "size 0")
    assertBadKomi(fromText("0.5", -3), "negative size")

  @Test def testBadKomiMessageDoesNotEchoInput(): Unit =
    val secret = "secret-input-123"
    val message = fromText(secret, 5).failure.exception.getMessage
    Assertions.assertFalse(message.contains(secret), message)
    val number = fromText("12345.5678", 5).failure.exception.getMessage
    Assertions.assertFalse(number.contains("12345"), number)

  @Test def testBadKomiIsIllegalMove(): Unit =
    Assertions.assertInstanceOf(classOf[IllegalMove], BadKomi())

  @Test def testValidateAcceptsHalfIntegers(): Unit =
    for komi <- List(0.5, -0.5, 6.5, -6.5, 13.5, 26.5) do
      Assertions.assertEquals(komi, Komi.validate(komi, 3).success.value)

  @Test def testValidateRejectsWholeAndFractionalNumbers(): Unit =
    for komi <- List(0.0, -0.0, 1.0, 7.0, -7.0, 0.25, 6.500000000000001, 6.4999999999999991) do
      assertBadKomi(Komi.validate(komi, 3), komi.toString)

  @Test def testValidateRejectsNonFiniteNumbers(): Unit =
    for komi <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do
      assertBadKomi(Komi.validate(komi, 3), komi.toString)

  @Test def testValidateRejectsHugeNumbers(): Unit =
    for komi <- List(1e300, -1e300, Double.MaxValue, 1e15 + 0.5, -1e15 - 0.5) do
      assertBadKomi(Komi.validate(komi, 25), komi.toString)

  @Test def testValidateBoundsMagnitudeByBoardPoints(): Unit =
    Assertions.assertEquals(26.5, Komi.validate(26.5, 3).success.value)
    assertBadKomi(Komi.validate(27.5, 3), "27.5 on 3")
    assertBadKomi(Komi.validate(-27.5, 3), "-27.5 on 3")
    Assertions.assertEquals(27.5, Komi.validate(27.5, 5).success.value)

  @Test def testValidateDoesNotOverflowForLargeSizes(): Unit =
    Assertions.assertEquals(2_100_000_000.5, Komi.validate(2_100_000_000.5, 1291).success.value)
    assertBadKomi(Komi.validate(0.5, 0), "size 0")
    assertBadKomi(Komi.validate(0.5, Int.MinValue), "min int size")

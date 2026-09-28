package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*
import org.rogach.scallop.exceptions.ValidationFailure
import java.net.UnknownHostException
import scala.util.Try

val ClientTestPort = 64556

/// Asserts that parsing `args` with `parse` fails with an exception of type `expected`.
def assertParseFails[E <: Throwable](
  expected: Class[E], parse: Array[String] => Try[BaseClient], args: String*
): Unit =
  Assertions.assertInstanceOf(expected, parse(args.toArray).failure.exception)

private val LocalUrl = "http://localhost:" + ClientTestPort.toString

class TestAsciiClient:

  @Test def testUrlReplacesServerAndPort(): Unit =
    val client = AsciiClient.parseArgs(Array("--url", LocalUrl + "/", "--game-id", "ABCDEF"))
    Assertions.assertEquals(LocalUrl, client.success.value.serverURL)

  @Test def testUrlWithServerFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure], AsciiClient.parseArgs,
      "--url", LocalUrl, "--server", "localhost", "--game-id", "ABCDEF"
    )

  @Test def testUrlWithPortFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure], AsciiClient.parseArgs,
      "--url", LocalUrl, "--port", ClientTestPort.toString, "--game-id", "ABCDEF"
    )

  @Test def testUrlWithUnsupportedSchemeFails(): Unit =
    assertParseFails(
      classOf[IllegalArgumentException], AsciiClient.parseArgs,
      "--url", "ftp://localhost", "--game-id", "ABCDEF"
    )

  @Test def testBadColor(): Unit =
    assertParseFails(
      classOf[BadColor], AsciiClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
    )

  @Test def testUnknownHost(): Unit =
    assertParseFails(
      classOf[UnknownHostException], AsciiClient.parseArgs,
      "--server", "doesnt-exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
    )

  @Test def testMissingServer(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], AsciiClient.parseArgs,
      "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
    )

  @Test def testMissingPort(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], AsciiClient.parseArgs,
      "--server", "localhost", "--size", "3", "--color", "b"
    )

  @Test def testMissingColor(): Unit =
    assertParseFails(
      classOf[ValidationFailure], AsciiClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString
    )

  @Test def testMissingSize(): Unit =
    assertParseFails(
      classOf[ValidationFailure], AsciiClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
    )

  @Test def testConflictingArguments(): Unit =
    assertParseFails(
      classOf[ValidationFailure], AsciiClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--game-id", "1"
    )

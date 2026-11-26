package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*
import org.rogach.scallop.exceptions.ValidationFailure
import java.net.UnknownHostException
import scala.util.Try

val ClientTestPort = 64556

// command line options used by the client tests
val ServerOpt = "--server"
val PortOpt = "--port"
val UrlOpt = "--url"
val GameIdOpt = "--game-id"
val TokenOpt = "--token"
val SizeOpt = "--size"
val ColorOpt = "--color"

/** A game id of the form the server gives out, for the tests that need no game behind it. */
val ValidGameId = "ABCDEF"

/// Asserts that parsing `args` with `parse` fails with an exception of type `expected`.
def assertParseFails[E <: Throwable](
    expected: Class[E],
    parse: Array[String] => Try[BaseClient],
    args: String*
): Unit =
  Assertions.assertInstanceOf(expected, parse(args.toArray).failure.exception)

private val LocalUrl = "http://localhost:" + ClientTestPort.toString

class TestAsciiClient:

  @Test def testUrlReplacesServerAndPort(): Unit =
    val client = AsciiClient.parseArgs(Array(UrlOpt, LocalUrl + "/", GameIdOpt, ValidGameId))
    Assertions.assertEquals(LocalUrl, client.success.value.serverURL)

  @Test def testMalformedGameIdFails(): Unit =
    for id <- List("<id>", "", "ABCDE", "ABCDEFG", "ABC-EF") do
      assertParseFails(
        classOf[ValidationFailure],
        AsciiClient.parseArgs,
        UrlOpt,
        LocalUrl,
        GameIdOpt,
        id
      )

  @Test def testMalformedGameIdWithTokenFailsBeforeAnyRequest(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      UrlOpt,
      LocalUrl,
      GameIdOpt,
      "<id>",
      TokenOpt,
      "token"
    )

  @Test def testUrlWithServerFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      UrlOpt,
      LocalUrl,
      ServerOpt,
      "localhost",
      GameIdOpt,
      ValidGameId
    )

  @Test def testUrlWithPortFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      UrlOpt,
      LocalUrl,
      PortOpt,
      ClientTestPort.toString,
      GameIdOpt,
      ValidGameId
    )

  @Test def testUrlWithUnsupportedSchemeFails(): Unit =
    assertParseFails(
      classOf[IllegalArgumentException],
      AsciiClient.parseArgs,
      UrlOpt,
      "ftp://localhost",
      GameIdOpt,
      ValidGameId
    )

  @Test def testBadColor(): Unit =
    assertParseFails(
      classOf[BadColor],
      AsciiClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "bx"
    )

  @Test def testUnknownHost(): Unit =
    assertParseFails(
      classOf[UnknownHostException],
      AsciiClient.parseArgs,
      ServerOpt,
      "doesnt-exist",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingServer(): Unit =
    assertParseFails(
      classOf[NoSuchElementException],
      AsciiClient.parseArgs,
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingPort(): Unit =
    assertParseFails(
      classOf[NoSuchElementException],
      AsciiClient.parseArgs,
      ServerOpt,
      "localhost",
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingColor(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString
    )

  @Test def testMissingSize(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      ColorOpt,
      "b"
    )

  @Test def testConflictingArguments(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      AsciiClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      GameIdOpt,
      ValidGameId
    )

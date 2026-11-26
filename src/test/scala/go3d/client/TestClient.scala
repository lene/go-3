package go3d.client

import go3d.BadColor
import org.junit.jupiter.api.{Assertions, Test}
import org.rogach.scallop.exceptions.ValidationFailure
import org.scalatest.TryValues.*

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}
import java.util.NoSuchElementException

class TestClient:

  @Test def testServerAndPortMakeHttpUrl(): Unit =
    Assertions.assertEquals(
      "http://localhost:6030",
      Client.serverUrl(None, Some("localhost"), Some(6030)).success.value
    )

  @Test def testHttpsUrlIsUsedWithoutTrailingSlash(): Unit =
    val url = "https://abc.execute-api.eu-central-1.amazonaws.com"
    Assertions.assertEquals(url, Client.serverUrl(Some(url + "/"), None, None).success.value)

  @Test def testUrlTakesPrecedenceOverServerAndPort(): Unit =
    Assertions.assertEquals(
      "http://other:1",
      Client.serverUrl(Some("http://other:1"), Some("x"), Some(2)).success.value
    )

  @Test def testUrlWithoutSchemeFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[IllegalArgumentException],
      Client.serverUrl(Some("localhost:6030"), None, None).failure.exception
    )

  @Test def testMalformedUrlFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[IllegalArgumentException],
      Client.serverUrl(Some("http://bad host"), None, None).failure.exception
    )

  @Test def testMissingServerOrPortNamesOption(): Unit =
    Assertions.assertEquals(
      "server",
      Client.serverUrl(None, None, Some(6030)).failure.exception.getMessage
    )
    Assertions.assertEquals(
      "port",
      Client.serverUrl(None, Some("localhost"), None).failure.exception.getMessage
    )

  @Test def testMalformedGameIdFailsWithOneLine(): Unit =
    val args = Array(UrlOpt, "http://localhost:1", GameIdOpt, "<id>")
    val failure = AsciiClient.parseArgs(args).failure.exception
    Assertions.assertInstanceOf(classOf[ValidationFailure], failure)
    val message = Client.failureMessage(failure)
    Assertions.assertTrue(message.contains("game-id"), message)
    Assertions.assertFalse(message.contains("\n"), message)
    Assertions.assertFalse(message.startsWith("unexpected error"), message)

  @Test def testFailureMessagesOfKnownErrors(): Unit =
    val expected = List(
      UnknownHostException("nowhere") -> "unknown host: nowhere",
      ConnectException("refused") -> "connection problem: refused",
      NumberFormatException("x") -> "not a number: x",
      IOException("disk") -> "disk",
      BadColor('x') -> "not a color, must be either black/b/@ or white/w/O",
      MoveEngineError("no model") -> "no model",
      ValidationFailure("bad option") -> "bad option",
      NoSuchElementException("server") -> "missing argument: --server",
      IllegalArgumentException("--url") -> "missing argument: --url"
    )
    for (e, message) <- expected do Assertions.assertEquals(message, Client.failureMessage(e))

  @Test def testUnexpectedErrorComesWithItsStackTrace(): Unit =
    val message = Client.failureMessage(IllegalStateException("boom"))
    Assertions.assertTrue(message.startsWith("unexpected error: boom "), message)
    Assertions.assertTrue(message.contains("TestClient"), message)

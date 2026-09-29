package go3d.client

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestClient:

  @Test def testServerAndPortMakeHttpUrl(): Unit =
    Assertions.assertEquals(
      "http://localhost:6030", Client.serverUrl(None, Some("localhost"), Some(6030)).success.value
    )

  @Test def testHttpsUrlIsUsedWithoutTrailingSlash(): Unit =
    val url = "https://abc.execute-api.eu-central-1.amazonaws.com"
    Assertions.assertEquals(url, Client.serverUrl(Some(url + "/"), None, None).success.value)

  @Test def testUrlTakesPrecedenceOverServerAndPort(): Unit =
    Assertions.assertEquals(
      "http://other:1", Client.serverUrl(Some("http://other:1"), Some("x"), Some(2)).success.value
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
      "server", Client.serverUrl(None, None, Some(6030)).failure.exception.getMessage
    )
    Assertions.assertEquals(
      "port", Client.serverUrl(None, Some("localhost"), None).failure.exception.getMessage
    )

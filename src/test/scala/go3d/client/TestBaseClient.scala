package go3d.client

import org.junit.jupiter.api.{Assertions, Test}

class TestBaseClient:

  @Test def testTokenExpandsToAuthHeader(): Unit =
    val client = BaseClient("", "", Some("test"))
    Assertions.assertEquals(Map("Authentication" -> s"Bearer test"), client.headers)
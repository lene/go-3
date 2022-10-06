package go3d.client

import org.junit.{After, Assert, Before, Test}

class TestBaseClient:

  @Test def testTokenExpandsToAuthHeader(): Unit =
    val client = BaseClient("", "", Some("test"))
    Assert.assertEquals(Map("Authentication" -> s"Bearer test"), client.headers)
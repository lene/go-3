package go3d.client

import org.junit.{Assert, Test}
import org.lwjgl.Version

import math.Ordered.orderingToOrdered

class TestLWJGL:

  @Test def testLWJGLLoaded(): Unit =
    Assert.assertTrue(Version.getVersion().compareTo("3.3.0") >= 0)

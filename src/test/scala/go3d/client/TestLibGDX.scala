package go3d.client

import com.badlogic.gdx.Version

import org.junit.{Assert, Test, Ignore}

class TestLibGDX:

  @Test def testVersion(): Unit =
    Assert.assertTrue(Version.isHigherEqual(1, 11, 0))
package go3d.client

import go3d.client.gdx.GobanDisplay
import go3d.server.StatusResponse

import com.badlogic.gdx.Version
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application

import org.junit.jupiter.api.{Assertions, Test, Disabled}

class TestLibGDX:

  @Test def testVersion(): Unit =
    Assertions.assertTrue(Version.isHigherEqual(1, 11, 0))

  @Disabled("Only run this with \"sbt test\"")
  @Test def testInstantiateClient(): Unit =
    /**
      Running this in sbt repeatedly causes:
      java.lang.UnsatisfiedLinkError: Native Library /tmp/lwjgl{$USER}/.../liblwjgl.so already loaded in another classloader
      So only run this with "sbt test"
      Also does not yet have a way to stop the application.
    */
    val dummyClient = new MockClient
    val config = GDXClient.getConfiguration("3D Go", 1280, 960)
    new Lwjgl3Application(new GobanDisplay(dummyClient), config).exit()

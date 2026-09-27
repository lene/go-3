package go3d.client

import org.junit.jupiter.api.{Assertions, Test}

class TestGDXClient:

  private val baseArgs = Array("--server", "localhost", "--port", "6030", "--game-id", "TESTID")

  @Test def testReplayOptionsAreParsed(): Unit =
    val args = baseArgs ++ Array("--replay", "--from", "2", "--to", "5", "--replay-speed", "0.5")
    Assertions.assertTrue(GDXClient.parseArgs(args).isSuccess)
    Assertions.assertTrue(GDXClient.replayEnabled)
    Assertions.assertEquals(2, GDXClient.replayFrom)
    Assertions.assertEquals(5, GDXClient.replayTo)
    Assertions.assertEquals(0.5f, GDXClient.replaySpeed)

  @Test def testReplayIsOffByDefault(): Unit =
    Assertions.assertTrue(GDXClient.parseArgs(baseArgs).isSuccess)
    Assertions.assertFalse(GDXClient.replayEnabled)
    Assertions.assertEquals(0, GDXClient.replayFrom)
    Assertions.assertEquals(Int.MaxValue, GDXClient.replayTo)
    Assertions.assertEquals(1.0f, GDXClient.replaySpeed)

  @Test def testReplayOptionsRequireReplay(): Unit =
    Assertions.assertTrue(GDXClient.parseArgs(baseArgs ++ Array("--from", "2")).isFailure)

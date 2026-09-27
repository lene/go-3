package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*
import org.rogach.scallop.exceptions.ValidationFailure
import java.net.UnknownHostException

val ClientTestPort = 64556

class TestAsciiClient:

  @Test def testBadColor(): Unit =
    Assertions.assertInstanceOf(
      classOf[BadColor],
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      )).failure.exception
    )

  @Test def testUnknownHost(): Unit =
    Assertions.assertInstanceOf(
      classOf[UnknownHostException],
      AsciiClient.parseArgs(Array(
        "--server", "doesnt-exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingServer(): Unit =
    Assertions.assertInstanceOf(
      classOf[NoSuchElementException],
      BotClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingPort(): Unit =
    Assertions.assertInstanceOf(
      classOf[NoSuchElementException],
      BotClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingColor(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString
      )).failure.exception
    )

  @Test def testMissingSize(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      )).failure.exception
    )

  @Test def testConflictingArguments(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString,
        "--size", "3", "--game-id", "1"
      )).failure.exception
    )

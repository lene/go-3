package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import org.rogach.scallop.exceptions.ValidationFailure
import java.net.UnknownHostException

val ClientTestPort = 64556

class TestAsciiClient:

  @Test def testBadColor(): Unit =
    Assertions.assertThrows(
      classOf[BadColor], () => {
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    Assertions.assertThrows(
      classOf[UnknownHostException], () => {
      AsciiClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingServer(): Unit =
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => {
        BotClient.parseArgs(Array(
          "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
        ))
      })

  @Test def testMissingPort(): Unit =
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => {
        BotClient.parseArgs(Array(
          "--server", "localhost", "--size", "3", "--color", "b"
        ))
      })

  @Test def testMissingColor(): Unit =
    Assertions.assertThrows(
      classOf[ValidationFailure], () => {
        BotClient.parseArgs(Array(
          "--server", "localhost", "--port", ClientTestPort.toString
        ))
      })

  @Test def testMissingSize(): Unit =
    Assertions.assertThrows(
      classOf[ValidationFailure], () => {
        AsciiClient.parseArgs(Array(
          "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
        ))
      })

  @Test def testConflictingArguments(): Unit =
    Assertions.assertThrows(
      classOf[ValidationFailure], () => {
        AsciiClient.parseArgs(Array(
          "--server", "localhost", "--port", ClientTestPort.toString,
          "--size", "3", "--game-id", "1"
        ))
      })

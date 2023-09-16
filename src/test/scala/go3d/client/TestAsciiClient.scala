package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
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

  @Test def testMissingArguments(): Unit =
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => {
      AsciiClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => {
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => {
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      ))
    })
    Assertions.assertThrows(
      classOf[IllegalArgumentException], () => {
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      ))
    })

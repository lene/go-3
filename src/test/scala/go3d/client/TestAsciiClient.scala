package go3d.client

import go3d._
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

val ClientTestPort = 64556

class TestAsciiClient:

  @Test def testBadColor(): Unit =
    assertThrows[BadColor]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    assertThrows[UnknownHostException]({
      AsciiClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingArguments(): Unit =
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      ))
    })
    assertThrows[IllegalArgumentException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      ))
    })

package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import java.net.UnknownHostException
import org.rogach.scallop.exceptions.{RequiredOptionNotFound, ValidationFailure}

class TestBotClient:

  @Test def testBadColor(): Unit =
    Assertions.assertThrows(
      classOf[BadColor], () => {
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    Assertions.assertThrows(
      classOf[UnknownHostException], () => {
      BotClient.parseArgs(Array(
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
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
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

  @Test def testStrategyIsParsed(): Unit =
    BotClient.parseArgs(Array(
      "--server", "localhost", "--port", ClientTestPort.toString, "--game-id", "",  "--token", "",
      "--strategy", "random"
    ))
    Assertions.assertTrue(Array("random").sameElements(BotClient.strategies))

  @Test def testStrategyWithMultipleElementsIsParsed(): Unit =
    BotClient.parseArgs(Array(
      "--server", "localhost", "--port", ClientTestPort.toString, "--game-id", "",  "--token", "",
      "--strategy", "closestToStarPoints,prioritiseCapture"
    ))
    Assertions.assertTrue(
      Array("closestToStarPoints","prioritiseCapture").sameElements(BotClient.strategies)
    )


  @Test def testExecutionTimeString(): Unit =
    Assertions.assertEquals("", BotClient.executionTimeString)
    BotClient.executionTimes = BotClient.executionTimes.appended(10)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(10ms last/10ms avg)"))
    BotClient.executionTimes = BotClient.executionTimes.appended(30)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(30ms last/20ms avg)"))

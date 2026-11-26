package go3d.probe

import go3d.*
import go3d.server.{FileIO, IdGenerator, Player}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.Files

class TestSampleRecords:

  @Test def testTheFirstThreeGamesOfAPairingAreSamples(): Unit =
    Assertions.assertEquals(3, SampleRecords.PerPairing)
    Assertions.assertEquals(
      List(true, true, true, false, false),
      (0 until 5).map(SampleRecords.isSample)
    )

  @Test def testRecordIdsAreValidGameIdsOfTheServer(): Unit =
    for
      size <- ProbeOptions.BoardSizes
      pairing <- ProbeOptions.defaults(1).pairings
      game <- List(0, 1, 2, 999_999)
    do
      val id = SampleRecords.recordId(size, pairing._1, pairing._2, game)
      Assertions.assertTrue(IdGenerator.isValidId(id), id)

  @Test def testNoTwoGamesShareARecordId(): Unit =
    val ids =
      for
        size <- ProbeOptions.BoardSizes
        (black, white) <- ProbeOptions.defaults(1).pairings
        game <- 0 until 500
      yield SampleRecords.recordId(size, black, white, game)
    Assertions.assertEquals(ids.length, ids.distinct.length)

  @Test def testARecordIdDoesNotDependOnTheStrategiesOfARun(): Unit =
    Assertions.assertEquals(
      SampleRecords.recordId(7, "random", "onStarPoints", 2),
      SampleRecords.recordId(7, "random", "onStarPoints", 2)
    )
    Assertions.assertNotEquals(
      SampleRecords.recordId(7, "random", "onStarPoints", 2),
      SampleRecords.recordId(7, "onStarPoints", "random", 2)
    )

  @Test def testRecordIdsStayTheSame(): Unit =
    // the viewer finds the samples of earlier runs by these ids
    Assertions.assertEquals("0LegcK", SampleRecords.recordId(5, "random", "random", 0))
    Assertions.assertEquals("0US9YY", SampleRecords.recordId(7, "random", "onStarPoints", 2))
    val capture = "prioritiseCapture"
    Assertions.assertEquals("0hJNEd", SampleRecords.recordId(9, capture, capture, 999_999))

  @Test def testARecordLoadsThroughTheServersFileIoAndReplaysToItsScore(): Unit =
    val game = GamePlayer.play(3, "prioritiseCapture", "random", 5L).success.value
    val id = SampleRecords.recordId(5, "prioritiseCapture", "random", 0)
    val out = Files.createTempDirectory("rules-probe-records")
    SampleRecords.write(out, List(id -> SampleRecords.saveGame(game, id)))

    val fileIO = FileIO(out.toString).success.value
    Assertions.assertTrue(fileIO.archivedExists(id))
    Assertions.assertEquals(List(id), fileIO.getArchivedGames)
    val loaded = fileIO.loadArchivedGame(id).success.value

    Assertions.assertEquals(game, loaded.game)
    Assertions.assertEquals(
      Map(Black -> Player(Black, id), White -> Player(White, id)),
      loaded.players
    )
    val replayed =
      loaded.game.moves.foldLeft(Game.start(loaded.game.size, loaded.game.komi).success.value) {
        (acc, move) => acc.makeMove(move).success.value
      }
    Assertions.assertEquals(game.score, replayed.score)
    Assertions.assertEquals(game.winner, replayed.winner)
    Assertions.assertTrue(replayed.isOver)

  @Test def testWritingNoRecordsStillMakesTheArchiveFolder(): Unit =
    val out = Files.createTempDirectory("rules-probe-records")
    SampleRecords.write(out, Nil)
    Assertions.assertTrue(Files.isDirectory(out.resolve("archive")))

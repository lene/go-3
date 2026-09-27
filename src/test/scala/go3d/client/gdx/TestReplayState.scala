package go3d.client.gdx

import go3d.Black
import go3d.White
import go3d.client.BaseClient
import go3d.server.{NullRequestInfo, StatusResponse}
import org.junit.jupiter.api.{Assertions, Test}

import scala.util.{Failure, Success, Try}

class TestReplayState:

  private def makeStatusResponse(moveCount: Int): StatusResponse =
    var game = go3d.Game.start(3).get
    for i <- 1 to moveCount do
      val pos = go3d.Position(((i-1) % 3) + 1, ((i-1) / 3 % 3) + 1, ((i-1) / 9) + 1)
      game = game.makeMove(go3d.Move(pos, if i % 2 == 1 then Black else White)).getOrElse(game)
    StatusResponse(game = game, moves = List(), ready = false, over = false, playerColor = None, debug = NullRequestInfo)

  private def stubClient(responses: Map[Int, Try[StatusResponse]]): BaseClient =
    new BaseClient("http://test", "TESTID", None, None):
      override def statusAtMove(n: Int): Try[StatusResponse] =
        responses.getOrElse(n, Failure(new RuntimeException(s"no stub for move $n")))

  @Test def testAdvanceIncrementsCurrentIndex(): Unit =
    val sr1 = makeStatusResponse(1)
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(0 -> Success(makeStatusResponse(0)), 1 -> Success(sr1), 2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.currentStatus = Some(sr1)
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertEquals(Some(sr2), state.currentStatus)

  @Test def testAdvanceAtToMinusOneStopsAutoPlay(): Unit =
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 2, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = true
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertFalse(state.isPlaying)

  @Test def testAdvanceAtEndIsNoOp(): Unit =
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 2, autoPlayDelay = 1.0f)
    state.currentIndex = 2
    state.currentStatus = Some(sr2)
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertEquals(Some(sr2), state.currentStatus)

  @Test def testRewindDecrementsCurrentIndex(): Unit =
    val sr0 = makeStatusResponse(0)
    val client = stubClient(Map(0 -> Success(sr0)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.rewind()
    Assertions.assertEquals(0, state.currentIndex)

  @Test def testRewindAtFromIsNoOp(): Unit =
    val sr0 = makeStatusResponse(0)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 0
    state.currentStatus = Some(sr0)
    state.rewind()
    Assertions.assertEquals(0, state.currentIndex)
    Assertions.assertEquals(Some(sr0), state.currentStatus)

  @Test def testTickTriggerAdvanceAfterDelay(): Unit =
    val sr1 = makeStatusResponse(1)
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = true
    state.currentStatus = Some(sr1)
    state.tick(1.0f)
    Assertions.assertEquals(2, state.currentIndex)

  @Test def testTickIsNoOpWhenNotPlaying(): Unit =
    val sr1 = makeStatusResponse(1)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = false
    state.currentStatus = Some(sr1)
    state.tick(99.0f)
    Assertions.assertEquals(1, state.currentIndex)
    Assertions.assertEquals(Some(sr1), state.currentStatus)

  @Test def testFetchFailureLeavesCurrentStatusUnchanged(): Unit =
    val sr1 = makeStatusResponse(1)
    val client = stubClient(Map(1 -> Failure(new RuntimeException("network error"))))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.currentStatus = Some(sr1)
    state.fetch()
    Assertions.assertEquals(Some(sr1), state.currentStatus)

  @Test def testHudLinesBeforeFirstFetch(): Unit =
    val state = new ReplayState(stubClient(Map()), from = 0, to = 3, autoPlayDelay = 1.0f)
    Assertions.assertEquals(Seq("Move 0 / 3", "", ""), state.hudLines)

  @Test def testHudLinesMidGame(): Unit =
    val state = new ReplayState(stubClient(Map()), from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 2
    state.currentStatus = Some(makeStatusResponse(2))
    Assertions.assertEquals(
      Seq("Move 2 / 3", s"$Black to move", "Black captures: 0  White captures: 0"),
      state.hudLines
    )

  @Test def testHudLinesAtGameOver(): Unit =
    val over = go3d.Game.start(3).get
      .makeMove(go3d.Pass(Black)).get
      .makeMove(go3d.Pass(White)).get
    val state = new ReplayState(stubClient(Map()), from = 0, to = 2, autoPlayDelay = 1.0f)
    state.currentIndex = 2
    state.currentStatus = Some(StatusResponse(
      game = over, moves = List(), ready = false, over = true, playerColor = None,
      debug = NullRequestInfo
    ))
    Assertions.assertEquals("Game over", state.hudLines(1))

  @Test def testProgressLineShowsCounterAndLastMove(): Unit =
    val state = new ReplayState(stubClient(Map()), from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 2
    state.currentStatus = Some(makeStatusResponse(2))
    // move 2 of makeStatusResponse is white at (2, 1, 1)
    Assertions.assertEquals(s"[2/3] white $White 2 1 1", state.progressLine)

  @Test def testProgressLineAtStartHasNoMove(): Unit =
    val state = new ReplayState(stubClient(Map()), from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentStatus = Some(makeStatusResponse(0))
    Assertions.assertEquals("[0/3]", state.progressLine)

  @Test def testProgressLineShowsPass(): Unit =
    val passed = go3d.Game.start(3).get.makeMove(go3d.Pass(Black)).get
    val state = new ReplayState(stubClient(Map()), from = 0, to = 1, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.currentStatus = Some(StatusResponse(
      game = passed, moves = List(), ready = false, over = false, playerColor = None,
      debug = NullRequestInfo
    ))
    Assertions.assertEquals(s"[1/1] black $Black pass", state.progressLine)

  @Test def testFetchPrintsProgressOnOneLine(): Unit =
    val client = stubClient(
      Map(1 -> Success(makeStatusResponse(1)), 0 -> Success(makeStatusResponse(0)))
    )
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    val buffer = new java.io.ByteArrayOutputStream()
    state.out = new java.io.PrintStream(buffer, true, "UTF-8")
    state.currentIndex = 1
    state.fetch()
    state.currentIndex = 0
    state.fetch()
    val first = s"[1/3] black $Black 1 1 1"
    // the shorter second line is padded so that no characters of the first line remain
    Assertions.assertEquals(
      first + "\r" + "[0/3]".padTo(first.length, ' ') + "\r", buffer.toString("UTF-8")
    )

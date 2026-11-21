package go3d.server.http4s

import go3d.server.{
  GameCreatedResponse, OpenGame, OpenGamesResponse, Players, RateLimiter, decodeOpenGamesResponse,
  IdGenerator, PlayerRegisteredResponse, StatusResponse, decodeGameCreatedResponse,
  decodePlayerRegisteredResponse, decodeStatusResponse
}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import go3d.{BadKomiTexts, Black, Color, Move, Pass, White}
import go3d.server.service.{GamePositions, StatusAssertions}
import io.circe.Json
import io.circe.parser.{decode, parse}
import org.http4s.implicits.uri
import org.http4s.{EntityDecoder, Headers, Method, Request, Response, Status, Uri}
import org.junit.jupiter.api.{Assertions, BeforeEach, Test}

class TestGoHttpService extends LazyLogging:

  private val gameSize = 7
  val goHttpService: GoHttpService = GoHttpService(0)

  @BeforeEach def resetRateLimiter(): Unit = RateLimiter.reset()

  def check[A](
      actual: IO[Response[IO]],
      expectedStatus: Status,
      expectedBody: Option[A]
  )(
      implicit ev: EntityDecoder[IO, A]
  ): Boolean =
    val actualResp = actual.unsafeRunSync()
    val statusCheck = actualResp.status == expectedStatus
    val bodyCheck = expectedBody.fold[Boolean](
      actualResp.body.compile.toVector.unsafeRunSync().isEmpty
    )(expected => actualResp.as[A].unsafeRunSync() == expected)
    if !bodyCheck then logger.error(s"${actualResp.as[A]} != $expectedBody")
    statusCheck && bodyCheck

  def runRequest[A](target: Uri): IO[Response[IO]] =
    GoHttpService(0).httpApp.run(Request[IO](Method.GET, target))

  private def httpRequest(path: String, headers: Headers = Headers.empty): Request[IO] =
    Request[IO](Method.GET, Uri.fromString(path).getOrElse(Assertions.fail()), headers = headers)

  @Test def testRunningServerWithInvalidPortThrows(): Unit =
    Assertions.assertThrows(
      classOf[IllegalArgumentException],
      () => GoHttpService(-1).server.use(_ => IO.unit).unsafeRunSync()
    )

  @Test def testHealth(): Unit =
    val actual = runRequest(uri"/health")
    val expectedBody = "1"
    Assertions.assertTrue(check(actual, Status.Ok, Some(expectedBody)))

  @Test def testNew(): Unit =
    createGameWithId()

  private def createGameWithId(): String =
    val request = httpRequest(s"/new/$gameSize")
    checkedGameId(request)

  private def checkedGameId(request: Request[IO]): String =
    val json = getJson(request)
    val result = decode[GameCreatedResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(gameSize), result.map(_.size))
    Assertions.assertTrue(IdGenerator.isValidId(result.map(_.id).getOrElse(Assertions.fail())))
    result.map(_.id).getOrElse(Assertions.fail())

  @Test def testRegisterPlayer(): Unit =
    val gameId = createGameWithId()
    checkedRegisterPlayer(gameId, Black)

  private def getJson(request: Request[IO]): String =
    val response = goHttpService.httpApp.run(request).unsafeRunSync()
    Assertions.assertEquals(Status.Ok, response.status)
    response.body.compile.toList.unsafeRunSync().map(_.toChar).mkString

  private def checkedRegisterPlayer(gameId: String, color: Color): String =
    val request = httpRequest(s"/register/$gameId/$color")
    val json = getJson(request)
    val result = decode[PlayerRegisteredResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(color), result.map(_.color))
    Assertions.assertTrue(
      IdGenerator.isValidToken(result.map(_.authToken).getOrElse(Assertions.fail()))
    )
    Assertions.assertFalse(result.map(_.ready).getOrElse(Assertions.fail()))
    result.map(_.authToken).getOrElse(Assertions.fail())

  @Test def testGameStatusEmptyGame(): Unit =
    val gameId = createGameWithId()
    val request = httpRequest(s"/status/$gameId")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(false), result.map(_.ready))
    Assertions.assertEquals(Right(false), result.map(_.over))

  @Test def testGameStatusBlackRegistered(): Unit =
    val gameId = createGameWithId()
    checkedRegisterPlayer(gameId, Black)
    val request = httpRequest(s"/status/$gameId")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(false), result.map(_.ready))
    Assertions.assertEquals(Right(false), result.map(_.over))

  @Test def testGameStatusBlackAndWhiteRegistered(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    val request = httpRequest(
      s"/status/$gameId",
      authHeader(blackToken)
    )
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(true), result.map(_.ready))
    Assertions.assertEquals(Right(false), result.map(_.over))

  @Test def testSetStone(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    val request = httpRequest(s"/set/$gameId/1/1/1", authHeader(blackToken))
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertTrue(result.map(_.game.moves.contains(Move(
      1,
      1,
      1,
      Black
    ))).getOrElse(Assertions.fail()))

  @Test def testPassTurn(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    val request = httpRequest(s"/pass/$gameId", authHeader(blackToken))
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(1, result.map(_.game.moves.length).getOrElse(Assertions.fail()))
    Assertions.assertEquals(Pass(Black), result.map(_.game.moves.head).getOrElse(Assertions.fail()))

  private def authHeader(token: String) = Headers(("Authentication", s"Bearer $token"))

  @Test def testListOpenGamesWithoutOpenGame(): Unit =
    val gameId = createGameWithId()
    checkedRegisterPlayer(gameId, White)
    val request = httpRequest(s"/openGames")
    val json = getJson(request)
    val result = decode[OpenGamesResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertFalse(result.map(_.ids.contains(gameId)).getOrElse(Assertions.fail()))

  @Test def testListOpenGamesWithOpenGame(): Unit =
    val gameId = createGameWithId()
    checkedRegisterPlayer(gameId, Black)
    val request = httpRequest(s"/openGames")
    val json = getJson(request)
    val result = decode[OpenGamesResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertTrue(result.map(_.ids.contains(gameId)).getOrElse(Assertions.fail()))

  private def openGames(): OpenGamesResponse =
    decode[OpenGamesResponse](getJson(httpRequest("/openGames"))).getOrElse(Assertions.fail())

  /** A new game of size 3 with only Black registered; returns its id. */
  private def openGame(query: String): String =
    val gameId = createdGameId(3, query)
    checkedRegisterPlayer(gameId, Black)
    gameId

  @Test def testOpenGamesCarryTheKomiOfEachGame(): Unit =
    val withoutKomi = openGame("")
    val large = openGame("?komi=6.5")
    val negative = openGame("?komi=-0.5")
    val games = openGames().games.toSet
    Assertions.assertTrue(games.contains(OpenGame(withoutKomi, 0.5)), games.toString)
    Assertions.assertTrue(games.contains(OpenGame(large, 6.5)), games.toString)
    Assertions.assertTrue(games.contains(OpenGame(negative, -0.5)), games.toString)

  @Test def testOpenGamesListIdsAndGamesInTheSameOrder(): Unit =
    for query <- List("", "?komi=6.5", "?komi=-0.5", "?komi=1.5") do openGame(query)
    val json = parse(getJson(httpRequest("/openGames"))).getOrElse(Assertions.fail()).hcursor
    val ids = json.downField("ids").as[List[String]].getOrElse(Assertions.fail())
    val gameIds = json.downField("games").as[List[Json]].getOrElse(Assertions.fail())
      .map(_.hcursor.downField("id").as[String].getOrElse(Assertions.fail()))
    Assertions.assertTrue(ids.length >= 4, ids.toString)
    Assertions.assertEquals(ids, gameIds)

  @Test def testOpenGamesLeaveOutGamesWithBothOrNoPlayers(): Unit =
    val open = openGame("?komi=6.5")
    val full = openGame("?komi=6.5")
    checkedRegisterPlayer(full, White)
    val noPlayers = createdGameId(3, "?komi=6.5")
    val response = openGames()
    Assertions.assertTrue(response.games.contains(OpenGame(open, 6.5)))
    for gameId <- List(full, noPlayers) do
      Assertions.assertFalse(response.ids.contains(gameId))
      Assertions.assertFalse(response.games.map(_.id).contains(gameId))

  @Test def testOpenGamesLeaveOutAPlayerWithoutAGame(): Unit =
    val open = openGame("?komi=6.5")
    val goneId = IdGenerator.getId
    Assertions.assertTrue(Players.register(goneId, Black).isSuccess)
    // the phantom player must not stay in the global Players for other tests
    try
      Assertions.assertTrue(Players.openGames().contains(goneId))
      val response = openGames()
      Assertions.assertTrue(response.games.contains(OpenGame(open, 6.5)))
      Assertions.assertFalse(response.ids.contains(goneId))
      Assertions.assertFalse(response.games.map(_.id).contains(goneId))
    finally Players.unregister(goneId)

  @Test def testGetArchivedGameNotFound(): Unit =
    val request = httpRequest(s"/archived/NONEXISTENT")
    val response = goHttpService.httpApp.run(request).unsafeRunSync()
    Assertions.assertEquals(Status.NotFound, response.status)

  @Test def testGetArchivedGameWithUnknownValidIdIsNotFound(): Unit =
    // unlike "NONEXISTENT" above, a well-formed id gets past the route to GetArchivedGame, which
    // checks the local archive and S3 before answering
    val request = httpRequest(s"/archived/${IdGenerator.getId}")
    val response = goHttpService.httpApp.run(request).unsafeRunSync()
    Assertions.assertEquals(Status.NotFound, response.status)
    Assertions.assertTrue(response.as[String].unsafeRunSync().contains("not found in archive"))

  @Test def testStatusAtMoveZeroReturnsEmptyBoard(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    goHttpService.httpApp.run(httpRequest(
      s"/set/$gameId/1/1/1",
      authHeader(blackToken)
    )).unsafeRunSync()
    val request = httpRequest(s"/status/$gameId/0")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(0), result.map(_.game.moves.length))

  @Test def testStatusAtMoveOneReturnsOneMove(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    goHttpService.httpApp.run(httpRequest(
      s"/set/$gameId/1/1/1",
      authHeader(blackToken)
    )).unsafeRunSync()
    val request = httpRequest(s"/status/$gameId/1")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(1), result.map(_.game.moves.length))

  @Test def testStatusAtMoveBeyondEndReturnsFinalBoard(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    goHttpService.httpApp.run(httpRequest(
      s"/set/$gameId/1/1/1",
      authHeader(blackToken)
    )).unsafeRunSync()
    val request = httpRequest(s"/status/$gameId/999")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(1), result.map(_.game.moves.length))

  @Test def testStatusAtMoveNegativeClampedToZero(): Unit =
    val gameId = createGameWithId()
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    goHttpService.httpApp.run(httpRequest(
      s"/set/$gameId/1/1/1",
      authHeader(blackToken)
    )).unsafeRunSync()
    val request = httpRequest(s"/status/$gameId/-1")
    val json = getJson(request)
    val result = decode[StatusResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(0), result.map(_.game.moves.length))

  @Test def testStatusAtMoveUnknownGameReturns404(): Unit =
    val actual = runRequest(uri"/status/NONEXISTENT/0")
    val response = actual.unsafeRunSync()
    Assertions.assertEquals(Status.NotFound, response.status)

  /** Komi queries that make `/new/3` fail: every plain bad text, a bare parameter and repeated
    * parameters.
    */
  private val BadKomiQueries: List[String] =
    BadKomiTexts.map(text => s"?komi=$text") ++
      List("?komi", "?komi=6.5&komi=6.5", "?komi=6.5&komi=7.5")

  private def newGame(size: Int, query: String): Response[IO] =
    RateLimiter.reset()
    goHttpService.httpApp.run(httpRequest(s"/new/$size$query")).unsafeRunSync()

  private def createdGameId(size: Int, query: String): String =
    val response = newGame(size, query)
    Assertions.assertEquals(Status.Ok, response.status)
    decode[GameCreatedResponse](response.as[String].unsafeRunSync())
      .map(_.id).getOrElse(Assertions.fail())

  private def statusOfGame(gameId: String): StatusResponse =
    decode[StatusResponse](getJson(httpRequest(s"/status/$gameId"))).getOrElse(Assertions.fail())

  private def authorizedGet(path: String, token: String): String =
    getJson(httpRequest(path, authHeader(token)))

  @Test def testNewGameWithoutKomiHasDefaultKomi(): Unit =
    Assertions.assertEquals(0.5, statusOfGame(createdGameId(3, "")).game.komi)

  @Test def testNewGameWithKomiHasThatKomi(): Unit =
    for komi <- List("6.5", "-0.5", "26.5", "6.50") do
      val gameId = createdGameId(3, s"?komi=$komi")
      Assertions.assertEquals(komi.toDouble, statusOfGame(gameId).game.komi)

  @Test def testKomiIsInRegisterResponseAndStatusJson(): Unit =
    val gameId = createdGameId(3, "?komi=6.5")
    val registered = decode[PlayerRegisteredResponse](getJson(httpRequest(s"/register/$gameId/@")))
    Assertions.assertEquals(Right(6.5), registered.map(_.game.komi))
    Assertions.assertTrue(getJson(httpRequest(s"/status/$gameId")).contains("\"komi\":6.5"))

  @Test def testUnrelatedQueryParametersAreIgnored(): Unit =
    Assertions.assertEquals(0.5, statusOfGame(createdGameId(3, "?other=1")).game.komi)
    Assertions.assertEquals(6.5, statusOfGame(createdGameId(3, "?other=1&komi=6.5")).game.komi)

  @Test def testNewGameWithBadKomiReturns400(): Unit =
    for query <- BadKomiQueries do
      val response = newGame(3, query)
      Assertions.assertEquals(Status.BadRequest, response.status, query)
      Assertions.assertTrue(response.as[String].unsafeRunSync().contains("BadKomi"), query)

  @Test def testBadKomiErrorDoesNotEchoInput(): Unit =
    val response = newGame(3, "?komi=secret-input-123")
    Assertions.assertEquals(Status.BadRequest, response.status)
    Assertions.assertFalse(response.as[String].unsafeRunSync().contains("secret-input-123"))

  @Test def testNewGameWithKomiBeyondBoardPointsReturns400(): Unit =
    Assertions.assertEquals(Status.Ok, newGame(3, "?komi=26.5").status)
    Assertions.assertEquals(Status.BadRequest, newGame(3, "?komi=27.5").status)
    Assertions.assertEquals(Status.Ok, newGame(5, "?komi=27.5").status)

  @Test def testNewGameWithBadSizeAndKomiReturns400(): Unit =
    Assertions.assertEquals(Status.BadRequest, newGame(4, "?komi=7").status)
    Assertions.assertEquals(Status.BadRequest, newGame(27, "?komi=6.5").status)

  @Test def testKomiSurvivesMovesAndReplay(): Unit =
    val gameId = createdGameId(3, "?komi=6.5")
    val blackToken = checkedRegisterPlayer(gameId, Black)
    val whiteToken = checkedRegisterPlayer(gameId, White)
    val set = decode[StatusResponse](authorizedGet(s"/set/$gameId/2/2/2", blackToken))
    Assertions.assertEquals(Right(6.5), set.map(_.game.komi))
    val passed = decode[StatusResponse](authorizedGet(s"/pass/$gameId", whiteToken))
    Assertions.assertEquals(Right(6.5), passed.map(_.game.komi))
    for count <- 0 to 2 do
      val replayed = decode[StatusResponse](getJson(httpRequest(s"/status/$gameId/$count")))
      Assertions.assertEquals(Right(6.5), replayed.map(_.game.komi))

  /** Plays the `positions` on the smallest board that holds them and ends the game with two
    * passes, and returns the response to each move.
    */
  private def playToTheEnd(query: String, positions: List[GamePositions.Position]): List[String] =
    val gameId = createdGameId(GamePositions.boardSize(positions), query)
    val black = checkedRegisterPlayer(gameId, Black)
    val white = checkedRegisterPlayer(gameId, White)
    val setResponses = GamePositions.withTokens(black, white, positions).map {
      case (token, (x, y, z)) => authorizedGet(s"/set/$gameId/$x/$y/$z", token)
    }
    val passes = GamePositions.passingTokens(black, white, positions)
      .map(token => authorizedGet(s"/pass/$gameId", token))
    setResponses ++ passes

  /** Only the end of the game names the winner. The server archives a finished game and reads it
    * back from the file store of the shared `Games`, which only `TestServer` sets up for its
    * tests, so the status fetched after the end is checked there.
    */
  private def assertWinnerOnlyWhenOver(
      query: String,
      positions: List[GamePositions.Position],
      winner: Color
  ): Unit =
    val responses = playToTheEnd(query, positions)
    responses.init.foreach(StatusAssertions.assertNotOver)
    StatusAssertions.assertWonBy(responses.last, winner)

  @Test def testWinnerWithoutKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver("", GamePositions.BlackLeadsByOne, Black)

  @Test def testWinnerWithKomiHalfIsBlack(): Unit =
    assertWinnerOnlyWhenOver("?komi=0.5", GamePositions.BlackLeadsByOne, Black)

  @Test def testWinnerWithLargeKomiIsWhite(): Unit =
    assertWinnerOnlyWhenOver("?komi=6.5", GamePositions.BlackLeadsByOne, White)

  @Test def testWinnerOfEmptyBoardWithNegativeKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver("?komi=-0.5", GamePositions.EmptyBoard, Black)

  @Test def testWinnerOfEmptyBoardWithKomiHalfIsWhite(): Unit =
    assertWinnerOnlyWhenOver("?komi=0.5", GamePositions.EmptyBoard, White)

  @Test def testCaptureDoesNotMakeWhiteWin(): Unit =
    assertWinnerOnlyWhenOver("?komi=-2.5", GamePositions.BeforeKo, Black)

  @Test def testStatusOfGameInProgressHasNoWinner(): Unit =
    val gameId = createdGameId(3, "?komi=6.5")
    StatusAssertions.assertNotOver(getJson(httpRequest(s"/status/$gameId")))
    val blackToken = checkedRegisterPlayer(gameId, Black)
    checkedRegisterPlayer(gameId, White)
    StatusAssertions.assertNotOver(authorizedGet(s"/status/$gameId", blackToken))

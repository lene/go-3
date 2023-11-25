package go3d.server.http4s

import go3d.server.{GameCreatedResponse, OpenGamesResponse, decodeOpenGamesResponse, GoResponse, IdGenerator, PlayerRegisteredResponse, StatusResponse, decodeGameCreatedResponse, decodePlayerRegisteredResponse, decodeStatusResponse}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import go3d.{Black, Color, Move, Pass, White}
import io.circe.parser.decode
import org.http4s.implicits.uri
import org.http4s.{EntityDecoder, Headers, Method, Request, Response, Status, Uri}
import org.junit.jupiter.api.{Assertions, Test}

class TestGoHttpService extends LazyLogging:

  private val gameSize = 7
  val goHttpService: GoHttpService = GoHttpService(0)

  def check[A](
    actual: IO[Response[IO]], expectedStatus: Status, expectedBody: Option[A]
  )(
    implicit ev: EntityDecoder[IO, A]
  ): Boolean =
    val actualResp = actual.unsafeRunSync()
    val statusCheck = actualResp.status == expectedStatus
    val bodyCheck = expectedBody.fold[Boolean](
      actualResp.body.compile.toVector.unsafeRunSync().isEmpty)(
      expected => actualResp.as[A].unsafeRunSync() == expected
    )
    if !bodyCheck then logger.error(s"${actualResp.as[A]} != $expectedBody")
    statusCheck && bodyCheck

  def runRequest[A](target: Uri): IO[Response[IO]] =
    GoHttpService(0).httpApp.run(Request[IO](Method.GET, target))

  private def httpRequest(path: String, headers: Headers = Headers.empty): Request[IO] =
    Request[IO](Method.GET, Uri.fromString(path).getOrElse(Assertions.fail()), headers = headers)

  @Test def testRunningServerWithInvalidPortThrows(): Unit =
    Assertions.assertThrows(classOf[IllegalArgumentException], () => GoHttpService(-1).server)

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
      s"/status/$gameId", authHeader(blackToken)
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
    Assertions.assertTrue(result.map(_.game.moves.contains(Move(1, 1, 1, Black))).getOrElse(Assertions.fail()))

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


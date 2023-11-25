package go3d.server.http4s

import go3d.server.{
  GameCreatedResponse, decodeGameCreatedResponse,
  PlayerRegisteredResponse, decodePlayerRegisteredResponse,
  IdGenerator
}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ch.qos.logback.core.pattern.color.BlackCompositeConverter
import com.typesafe.scalalogging.LazyLogging
import go3d.Black
import io.circe.parser.decode
import org.http4s.implicits.uri
import org.http4s.{EntityDecoder, Method, Request, Response, Status, Uri}
import org.junit.jupiter.api.{Assertions, Test}

class TestGoHttpService extends LazyLogging:

  private val gameSize = 7
  val goHttpService = GoHttpService(8080)

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

  private def sendRequest(path: String): Request[IO] =
    Request[IO](Method.GET, Uri.fromString(path).getOrElse(Assertions.fail()))

  @Test def testRunningServerWithInvalidPortThrows(): Unit =
    Assertions.assertThrows(classOf[IllegalArgumentException], () => GoHttpService(-1).server)

  @Test def testHealth(): Unit =
    val actual = runRequest(uri"/health")
    val expectedBody = "1"
    Assertions.assertTrue(check(actual, Status.Ok, Some(expectedBody)))

  @Test def testNew(): Unit =
    createGameWithId()

  private def createGameWithId(): String =
    val request = sendRequest(s"/new/$gameSize")
    val response = goHttpService.httpApp.run(request).unsafeRunSync()
    checkedGameId(response)

  private def checkedGameId(response: Response[IO]): String =
    Assertions.assertEquals(Status.Ok, response.status)
    val json = response.body.compile.toList.unsafeRunSync().map(_.toChar).mkString
    val result = decode[GameCreatedResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(7), result.map(_.size))
    Assertions.assertTrue(IdGenerator.isValidId(result.map(_.id).getOrElse(Assertions.fail())))
    result.map(_.id).getOrElse(Assertions.fail())

  @Test def testRegisterPlayer(): Unit =
    val gameId = createGameWithId()
    val request = sendRequest(s"/register/$gameId/$Black")
    val response = goHttpService.httpApp.run(request).unsafeRunSync()
    Assertions.assertEquals(Status.Ok, response.status)
    val json = response.body.compile.toList.unsafeRunSync().map(_.toChar).mkString
    val result = decode[PlayerRegisteredResponse](json)
    Assertions.assertTrue(result.isRight)
    Assertions.assertEquals(Right(Black), result.map(_.color))
    Assertions.assertTrue(
      IdGenerator.isValidToken(result.map(_.authToken).getOrElse(Assertions.fail()))
    )
    Assertions.assertFalse(result.map(_.ready).getOrElse(Assertions.fail()))
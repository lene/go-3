package go3d.server.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import org.http4s.implicits.uri
import org.http4s.{EntityDecoder, Method, Request, Response, Status, Uri}
import org.junit.jupiter.api.{Assertions, Test}

class TestGoHttpService extends LazyLogging:

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

  @Test def testRunningServerWithInvalidPortThrows(): Unit =
    Assertions.assertThrows(classOf[IllegalArgumentException], () => GoHttpService(-1).server)

  @Test def testHealth(): Unit =
    val actual = runRequest(uri"/health")
    val expectedBody = "1"
    Assertions.assertTrue(check(actual, Status.Ok, Some(expectedBody)))

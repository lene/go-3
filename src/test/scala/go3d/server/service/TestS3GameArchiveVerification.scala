package go3d.server.service

import go3d.{Black, Game}
import go3d.server.{Player, SaveGame, encodeSaveGame}
import io.circe.syntax._
import org.junit.jupiter.api.{AfterEach, Assertions, Test}
import org.scalatest.TryValues.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.{
  HeadObjectRequest, HeadObjectResponse, PutObjectRequest, PutObjectResponse, S3Exception
}
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

/** An S3 client that accepts every upload and answers HeadObject with `headLength`, or fails with
  * the exception it holds. It lets [[S3GameArchive]]'s verification be tested without S3. The
  * uploaded bodies are kept in `uploads`.
  */
@SuppressWarnings(Array("org.wartremover.warts.Throw"))
private class FakeS3Client(headLength: Try[Long]) extends AwsS3Client:
  def serviceName(): String = "s3"
  def close(): Unit = ()

  val uploads: ListBuffer[String] = ListBuffer()

  override def putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse =
    uploads +=
      String(body.contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8)
    PutObjectResponse.builder().build()

  override def headObject(request: HeadObjectRequest): HeadObjectResponse =
    headLength match
      case Success(length) => HeadObjectResponse.builder().contentLength(length).build()
      case Failure(e) => throw e

/** The checks of [[S3GameArchive.store]] that S3Mock does not exercise: an archive that arrived
  * truncated, or cannot be verified, must not yield a key, so the game is not marked completed.
  */
class TestS3GameArchiveVerification:
  private val presigner = S3Presigner.builder()
    .region(Region.EU_CENTRAL_1)
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("a", "b")))
    .build()

  private val json = """{"game":"x"}"""

  private def archiveWith(headLength: Try[Long]): S3GameArchive =
    new S3GameArchive(FakeS3Client(headLength), presigner, "bucket")

  @AfterEach def closePresigner(): Unit = presigner.close()

  @Test def testStoredArchiveOfExpectedLengthReturnsKey(): Unit =
    Assertions.assertEquals(
      Success("archives/GAME01.json"),
      archiveWith(Success(json.length.toLong)).store("GAME01", json)
    )

  @Test def testArchiveOfWrongLengthFails(): Unit =
    archiveWith(Success(json.length.toLong - 1)).store("GAME01", json) match
      case Failure(e: IllegalStateException) =>
        Assertions.assertTrue(e.getMessage.contains("archives/GAME01.json"), e.getMessage)
      case other => Assertions.fail("expected a length mismatch, got " + other.toString)

  @Test def testArchiveThatCannotBeVerifiedFails(): Unit =
    archiveWith(Failure(S3Exception.builder().statusCode(404).build()))
      .store("GAME01", json) match
      case Failure(_: S3Exception) => ()
      case other => Assertions.fail("expected the S3 error, got " + other.toString)

  @Test def testArchiveUploadsGameJsonWithKomi(): Unit =
    val saveGame = SaveGame(Game.start(3, 6.5).success.value, Map(Black -> Player(Black, "GAME01")))
    val expected = saveGame.asJson.noSpaces
    val client = FakeS3Client(Success(expected.getBytes(StandardCharsets.UTF_8).length.toLong))
    val key = S3GameArchive(client, presigner, "bucket").archive("GAME01", saveGame)
    Assertions.assertEquals(Success("archives/GAME01.json"), key)
    Assertions.assertEquals(List(expected), client.uploads.toList)
    Assertions.assertTrue(expected.contains("\"komi\":6.5"), expected)

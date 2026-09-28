package go3d.server.service

import go3d.Black
import go3d.Game
import go3d.server.{Player, SaveGame, encodeSaveGame}
import io.circe.syntax._
import org.junit.jupiter.api.{AfterEach, Assertions, Assumptions, BeforeEach, Test}
import org.scalatest.TryValues.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  CreateBucketRequest, DeleteBucketRequest, DeleteObjectRequest, GetObjectRequest,
  ListObjectsV2Request
}

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Runs [[S3GameArchive]] against S3Mock (`adobe/s3mock`) at the endpoint in `S3_TEST_ENDPOINT`,
 * which CI sets; skipped when the variable is not set. Every test gets its own bucket.
 */
class TestS3GameArchive:
  private val endpoint = sys.env.get("S3_TEST_ENDPOINT")
  private val bucket = "go3d-test-" + java.util.UUID.randomUUID().toString

  private lazy val client: S3Client =
    S3Client.builder()
      .endpointOverride(URI.create(endpoint.getOrElse("http://localhost:9090")))
      .region(Region.EU_CENTRAL_1)
      .forcePathStyle(true)
      .credentialsProvider(
        StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
      )
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .build()

  private lazy val archive = new S3GameArchive(client, bucket)

  private def saveGame(gameId: String): SaveGame =
    SaveGame(Game.start(3).success.value, Map(Black -> Player(Black, gameId)))

  @BeforeEach def createBucket(): Unit =
    Assumptions.assumeTrue(endpoint.isDefined, "S3_TEST_ENDPOINT is not set")
    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    ()

  @AfterEach def deleteBucket(): Unit =
    if endpoint.isDefined then
      Try {
        val keys = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
          .contents().asScala.map(_.key())
        for key <- keys do
          client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build())
      }
      client.close()

  @Test def testArchiveReturnsKeyOfGame(): Unit =
    Assertions.assertEquals(
      "archives/GAME01.json", archive.archive("GAME01", saveGame("GAME01")).success.value
    )

  @Test def testArchivedGameCanBeReadBack(): Unit =
    val key = archive.archive("GAME01", saveGame("GAME01")).success.value
    val stored = client.getObjectAsBytes(
      GetObjectRequest.builder().bucket(bucket).key(key).build()
    ).asString(StandardCharsets.UTF_8)
    Assertions.assertEquals(saveGame("GAME01").asJson.noSpaces, stored)

  @Test def testArchiveToMissingBucketFails(): Unit =
    val missing = new S3GameArchive(client, bucket + "-missing")
    Assertions.assertTrue(missing.archive("GAME01", saveGame("GAME01")).isFailure)

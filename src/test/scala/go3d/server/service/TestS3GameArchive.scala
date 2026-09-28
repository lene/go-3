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
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}
import software.amazon.awssdk.services.s3.model.{
  CreateBucketRequest, DeleteBucketRequest, DeleteObjectRequest, GetObjectRequest,
  ListObjectsV2Request
}
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Runs [[S3GameArchive]] against S3Mock (`adobe/s3mock`) at the endpoint in `S3_TEST_ENDPOINT`,
 * which CI sets; skipped when the variable is not set. Every test gets its own bucket.
 */
class TestS3GameArchive:
  private val endpoint = sys.env.get("S3_TEST_ENDPOINT")
  private val bucket = "go3d-test-" + java.util.UUID.randomUUID().toString

  private val endpointUri = URI.create(endpoint.getOrElse("http://localhost:9090"))
  private val credentials =
    StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))

  private lazy val client: S3Client =
    S3Client.builder()
      .endpointOverride(endpointUri)
      .region(Region.EU_CENTRAL_1)
      .forcePathStyle(true)
      .credentialsProvider(credentials)
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .build()

  private lazy val presigner: S3Presigner =
    S3Presigner.builder()
      .endpointOverride(endpointUri)
      .region(Region.EU_CENTRAL_1)
      .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
      .credentialsProvider(credentials)
      .build()

  private lazy val archive = new S3GameArchive(client, presigner, bucket)

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
      presigner.close()

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
    val missing = new S3GameArchive(client, presigner, bucket + "-missing")
    Assertions.assertTrue(missing.archive("GAME01", saveGame("GAME01")).isFailure)

  @Test def testUrlOfArchivedGameDownloadsIt(): Unit =
    archive.archive("GAME01", saveGame("GAME01")).success.value
    val url = archive.url("GAME01").success.value
    Assertions.assertTrue(url.isDefined)
    val source = Source.fromURL(url.getOrElse(""), StandardCharsets.UTF_8.name())
    try Assertions.assertEquals(saveGame("GAME01").asJson.noSpaces, source.mkString)
    finally source.close()

  @Test def testUrlOfMissingArchiveIsNone(): Unit =
    Assertions.assertEquals(None, archive.url("NOGAME").success.value)

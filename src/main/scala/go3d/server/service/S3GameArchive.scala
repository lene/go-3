package go3d.server.service

import go3d.server.SaveGame
import go3d.server.aws.{S3Client, S3Config}
import go3d.server.encodeSaveGame
import io.circe.syntax._
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.{
  HeadObjectRequest, PutObjectRequest, ServerSideEncryption
}

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

/**
 * [[GameArchive]] on S3: each finished game is stored as JSON under `archives/<gameId>.json`
 * and verified with a HeadObject before its key is returned, so a game is only marked completed
 * (and eventually expired from DynamoDB) once its archive exists.
 */
class S3GameArchive(client: AwsS3Client, bucket: String) extends GameArchive:

  def archive(gameId: String, saveGame: SaveGame): Try[String] =
    store(gameId, saveGame.asJson.noSpaces)

  /** Uploads `json` as the archive of `gameId`, checks it arrived whole, and returns its key. */
  def store(gameId: String, json: String): Try[String] =
    val key = S3Config.s3Key(gameId)
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    for
      _ <- Try(client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key)
          .contentType("application/json")
          .contentLength(bytes.length.toLong)
          .serverSideEncryption(ServerSideEncryption.AES256)
          .build(),
        RequestBody.fromBytes(bytes)
      ))
      head <- Try(client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()))
      _ <-
        if head.contentLength() == bytes.length.toLong then Success(())
        else Failure(IllegalStateException(
          "archive " + key + " has " + head.contentLength().toString + " bytes, expected "
            + bytes.length.toString
        ))
    yield key

object S3GameArchive:
  /** The archive in the bucket configured in the environment, if S3 is configured. */
  def fromEnv(): Option[S3GameArchive] =
    S3Client.get().map((client, _, config) => new S3GameArchive(client, config.bucket))

/** Used when S3 is not configured: finished games stay in the store without an expiry. */
object UnconfiguredArchive extends GameArchive:
  def archive(gameId: String, saveGame: SaveGame): Try[String] =
    Failure(IllegalStateException("S3 archive is not configured"))

package go3d.server.aws

import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.time.Duration
import scala.util.{Failure, Success, Try}

/**
 * S3 archival client for game JSON storage.
 *
 * Lazily initialised from environment; no-op when S3 not configured.
 * Uses UrlConnectionHttpClient (shares the dependency with DynamoDB).
 */
object S3Client extends LazyLogging:

  private var _client: Option[AwsS3Client] = None
  private var _presigner: Option[S3Presigner] = None
  private var _config: Option[S3Config] = None
  private var _initialised = false

  val PresignedUrlExpiryMinutes: Int = 5

  /** Returns (client, presigner, config) when S3 is configured, None otherwise. */
  def get(): Option[(AwsS3Client, S3Presigner, S3Config)] =
    if !_initialised then init()
    for c <- _client ; p <- _presigner ; cfg <- _config yield (c, p, cfg)

  def init(): Unit =
    _initialised = true
    S3Config.fromEnv() match
      case None =>
        logger.debug("S3 not configured (AWS_REGION not set or S3_ENABLED=false)")
      case Some(config) =>
        Try(buildClients(config)) match
          case Success((client, presigner)) =>
            _client = Some(client)
            _presigner = Some(presigner)
            _config = Some(config)
            logger.info(s"S3 client initialised: region=${config.region}, bucket=${config.bucket}")
          case Failure(e) =>
            logger.warn(s"Failed to initialise S3 client: ${e.getMessage}")

  private[aws] def initWithConfig(config: S3Config): Try[Unit] =
    Try(buildClients(config)).map { case (client, presigner) =>
      _client = Some(client)
      _presigner = Some(presigner)
      _config = Some(config)
      _initialised = true
    }

  def close(): Unit =
    _client.foreach(_.close())
    _presigner.foreach(_.close())
    _client = None
    _presigner = None
    _config = None
    _initialised = false

  /**
   * Uploads game JSON to S3 as archives/gameId.json with AES-256 server-side encryption and
   * verifies it arrived. Succeeds without uploading when S3 is not configured.
   */
  def archiveGame(gameId: String, gameJson: String): Try[Unit] =
    get().fold[Try[Unit]](Success(())) { case (client, _, config) =>
      go3d.server.service.S3GameArchive(client, config.bucket).store(gameId, gameJson).map(_ => ())
    }

  /**
   * Generate a pre-signed GET URL for an archived game (5-minute expiry).
   * Returns None when S3 is not configured or the object does not exist.
   */
  def generatePresignedUrl(gameId: String): Option[String] =
    get().flatMap { case (client, presigner, config) =>
      val key = S3Config.s3Key(gameId)
      val exists = Try(client.headObject(
        HeadObjectRequest.builder().bucket(config.bucket).key(key).build()
      )).isSuccess
      Option.when(exists)(key).flatMap { _ =>
        Try {
          val getRequest = GetObjectRequest.builder()
            .bucket(config.bucket)
            .key(key)
            .build()
          val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PresignedUrlExpiryMinutes))
            .getObjectRequest(getRequest)
            .build()
          presigner.presignGetObject(presignRequest).url().toString
        }.toOption
      }
    }

  private def buildClients(config: S3Config): (AwsS3Client, S3Presigner) =
    val region = Region.of(config.region)
    val client = AwsS3Client.builder()
      .region(region)
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .build()
    val presigner = S3Presigner.builder()
      .region(region)
      .build()
    (client, presigner)

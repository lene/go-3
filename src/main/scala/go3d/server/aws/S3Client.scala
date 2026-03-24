package go3d.server.aws

import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.nio.charset.StandardCharsets
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
   * Upload game JSON to S3 as archives/gameId.json (best-effort).
   * Uses AES-256 server-side encryption.
   */
  def archiveGame(gameId: String, gameJson: String): Unit =
    get() match
      case None => ()
      case Some((client, _, config)) =>
        withRetry(s"archive game $gameId to S3") {
          val bytes = gameJson.getBytes(StandardCharsets.UTF_8)
          client.putObject(
            PutObjectRequest.builder()
              .bucket(config.bucket)
              .key(S3Config.s3Key(gameId))
              .contentType("application/json")
              .contentLength(bytes.length.toLong)
              .serverSideEncryption(ServerSideEncryption.AES256)
              .build(),
            RequestBody.fromBytes(bytes)
          )
        }

  /**
   * Generate a pre-signed GET URL for an archived game (5-minute expiry).
   * Returns None when S3 is not configured or the object does not exist.
   */
  def generatePresignedUrl(gameId: String): Option[String] =
    get().flatMap { case (_, presigner, config) =>
      val key = S3Config.s3Key(gameId)
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

  private def withRetry(operation: String)(op: => Unit): Unit =
    Try(op) match
      case Success(_) => ()
      case Failure(e1) =>
        logger.warn(s"S3 $operation failed (retrying): ${e1.getMessage}")
        Try(op) match
          case Success(_) => ()
          case Failure(e2) =>
            logger.error(s"S3 $operation failed permanently: ${e2.getMessage}")

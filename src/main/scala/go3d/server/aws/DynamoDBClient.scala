package go3d.server.aws

import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient

import scala.util.{Failure, Success, Try}

/**
 * Manages the lifecycle of the AWS DynamoDB client.
 * Initialised lazily on first use; no-op when DynamoDB is not configured.
 */
object DynamoDBClient extends LazyLogging:

  // Lazily built from DynamoDBConfig.fromEnv() on first access
  private var _client: Option[DynamoDbClient] = None
  private var _config: Option[DynamoDBConfig] = None
  private var _initialised = false

  /** Returns (client, config) when DynamoDB is configured, None otherwise. */
  def get(): Option[(DynamoDbClient, DynamoDBConfig)] =
    if !_initialised then init()
    for c <- _client ; cfg <- _config yield (c, cfg)

  /** Initialise from environment. Called automatically on first use. */
  def init(): Unit =
    _initialised = true
    DynamoDBConfig.fromEnv() match
      case None =>
        logger.debug("DynamoDB not configured (AWS_REGION not set or DYNAMODB_ENABLED=false)")
      case Some(config) =>
        Try(buildClient(config)) match
          case Success(client) =>
            _client = Some(client)
            _config = Some(config)
            logger.info(
              s"DynamoDB client initialised: region=${config.region}, " +
              s"games=${config.gamesTable}, players=${config.playersTable}"
            )
          case Failure(e) =>
            logger.warn(s"Failed to initialise DynamoDB client: ${e.getMessage}")

  /** Override config for testing. */
  private[aws] def initWithConfig(config: DynamoDBConfig): Try[Unit] =
    Try(buildClient(config)).map { client =>
      _client = Some(client)
      _config = Some(config)
      _initialised = true
    }

  /** Close the client (for clean shutdown). */
  def close(): Unit =
    _client.foreach(_.close())
    _client = None
    _config = None
    _initialised = false

  private def buildClient(config: DynamoDBConfig): DynamoDbClient =
    DynamoDbClient.builder()
      .region(Region.of(config.region))
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .build()

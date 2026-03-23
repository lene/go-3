package go3d.server.aws

/**
 * Configuration for DynamoDB integration.
 *
 * Reads from environment variables so the server works without AWS
 * when not configured (e.g. local development, CI).
 */
case class DynamoDBConfig(
  region: String,
  gamesTable: String,
  playersTable: String
)

object DynamoDBConfig:

  val DefaultGamesTable: String = "go3d-active-games"
  val DefaultPlayersTable: String = "go3d-players"

  /**
   * Load config from environment variables.
   * Returns None when AWS_REGION / AWS_DEFAULT_REGION is not set,
   * or when DYNAMODB_ENABLED=false, so callers can skip DynamoDB writes.
   */
  def fromEnv(): Option[DynamoDBConfig] =
    if sys.env.get("DYNAMODB_ENABLED").contains("false") then None
    else
      sys.env.get("AWS_REGION").orElse(sys.env.get("AWS_DEFAULT_REGION")).map { region =>
        DynamoDBConfig(
          region = region,
          gamesTable = sys.env.getOrElse("DYNAMODB_GAMES_TABLE", DefaultGamesTable),
          playersTable = sys.env.getOrElse("DYNAMODB_PLAYERS_TABLE", DefaultPlayersTable)
        )
      }

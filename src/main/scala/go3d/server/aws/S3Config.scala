package go3d.server.aws

/**
 * Configuration for S3 archival integration.
 * Reads from environment variables; returns None when not configured.
 */
case class S3Config(region: String, bucket: String)

object S3Config:

  val DefaultBucket: String = "go3d-game-archives"
  val ArchivePrefix: String = "archives"

  /** S3 key for a given gameId: archives/gameId.json */
  def s3Key(gameId: String): String = s"$ArchivePrefix/$gameId.json"

  /**
   * Load config from environment variables.
   * Returns None when AWS_REGION / AWS_DEFAULT_REGION is not set,
   * or when S3_ENABLED=false.
   */
  def fromEnv(): Option[S3Config] =
    if sys.env.get("S3_ENABLED").contains("false") then None
    else
      sys.env.get("AWS_REGION").orElse(sys.env.get("AWS_DEFAULT_REGION")).map { region =>
        S3Config(
          region = region,
          bucket = sys.env.getOrElse("S3_ARCHIVE_BUCKET", DefaultBucket)
        )
      }

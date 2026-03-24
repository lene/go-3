package go3d.server.aws

import com.typesafe.scalalogging.LazyLogging
import go3d.Color
import go3d.server.SecurityUtils
import software.amazon.awssdk.services.dynamodb.model._

import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Dual-write repository for player registrations in DynamoDB.
 *
 * All methods are best-effort: failures are logged but do NOT propagate
 * to the caller. The file-based system remains the source of truth.
 *
 * Tokens are stored as SHA-256 hashes — the plaintext token is never
 * written to DynamoDB.
 *
 * Schema (go3d-players):
 *   PK  gameId        String
 *   SK  color         String  ("@" or "O")
 *       authTokenHash String  (SHA-256 hex of the bearer token)
 *       createdAt     Number  (Unix seconds)
 *       expiresAt     Number  (Unix seconds, TTL attribute = createdAt + 24h)
 */
object DynamoDBPlayersRepository extends LazyLogging:

  private val TokenTtlHours = 24
  private val SecondsPerHour = 3600L

  /**
   * Write a player registration to DynamoDB (best-effort).
   *
   * Uses a condition expression to prevent overwriting an already-registered
   * player (idempotent re-registration is allowed; duplicate color is rejected
   * at the application layer before this is called).
   */
  def put(gameId: String, color: Color, token: String): Unit =
    DynamoDBClient.get() match
      case None => ()
      case Some((client, config)) =>
        withRetry(s"put player $gameId/${color}") {
          val now = System.currentTimeMillis() / 1000L
          val expiresAt = now + TokenTtlHours * SecondsPerHour
          val item: JMap[String, AttributeValue] = Map(
            "gameId"        -> str(gameId),
            "color"         -> str(color.toString),
            "authTokenHash" -> str(SecurityUtils.sha256(token)),
            "createdAt"     -> num(now),
            "expiresAt"     -> num(expiresAt),
          ).asJava
          client.putItem(
            PutItemRequest.builder()
              .tableName(config.playersTable)
              .item(item)
              .conditionExpression("attribute_not_exists(gameId)")
              .build()
          )
        }

  /**
   * Return game IDs that have exactly one player registered (open for opponent).
   * Scans the players table and returns IDs appearing exactly once.
   * Returns empty array when DynamoDB is not configured.
   */
  def openGames(): Array[String] =
    DynamoDBClient.get() match
      case None => Array.empty
      case Some((client, config)) =>
        Try(
          client.scan(
            ScanRequest.builder()
              .tableName(config.playersTable)
              .projectionExpression("gameId")
              .build()
          )
        ).map { response =>
          response.items().asScala.toSeq
            .flatMap(item => item.asScala.get("gameId").map(_.s()))
            .groupBy(identity)
            .collect { case (gId, occurrences) if occurrences.length == 1 => gId }
            .toArray
        }.getOrElse(Array.empty)

  /** Remove all player registrations for a game (best-effort, called on archive). */
  def deleteGame(gameId: String): Unit =
    DynamoDBClient.get() match
      case None => ()
      case Some((client, config)) =>
        for colorStr <- Seq("@", "O") do
          withRetry(s"delete player $gameId/$colorStr") {
            client.deleteItem(
              DeleteItemRequest.builder()
                .tableName(config.playersTable)
                .key(Map(
                  "gameId" -> str(gameId),
                  "color"  -> str(colorStr)
                ).asJava)
                .build()
            )
          }

  private def str(value: String): AttributeValue =
    AttributeValue.builder().s(value).build()

  private def num(value: Long): AttributeValue =
    AttributeValue.builder().n(value.toString).build()

  private def withRetry(operation: String)(op: => Unit): Unit =
    Try(op) match
      case Success(_) => ()
      case Failure(e1) =>
        logger.warn(s"DynamoDB $operation failed (retrying): ${e1.getMessage}")
        Try(op) match
          case Success(_) => ()
          case Failure(e2) =>
            logger.error(s"DynamoDB $operation failed permanently: ${e2.getMessage}")

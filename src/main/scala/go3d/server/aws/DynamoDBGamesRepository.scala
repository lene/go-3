package go3d.server.aws

import com.typesafe.scalalogging.LazyLogging
import go3d.Game
import go3d.server.SaveGame
import go3d.server.Players
import go3d.server.given  // top-level Circe encoders in go3d.server (Jsonify.scala)
import io.circe.parser._
import io.circe.syntax._
import software.amazon.awssdk.services.dynamodb.model._

import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Dual-write repository for game state in DynamoDB.
 *
 * All methods are best-effort: failures are logged but do NOT propagate
 * to the caller. The file-based system remains the source of truth.
 *
 * Schema (go3d-active-games):
 *   PK  gameId       String
 *       gameState    String  (JSON-serialised SaveGame)
 *       expiresAt    Number  (Unix seconds, TTL attribute)
 *       lastModified Number  (Unix millis)
 *       version      Number  (incremented on each update)
 */
object DynamoDBGamesRepository extends LazyLogging:

  private val ActiveGameTtlDays = 7
  private val SecondsPerDay = 86400L

  /** Write or update a game in DynamoDB (best-effort). */
  def put(gameId: String, game: Game): Unit =
    DynamoDBClient.get() match
      case None => ()
      case Some((client, config)) =>
        withRetry(s"put game $gameId") {
          val now = System.currentTimeMillis()
          val expiresAt = (now / 1000L) + ActiveGameTtlDays * SecondsPerDay
          val gameState = SaveGame(game, Players(gameId)).asJson.noSpaces
          val item: JMap[String, AttributeValue] = Map(
            "gameId"       -> str(gameId),
            "gameState"    -> str(gameState),
            "expiresAt"    -> num(expiresAt),
            "lastModified" -> num(now),
          ).asJava
          client.putItem(
            PutItemRequest.builder()
              .tableName(config.gamesTable)
              .item(item)
              .build()
          )
        }

  /** Read a game from DynamoDB. Returns None when DynamoDB is not configured or item not found. */
  def get(gameId: String): Option[SaveGame] =
    DynamoDBClient.get().flatMap { case (client, config) =>
      Try(
        client.getItem(
          GetItemRequest.builder()
            .tableName(config.gamesTable)
            .key(Map("gameId" -> str(gameId)).asJava)
            .build()
        )
      ).toOption.flatMap { response =>
        if response.hasItem then
          response.item().asScala.get("gameState")
            .flatMap(av => decode[SaveGame](av.s()).toOption)
        else None
      }
    }

  /** Remove a game from DynamoDB (best-effort, called on archive). */
  def delete(gameId: String): Unit =
    DynamoDBClient.get() match
      case None => ()
      case Some((client, config)) =>
        withRetry(s"delete game $gameId") {
          client.deleteItem(
            DeleteItemRequest.builder()
              .tableName(config.gamesTable)
              .key(Map("gameId" -> str(gameId)).asJava)
              .build()
          )
        }

  private def str(value: String): AttributeValue =
    AttributeValue.builder().s(value).build()

  private def num(value: Long): AttributeValue =
    AttributeValue.builder().n(value.toString).build()

  /** Run op, retrying once on transient failure, log and swallow on final failure. */
  private def withRetry(operation: String)(op: => Unit): Unit =
    Try(op) match
      case Success(_) => ()
      case Failure(e1) =>
        logger.warn(s"DynamoDB $operation failed (retrying): ${e1.getMessage}")
        Try(op) match
          case Success(_) => ()
          case Failure(e2) =>
            logger.error(s"DynamoDB $operation failed permanently: ${e2.getMessage}")

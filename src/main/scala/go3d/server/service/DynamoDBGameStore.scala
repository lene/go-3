package go3d.server.service

import go3d.Black
import go3d.Color
import go3d.Game
import go3d.server.DuplicateColor
import go3d.server.NonexistentGame
import go3d.server.aws.DynamoDBClient
import go3d.server.{decodeGame, encodeGame}
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * [[GameStore]] on DynamoDB, the authoritative store of the Lambda server.
 *
 * Games table (partition key `gameId`): `game` (JSON), `version`, `lastModified` (Unix millis),
 * and after completion `archiveKey` and the TTL attribute `expiresAt` (Unix seconds).
 * Players table (partition key `gameId`, sort key `color`): `authTokenHash`, `createdAt`, and
 * after completion `expiresAt`. Active games and players never expire.
 *
 * Reads are strongly consistent, so a request sees the writes of the previous one.
 */
class DynamoDBGameStore(client: DynamoDbClient, gamesTable: String, playersTable: String)
  extends GameStore:

  def createGame(gameId: String, game: Game): Try[Unit] =
    Try(client.putItem(
      PutItemRequest.builder().tableName(gamesTable)
        .item(Map(
          "gameId" -> str(gameId), "game" -> str(game.asJson.noSpaces), "version" -> num(0L),
          "lastModified" -> num(System.currentTimeMillis())
        ).asJava)
        .conditionExpression("attribute_not_exists(gameId)")
        .build()
    )).map(_ => ()).recoverWith {
      case _: ConditionalCheckFailedException => Failure(GameExists(gameId))
    }

  def getGame(gameId: String): Try[Option[StoredGame]] =
    Try(client.getItem(
      GetItemRequest.builder().tableName(gamesTable).key(gameKey(gameId)).consistentRead(true)
        .build()
    )).flatMap { response =>
      if !response.hasItem then Success(Option.empty[StoredGame])
      else
        val item = response.item().asScala
        for
          json <- attribute(item, "game").map(_.s())
          game <- decode[Game](json).toTry
          version <- attribute(item, "version").flatMap(v => Try(v.n().toLong))
          registered <- players(gameId)
        yield Option(StoredGame(game, registered.keySet, version))
    }

  def updateGame(gameId: String, expectedVersion: Long, game: Game): Try[Unit] =
    Try(client.updateItem(
      UpdateItemRequest.builder().tableName(gamesTable).key(gameKey(gameId))
        .updateExpression("SET #game = :game, #version = :next, #modified = :now")
        .conditionExpression("#version = :expected")
        .expressionAttributeNames(
          Map("#game" -> "game", "#version" -> "version", "#modified" -> "lastModified").asJava
        )
        .expressionAttributeValues(Map(
          ":game" -> str(game.asJson.noSpaces), ":next" -> num(expectedVersion + 1),
          ":now" -> num(System.currentTimeMillis()), ":expected" -> num(expectedVersion)
        ).asJava)
        .build()
    )).map(_ => ()).recoverWith {
      case _: ConditionalCheckFailedException =>
        getGame(gameId).flatMap(stored =>
          if stored.isEmpty then Failure(NonexistentGame(gameId, List()))
          else Failure(ConcurrentModification(gameId, expectedVersion))
        )
    }

  def registerPlayer(gameId: String, color: Color, tokenHash: String): Try[Unit] =
    Try(client.putItem(
      PutItemRequest.builder().tableName(playersTable)
        .item(Map(
          "gameId" -> str(gameId), "color" -> str(color.toString),
          "authTokenHash" -> str(tokenHash), "createdAt" -> num(System.currentTimeMillis() / 1000L)
        ).asJava)
        .conditionExpression("attribute_not_exists(gameId)")
        .build()
    )).map(_ => ()).recoverWith {
      case _: ConditionalCheckFailedException => Failure(DuplicateColor(gameId, color))
    }

  def playerColor(gameId: String, tokenHash: String): Try[Option[Color]] =
    players(gameId).map(_.collectFirst { case (color, hash) if hash == tokenHash => color })

  def openGames(): Try[Array[String]] =
    Try(client.scanPaginator(
      ScanRequest.builder().tableName(playersTable).projectionExpression("#gameId, #color")
        .expressionAttributeNames(Map("#gameId" -> "gameId", "#color" -> "color").asJava)
        .build()
    ).items().asScala.toList).map { items =>
      items.flatMap(item =>
        for gameId <- Option(item.get("gameId")); color <- Option(item.get("color"))
        yield (gameId.s(), color.s())
      ).groupMap(_._1)(_._2)
        .collect { case (gameId, colors) if colors == List(Black.toString) => gameId }
        .toArray
    }

  /** Sets the game's archive key and expiry, and its players' expiry, in one transaction. */
  def markCompleted(gameId: String, archiveKey: String, expiresAt: Long): Try[Unit] =
    val completeGame = TransactWriteItem.builder().update(
      Update.builder().tableName(gamesTable).key(gameKey(gameId))
        .updateExpression("SET #archiveKey = :key, #expires = :expires")
        .conditionExpression("attribute_exists(gameId)")
        .expressionAttributeNames(
          Map("#archiveKey" -> "archiveKey", "#expires" -> "expiresAt").asJava
        )
        .expressionAttributeValues(Map(
          ":key" -> str(archiveKey), ":expires" -> num(expiresAt)
        ).asJava)
        .build()
    ).build()
    players(gameId).flatMap { registered =>
      val expirePlayers = registered.keys.toList.map(color =>
        TransactWriteItem.builder().update(
          Update.builder().tableName(playersTable)
            .key(Map("gameId" -> str(gameId), "color" -> str(color.toString)).asJava)
            .updateExpression("SET #expires = :expires")
            .expressionAttributeNames(Map("#expires" -> "expiresAt").asJava)
            .expressionAttributeValues(Map(":expires" -> num(expiresAt)).asJava)
            .build()
        ).build()
      )
      Try(client.transactWriteItems(
        TransactWriteItemsRequest.builder().transactItems((completeGame :: expirePlayers).asJava)
          .build()
      )).map(_ => ()).recoverWith {
        case e: TransactionCanceledException if gameCheckFailed(e) =>
          Failure(NonexistentGame(gameId, List()))
      }
    }

  /** The registered players of a game with the hashes of their tokens. */
  private def players(gameId: String): Try[Map[Color, String]] =
    Try(client.query(
      QueryRequest.builder().tableName(playersTable).consistentRead(true)
        .keyConditionExpression("#gameId = :gameId")
        .expressionAttributeNames(Map("#gameId" -> "gameId").asJava)
        .expressionAttributeValues(Map(":gameId" -> str(gameId)).asJava)
        .build()
    )).flatMap { response =>
      response.items().asScala.toList.foldLeft(Try(Map.empty[Color, String])) { (acc, item) =>
        for
          map <- acc
          color <- attribute(item.asScala, "color").flatMap(c => parseColor(c.s()))
          hash <- attribute(item.asScala, "authTokenHash").map(_.s())
        yield map + (color -> hash)
      }
    }

  /** The first item of the transaction, the game's update, failed its `attribute_exists`. */
  private def gameCheckFailed(e: TransactionCanceledException): Boolean =
    e.cancellationReasons().asScala.headOption.exists(_.code() == "ConditionalCheckFailed")

  private def parseColor(value: String): Try[Color] =
    value.headOption.fold[Try[Color]](Failure(IllegalStateException("empty color")))(Color(_))

  private def attribute(
    item: collection.Map[String, AttributeValue], name: String
  ): Try[AttributeValue] =
    item.get(name).fold[Try[AttributeValue]](
      Failure(IllegalStateException("missing attribute " + name))
    )(Success(_))

  private def gameKey(gameId: String): java.util.Map[String, AttributeValue] =
    Map("gameId" -> str(gameId)).asJava

  private def str(value: String): AttributeValue = AttributeValue.builder().s(value).build()

  private def num(value: Long): AttributeValue = AttributeValue.builder().n(value.toString).build()

object DynamoDBGameStore:
  /** The store for the tables configured in the environment, if DynamoDB is configured. */
  def fromEnv(): Option[DynamoDBGameStore] =
    DynamoDBClient.get().map { case (client, config) =>
      new DynamoDBGameStore(client, config.gamesTable, config.playersTable)
    }

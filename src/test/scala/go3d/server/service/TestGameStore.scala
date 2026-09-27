package go3d.server.service

import go3d.Black
import go3d.Game
import go3d.Move
import go3d.White
import go3d.server.DuplicateColor
import go3d.server.NonexistentGame
import org.junit.jupiter.api.{AfterEach, Assertions, Assumptions, BeforeEach, Test}
import org.scalatest.TryValues.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

private val ArchiveKey = "archives/GAME01.json"

/** The behavior every [[GameStore]] must have; each implementation runs it in a subclass. */
abstract class GameStoreContract:
  protected def store: GameStore

  private val gameId = "GAME01"
  private def emptyGame: Game = Game.start(3).success.value
  private def gameWithMove: Game = emptyGame.makeMove(Move(2, 2, 2, Black)).success.value

  @Test def testCreatedGameIsReadBackAtVersion0(): Unit =
    store.createGame(gameId, gameWithMove).success.value
    val stored = store.getGame(gameId).success.value
    Assertions.assertEquals(Some(gameWithMove), stored.map(_.game))
    Assertions.assertEquals(Some(0L), stored.map(_.version))
    Assertions.assertEquals(Some(Set.empty[go3d.Color]), stored.map(_.players))

  @Test def testMissingGameIsNone(): Unit =
    Assertions.assertEquals(None, store.getGame(gameId).success.value)

  @Test def testCreatingExistingGameFails(): Unit =
    store.createGame(gameId, emptyGame).success.value
    Assertions.assertInstanceOf(
      classOf[GameExists], store.createGame(gameId, emptyGame).failure.exception
    )

  @Test def testUpdateWithCurrentVersionStoresGame(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.updateGame(gameId, 0L, gameWithMove).success.value
    val stored = store.getGame(gameId).success.value
    Assertions.assertEquals(Some(gameWithMove), stored.map(_.game))
    Assertions.assertEquals(Some(1L), stored.map(_.version))

  @Test def testUpdateWithStaleVersionFails(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.updateGame(gameId, 0L, gameWithMove).success.value
    Assertions.assertInstanceOf(
      classOf[ConcurrentModification], store.updateGame(gameId, 0L, emptyGame).failure.exception
    )
    Assertions.assertEquals(Some(gameWithMove), store.getGame(gameId).success.value.map(_.game))

  @Test def testUpdateOfMissingGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame], store.updateGame(gameId, 0L, emptyGame).failure.exception
    )

  @Test def testOnlyOneOfConcurrentUpdatesSucceeds(): Unit =
    store.createGame(gameId, emptyGame).success.value
    val results = Await.result(
      Future.sequence((1 to 8).map(_ => Future(store.updateGame(gameId, 0L, gameWithMove)))),
      60.seconds
    )
    Assertions.assertEquals(1, results.count(_.isSuccess))
    Assertions.assertEquals(7, results.count {
      case Failure(_: ConcurrentModification) => true
      case _ => false
    })
    Assertions.assertEquals(Some(1L), store.getGame(gameId).success.value.map(_.version))

  @Test def testRegisteredPlayersAreFoundByTokenHash(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.registerPlayer(gameId, Black, "hashB").success.value
    store.registerPlayer(gameId, White, "hashW").success.value
    Assertions.assertEquals(Some(Black), store.playerColor(gameId, "hashB").success.value)
    Assertions.assertEquals(Some(White), store.playerColor(gameId, "hashW").success.value)
    Assertions.assertEquals(None, store.playerColor(gameId, "other").success.value)
    Assertions.assertEquals(
      Some(Set(Black, White)), store.getGame(gameId).success.value.map(_.players)
    )

  @Test def testTokenHashOfOtherGameIsNotFound(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.registerPlayer("OTHER1", Black, "hashB").success.value
    Assertions.assertEquals(None, store.playerColor(gameId, "hashB").success.value)

  @Test def testRegisteringColorTwiceFails(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.registerPlayer(gameId, Black, "hash1").success.value
    Assertions.assertInstanceOf(
      classOf[DuplicateColor], store.registerPlayer(gameId, Black, "hash2").failure.exception
    )
    Assertions.assertEquals(Some(Black), store.playerColor(gameId, "hash1").success.value)

  @Test def testOpenGamesHaveOnlyBlackRegistered(): Unit =
    for id <- List("OPEN01", "FULL01", "EMPTY1") do store.createGame(id, emptyGame).success.value
    store.registerPlayer("OPEN01", Black, "h1").success.value
    store.registerPlayer("FULL01", Black, "h2").success.value
    store.registerPlayer("FULL01", White, "h3").success.value
    Assertions.assertEquals(List("OPEN01"), store.openGames().success.value.toList)

  @Test def testMarkCompletedSucceedsForExistingGame(): Unit =
    store.createGame(gameId, emptyGame).success.value
    store.registerPlayer(gameId, Black, "hashB").success.value
    store.markCompleted(gameId, ArchiveKey, 1_000_000L).success.value
    Assertions.assertTrue(store.getGame(gameId).success.value.isDefined)

  @Test def testMarkCompletedOfMissingGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame],
      store.markCompleted(gameId, ArchiveKey, 1_000_000L).failure.exception
    )

class TestInMemoryGameStore extends GameStoreContract:
  protected val store: InMemoryGameStore = InMemoryGameStore()

/**
 * Runs the contract against DynamoDB Local (`amazon/dynamodb-local`) at the endpoint in
 * `DYNAMODB_TEST_ENDPOINT`, which CI sets; skipped when the variable is not set. Every test gets
 * its own pair of tables.
 */
class TestDynamoDBGameStore extends GameStoreContract:
  private val endpoint = sys.env.get("DYNAMODB_TEST_ENDPOINT")
  private val suffix = java.util.UUID.randomUUID().toString
  private val gamesTable = "games-" + suffix
  private val playersTable = "players-" + suffix

  private lazy val client: DynamoDbClient =
    DynamoDbClient.builder()
      .endpointOverride(URI.create(endpoint.getOrElse("http://localhost:8000")))
      .region(Region.EU_CENTRAL_1)
      .credentialsProvider(
        StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
      )
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .build()

  protected lazy val store: DynamoDBGameStore =
    new DynamoDBGameStore(client, gamesTable, playersTable)

  @BeforeEach def createTables(): Unit =
    Assumptions.assumeTrue(endpoint.isDefined, "DYNAMODB_TEST_ENDPOINT is not set")
    createTable(gamesTable, List("gameId"))
    createTable(playersTable, List("gameId", "color"))

  @AfterEach def deleteTables(): Unit =
    if endpoint.isDefined then
      for table <- List(gamesTable, playersTable) do
        Try(client.deleteTable(DeleteTableRequest.builder().tableName(table).build()))
      client.close()

  @Test def testMarkCompletedSetsExpiryOnGameAndPlayers(): Unit =
    store.createGame("GAME01", Game.start(3).success.value).success.value
    store.registerPlayer("GAME01", Black, "hashB").success.value
    store.markCompleted("GAME01", ArchiveKey, 1_000_000L).success.value
    val game = item(gamesTable, Map("gameId" -> "GAME01"))
    Assertions.assertEquals(Some(ArchiveKey), game.get("archiveKey").map(_.s()))
    Assertions.assertEquals(Some("1000000"), game.get("expiresAt").map(_.n()))
    val player = item(playersTable, Map("gameId" -> "GAME01", "color" -> Black.toString))
    Assertions.assertEquals(Some("1000000"), player.get("expiresAt").map(_.n()))

  @Test def testActiveGameHasNoExpiry(): Unit =
    store.createGame("GAME01", Game.start(3).success.value).success.value
    store.registerPlayer("GAME01", Black, "hashB").success.value
    Assertions.assertEquals(None, item(gamesTable, Map("gameId" -> "GAME01")).get("expiresAt"))
    Assertions.assertEquals(
      None,
      item(playersTable, Map("gameId" -> "GAME01", "color" -> Black.toString)).get("expiresAt")
    )

  private def item(table: String, key: Map[String, String]): Map[String, AttributeValue] =
    client.getItem(
      GetItemRequest.builder().tableName(table)
        .key(key.map((k, v) => k -> AttributeValue.builder().s(v).build()).asJava)
        .build()
    ).item().asScala.toMap

  private def createTable(name: String, keys: List[String]): Unit =
    val keyTypes = keys.zip(List(KeyType.HASH, KeyType.RANGE))
    client.createTable(
      CreateTableRequest.builder().tableName(name)
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .attributeDefinitions(keys.map(k =>
          AttributeDefinition.builder().attributeName(k).attributeType(ScalarAttributeType.S)
            .build()
        ).asJava)
        .keySchema(keyTypes.map((k, t) =>
          KeySchemaElement.builder().attributeName(k).keyType(t).build()
        ).asJava)
        .build()
    )
    ()

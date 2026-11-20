package go3d.server

import go3d._
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

private val EmptyLevel = "   \n   \n   "
private val CenterLevel = "   \n @ \n   "

class TestJsonify:

  @Test def testEqualMapsAreEqual(): Unit =
    val map1 = Map(1 -> "a")
    val map2 = Map(1 -> "a")
    Assertions.assertEquals(map1, map2)

  @Test def testUseCirceForColorJson(): Unit =
    val col = Black
    val json = col.asJson.noSpaces
    Assertions.assertEquals(Right(col), decode[Color](json))

  @Test def testUseCirceForPositionJson(): Unit =
    val pos = Position(1, 1, 1)
    val json = pos.asJson.noSpaces
    Assertions.assertEquals(Right(pos), decode[Position](json))

  @Test def testUseCirceForMoveJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = move.asJson.noSpaces
    Assertions.assertEquals(Right(move), decode[Move](json))

  @Test def testUseCirceForListMovesJson(): Unit =
    val moves = List(Move(1, 1, 1, Black), Move(2, 1, 1, White))
    val json = moves.asJson.noSpaces
    Assertions.assertEquals(Right(moves), decode[List[Move]](json))

  @Test def testUseCirceForListMovePassJson(): Unit =
    val moves = List[Move | Pass](Move(1, 1, 1, Black), Pass(White))
    val json = moves.asJson.noSpaces
    decode[List[Move | Pass]](json) match
      case Right(decoded) => Assertions.assertEquals(moves.toString, decoded.toString)
      case Left(e) => Assertions.fail(e.getMessage)

  @Test def testUseCirceForPrimitiveMapJson(): Unit =
    val map = Map(1 -> "a")
    val json = map.asJson.noSpaces
    Assertions.assertEquals(Right(map), decode[Map[Int, String]](json))

  @Test def testUseCirceForMapWithColorValuesJson(): Unit =
    val map = Map(1 -> Black)
    val json = map.asJson.noSpaces
    Assertions.assertEquals(Right(map), decode[Map[Int, Color]](json))

  @Test def testUseCirceForMapWithColorKeysJson(): Unit =
    val map = Map(Black -> "a")
    val json = map.asJson.noSpaces
    Assertions.assertEquals(Right(map), decode[Map[Color, String]](json))

  @Test def testFromStringsWithPipes(): Unit =
    val goban = gobanFromStrings(
      Array("|   |\n|   |\n|   |", "|   |\n| @ |\n|   |", "|   |\n|   |\n|   |")
    ).success.value
    Assertions.assertEquals(Black, goban.at(2, 2, 2))

  @Test def testFromStringsWithMargin(): Unit =
    val goban = gobanFromStrings(Array(
      """|   |
         |   |
         |   |""",
      """|   |
         | @ |
         |   |""",
      """|   |
         |   |
         |   |"""
    )).success.value
    Assertions.assertEquals(Black, goban.at(2, 2, 2), goban.toString)

  @Test def testFromStringsWithoutPipesOrMargin(): Unit =
    val goban = gobanFromStrings(Array(EmptyLevel, CenterLevel, EmptyLevel)).success.value
    Assertions.assertEquals(Black, goban.at(2, 2, 2))

  @Test def testToStringsIsInverseOfFromStrings(): Unit =
    val definition = Array(EmptyLevel, CenterLevel, EmptyLevel)
    val goban = gobanFromStrings(definition).success.value
    Assertions.assertEquals(definition.toList, gobanToStrings(goban).toList)

  @Test def testFromStringsIsInverseOfToStrings(): Unit =
    val goban = gobanFromStrings(Array(
      """|   |
         |   |
         |   |""",
      """|   |
         | @ |
         |   |""",
      """|   |
         |   |
         |   |"""
    )).success.value
    val definition = gobanToStrings(goban)
    Assertions.assertEquals(gobanToStrings(goban).toList, definition.toList)

  @Test def testFromStringsWithTooFewLevels(): Unit =
    Assertions.assertInstanceOf(
      classOf[JsonDecodeError],
      gobanFromStrings(Array(EmptyLevel, CenterLevel)).failure.exception
    )

  @Test def testFromStringsWithTruncatedLastLevel(): Unit =
    Assertions.assertInstanceOf(
      classOf[JsonDecodeError],
      gobanFromStrings(Array(EmptyLevel, CenterLevel, "   \n   \n ")).failure.exception
    )

  @Test def testUseCirceForEmptyGobanJson(): Unit =
    val goban = Goban.start(TestSize).success.value
    val json = goban.asJson.noSpaces
    Assertions.assertEquals(Right(goban), decode[Goban](json))

  @Test def testUseCirceForNonEmptyGobanJson(): Unit =
    val goban = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    val json = goban.asJson.noSpaces
    Assertions.assertEquals(Right(goban), decode[Goban](json))

  @Test def testUseCirceForEmptyGameJson(): Unit =
    val game = Game.start(TestSize).success.value
    val json = game.asJson.noSpaces
    Assertions.assertEquals(Right(game), decode[Game](json), json)

  @Test def testUseCirceForNonEmptyGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val json = game.asJson.noSpaces
    Assertions.assertEquals(Right(game), decode[Game](json), json)

  private def withoutKomi(game: Game): Json = game.asJson.mapObject(_.remove("komi"))

  private def withKomi(game: Game, komi: Json): Json = game.asJson.mapObject(_.add("komi", komi))

  @Test def testGameJsonHasKomiAsNumber(): Unit =
    val game = Game.start(TestSize, 6.5).success.value
    Assertions.assertEquals(
      Some(Json.fromDoubleOrNull(6.5)),
      game.asJson.hcursor.downField("komi").focus
    )
    Assertions.assertTrue(game.asJson.noSpaces.contains("\"komi\":6.5"), game.asJson.noSpaces)
    Assertions.assertTrue(
      Game.start(TestSize).success.value.asJson.noSpaces.contains("\"komi\":0.5")
    )
    Assertions.assertTrue(
      Game.start(TestSize, -0.5).success.value.asJson.noSpaces.contains("\"komi\":-0.5")
    )

  @Test def testUseCirceForGameWithKomiJson(): Unit =
    for komi <- List(0.5, -0.5, 6.5, -6.5, 26.5) do
      val game = playListOfMovesWithKomi(TestSize, komi, CaptureMoves)
      val json = game.asJson.noSpaces
      val decoded = decode[Game](json).toTry.success.value
      Assertions.assertEquals(game, decoded, json)
      Assertions.assertEquals(komi, decoded.komi)

  @Test def testUseCirceForSaveGameWithKomiJson(): Unit =
    val game = Game.start(TestSize, 6.5).success.value
    val players = Map(Black -> Player(Black, "game ID"))
    val saveGame = SaveGame(game, players)
    val json = saveGame.asJson.noSpaces
    Assertions.assertTrue(json.contains("\"komi\":6.5"), json)
    Assertions.assertEquals(6.5, decode[SaveGame](json).toTry.success.value.game.komi)

  @Test def testGameWithoutKomiFieldDecodesWithDefaultKomi(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val decoded = decode[Game](withoutKomi(game).noSpaces).toTry.success.value
    Assertions.assertEquals(Komi.Default, decoded.komi)
    Assertions.assertEquals(game, decoded)

  @Test def testGameWithNullKomiDecodesWithDefaultKomi(): Unit =
    val game = Game.start(TestSize, 6.5).success.value
    val decoded = decode[Game](withKomi(game, Json.Null).noSpaces).toTry.success.value
    Assertions.assertEquals(Komi.Default, decoded.komi)

  @Test def testGameWithInvalidKomiFailsToDecode(): Unit =
    val game = Game.start(TestSize).success.value
    val invalid = List(
      Json.fromInt(0),
      Json.fromInt(7),
      Json.fromDoubleOrNull(6.0),
      Json.fromDoubleOrNull(27.5),
      Json.fromDoubleOrNull(-27.5),
      Json.fromDoubleOrNull(6.500000000000001),
      Json.fromString("abc"),
      Json.True,
      Json.arr(Json.fromDoubleOrNull(6.5)),
      Json.obj()
    )
    for komi <- invalid do
      Assertions.assertTrue(decode[Game](withKomi(game, komi).noSpaces).isLeft, komi.noSpaces)

  @Test def testGameWithOverflowingKomiNumberFailsToDecode(): Unit =
    val json = withoutKomi(Game.start(TestSize).success.value).noSpaces
    val overflowing = json.stripSuffix("}") + ",\"komi\":1e400}"
    Assertions.assertTrue(decode[Game](overflowing).isLeft, overflowing)

  @Test def testStatusAndRegisteredResponsesCarryKomiInGame(): Unit =
    val game = Game.start(TestSize, 6.5).success.value
    val status = StatusResponse(game, List(), false, false, None, NullRequestInfo)
    Assertions.assertEquals(
      6.5,
      decode[StatusResponse](status.asJson.noSpaces).toTry.success.value.game.komi
    )
    val registered = PlayerRegisteredResponse(game, Black, "token", false, NullRequestInfo)
    Assertions.assertEquals(
      6.5,
      decode[PlayerRegisteredResponse](registered.asJson.noSpaces).toTry.success.value.game.komi
    )

  @Test def testSavedGamesOfBeforeKomiDecodeWithDefaultKomi(): Unit =
    val files = Using.resource(Files.list(Path.of("src/test/resources/data"))) { paths =>
      paths.iterator().asScala.filter(_.toString.endsWith(".json")).toList
    }
    Assertions.assertTrue(files.nonEmpty)
    for file <- files do
      val json = Files.readString(file)
      Assertions.assertFalse(json.contains("komi"), file.toString)
      val saveGame = decode[SaveGame](json).toTry.success.value
      Assertions.assertEquals(Komi.Default, saveGame.game.komi, file.toString)

  @Test def testUseCirceForPlayerJson(): Unit =
    val player = Player(Black, "game ID")
    val json = player.asJson.noSpaces
    Assertions.assertEquals(Right(player), decode[Player](json), json)

  @Test def testUseCirceForSaveGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val players = Map(
      (Black -> Player(Black, "game ID")),
      (White -> Player(White, "game ID"))
    )
    val saveGame = SaveGame(game, players)
    val json = saveGame.asJson.noSpaces
    Assertions.assertEquals(Right(saveGame), decode[SaveGame](json), json)

  @Test def testUseCirceForErrorResponseJson(): Unit =
    val response = ErrorResponse("error")
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[ErrorResponse](json), json)

  @Test def testUseCirceForGameCreatedResponseJson(): Unit =
    val response = GameCreatedResponse("game ID", TestSize)
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[GameCreatedResponse](json), json)

  @Test def testUseCirceForRequestInfoJson(): Unit =
    val response = RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[RequestInfo](json), json)

  @Test def testUseCirceForPlayerRegisteredResponseJson(): Unit =
    val response = PlayerRegisteredResponse(
      Game.start(TestSize).success.value,
      Black,
      "token",
      true,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[PlayerRegisteredResponse](json), json)

  @Test def testUseCirceForStatusResponseJson(): Unit =
    val response = StatusResponse(
      Game.start(TestSize).success.value,
      List(Position(1, 1, 1)),
      true,
      false,
      None,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[StatusResponse](json), json)

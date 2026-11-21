package go3d.server

import go3d._
import io.circe.{CursorOp, DecodingFailure, Json}
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
    val status = StatusResponse(game, List(), false, None, NullRequestInfo)
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
      None,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[StatusResponse](json), json)

  private def statusOf(game: Game): StatusResponse =
    StatusResponse(game, List(), false, None, NullRequestInfo)

  private def finishedGame(komi: Double): Game =
    playListOfMovesWithKomi(TestSize, komi, BlackLeadsByOneMoves)

  private def decodeStatus(json: Json): Either[io.circe.Error, StatusResponse] =
    decode[StatusResponse](json.noSpaces)

  @Test def testStatusResponseDerivesOverAndWinnerFromItsGame(): Unit =
    val running = statusOf(Game.start(TestSize).success.value)
    Assertions.assertFalse(running.over)
    Assertions.assertEquals(None, running.winner)
    val finished = statusOf(finishedGame(6.5))
    Assertions.assertTrue(finished.over)
    Assertions.assertEquals(Some(White), finished.winner)

  @Test def testStatusResponseJsonHasWinnerIfAndOnlyIfOver(): Unit =
    val games = List(
      Game.start(TestSize).success.value,
      playListOfMovesWithKomi(TestSize, 6.5, BlackLeadsByOneMoves.take(3)),
      playListOfMovesWithKomi(TestSize, 6.5, BlackLeadsByOneMoves.take(4)),
      finishedGame(0.5),
      finishedGame(6.5),
      finishedGame(-0.5)
    )
    for game <- games do
      val response = statusOf(game)
      val json = response.asJson
      val fields = json.hcursor
      Assertions.assertEquals(Right(game.isOver), fields.downField("over").as[Boolean])
      Assertions.assertEquals(game.isOver, fields.downField("winner").succeeded, json.noSpaces)
      Assertions.assertEquals(Right(response.winner), fields.downField("winner").as[Option[Color]])
      Assertions.assertEquals(Right(response), decodeStatus(json), json.noSpaces)

  @Test def testStatusResponseJsonWritesWinnerInColorFormat(): Unit =
    for (komi, winner) <- List[(Double, Color)]((0.5, Black), (6.5, White)) do
      val json = statusOf(finishedGame(komi)).asJson
      Assertions.assertEquals(Right(winner), json.hcursor.downField("winner").as[Color])
      Assertions.assertEquals(
        Some(winner.asJson),
        json.hcursor.downField("winner").focus,
        json.noSpaces
      )

  @Test def testStatusResponseWithOverAndWithoutWinnerDecodes(): Unit =
    val response = statusOf(finishedGame(6.5))
    val legacy = response.asJson.mapObject(_.remove("winner"))
    Assertions.assertEquals(Right(response), decodeStatus(legacy), legacy.noSpaces)
    Assertions.assertEquals(Right(Some(White)), decodeStatus(legacy).map(_.winner))

  @Test def testStatusResponseWithNullWinnerDecodesLikeWithoutWinner(): Unit =
    val response = statusOf(finishedGame(0.5))
    val json = response.asJson.mapObject(_.add("winner", Json.Null))
    Assertions.assertEquals(Right(response), decodeStatus(json), json.noSpaces)

  @Test def testStatusResponseWithWinnerWhileNotOverFailsToDecode(): Unit =
    val running = statusOf(Game.start(TestSize).success.value).asJson
    for winner <- List[Color](Black, White) do
      val json = running.mapObject(_.add("winner", winner.asJson))
      Assertions.assertTrue(decodeStatus(json).isLeft, json.noSpaces)

  @Test def testStatusResponseWithWrongOverFailsToDecode(): Unit =
    val running = statusOf(Game.start(TestSize).success.value).asJson
    val trueWhileRunning = running.mapObject(_.add("over", Json.True))
    Assertions.assertTrue(decodeStatus(trueWhileRunning).isLeft, trueWhileRunning.noSpaces)
    val finished = statusOf(finishedGame(6.5)).asJson
    val falseWhileFinished = finished.mapObject(_.add("over", Json.False))
    Assertions.assertTrue(decodeStatus(falseWhileFinished).isLeft, falseWhileFinished.noSpaces)
    val falseWithoutWinner = falseWhileFinished.mapObject(_.remove("winner"))
    Assertions.assertTrue(decodeStatus(falseWithoutWinner).isLeft, falseWithoutWinner.noSpaces)

  @Test def testStatusResponseConflictsPointAtTheFieldAtFault(): Unit =
    val running = statusOf(Game.start(TestSize).success.value).asJson
    val conflicts = List(
      (running.mapObject(_.add("over", Json.True)), "over"),
      (running.mapObject(_.add("winner", Black.asJson)), "winner")
    )
    for (json, field) <- conflicts do
      decodeStatus(json) match
        case Left(failure: DecodingFailure) =>
          Assertions.assertEquals(List(CursorOp.DownField(field)), failure.history, json.noSpaces)
        case other => Assertions.fail(s"${json.noSpaces} decoded to $other")

  @Test def testStatusResponseKeepsTheServersWinnerOverTheClientsScoring(): Unit =
    for (komi, serverWinner) <- List[(Double, Color)]((0.5, White), (6.5, Black)) do
      val game = finishedGame(komi)
      Assertions.assertNotEquals(Some(serverWinner), game.winner)
      val json = statusOf(game).asJson.mapObject(_.add("winner", serverWinner.asJson))
      val decoded = decodeStatus(json).toTry.success.value
      Assertions.assertEquals(Some(serverWinner), decoded.winner, json.noSpaces)
      Assertions.assertTrue(decoded.over)
      Assertions.assertEquals(json, decoded.asJson)

  @Test def testFinishedStatusWithEmptyCaptureArrayDecodes(): Unit =
    val json = statusOf(finishedGame(6.5)).asJson.mapObject(_.remove("winner"))
      .hcursor.downField("game").downField("captures").set(Json.obj(("0", Json.arr())))
      .top.getOrElse(Assertions.fail())
    val decoded = decodeStatus(json).toTry.success.value
    Assertions.assertEquals(Some(White), decoded.winner, json.noSpaces)
    Assertions.assertEquals(0, decoded.game.captures(Black) + decoded.game.captures(White))

  @Test def testStatusResponseWithMalformedWinnerFailsToDecode(): Unit =
    val finished = statusOf(finishedGame(6.5)).asJson
    val malformed = List(Json.fromString("O"), Json.True, Json.obj(("color", Json.fromString("x"))))
    for winner <- malformed do
      val json = finished.mapObject(_.add("winner", winner))
      Assertions.assertTrue(decodeStatus(json).isLeft, json.noSpaces)

  @Test def testStatusResponseWithoutOverFailsToDecode(): Unit =
    val json = statusOf(finishedGame(6.5)).asJson.mapObject(_.remove("over"))
    Assertions.assertTrue(decodeStatus(json).isLeft, json.noSpaces)

  private val TwoOpenGames = List(OpenGame("AAAAAA", 0.5), OpenGame("BBBBBB", 6.5))

  private def decodeOpenGames(text: String): Either[io.circe.Error, OpenGamesResponse] =
    decode[OpenGamesResponse](text)

  @Test def testOpenGamesResponseDerivesIdsFromItsGames(): Unit =
    val response = OpenGamesResponse(TwoOpenGames)
    Assertions.assertEquals(List("AAAAAA", "BBBBBB"), response.ids)
    Assertions.assertTrue(OpenGamesResponse(List()).ids.isEmpty)

  @Test def testOpenGamesResponsesAreEqualIfTheirGamesAre(): Unit =
    val response = OpenGamesResponse(TwoOpenGames)
    val copy = OpenGamesResponse(List(OpenGame("AAAAAA", 0.5), OpenGame("BBBBBB", 6.5)))
    Assertions.assertEquals(response, copy)
    Assertions.assertEquals(response.hashCode, copy.hashCode)
    Assertions.assertNotEquals(response, OpenGamesResponse(TwoOpenGames.reverse))
    Assertions.assertNotEquals(response, OpenGamesResponse(TwoOpenGames.init))
    Assertions.assertNotEquals(
      response,
      OpenGamesResponse(List(OpenGame("AAAAAA", 0.5), OpenGame("BBBBBB", 7.5)))
    )

  @Test def testOpenGamesResponseShowsItsGames(): Unit =
    Assertions.assertEquals(
      "OpenGamesResponse(List(OpenGame(AAAAAA,0.5), OpenGame(BBBBBB,6.5)))",
      OpenGamesResponse(TwoOpenGames).toString
    )

  @Test def testUseCirceForOpenGamesResponseJson(): Unit =
    for games <- List(List(), TwoOpenGames, List(OpenGame("CCCCCC", -0.5))) do
      val response = OpenGamesResponse(games)
      val json = response.asJson.noSpaces
      Assertions.assertEquals(Right(response), decodeOpenGames(json), json)

  @Test def testOpenGamesResponseJsonHasIdsAndGamesInTheSameOrder(): Unit =
    val expected = parse(
      """{"ids": ["AAAAAA", "BBBBBB"],
        |"games": [{"id": "AAAAAA", "komi": 0.5}, {"id": "BBBBBB", "komi": 6.5}]}""".stripMargin
    )
    Assertions.assertEquals(expected, Right(OpenGamesResponse(TwoOpenGames).asJson))

  @Test def testEmptyOpenGamesResponseJsonHasEmptyArrays(): Unit =
    val expected = parse("""{"ids": [], "games": []}""")
    Assertions.assertEquals(expected, Right(OpenGamesResponse(List()).asJson))

  @Test def testGenericGoResponseEncoderWritesOpenGames(): Unit =
    val response: GoResponse = OpenGamesResponse(TwoOpenGames)
    Assertions.assertEquals(OpenGamesResponse(TwoOpenGames).asJson, response.asJson)

  @Test def testOpenGamesListingWithoutGamesDecodesWithDefaultKomi(): Unit =
    val expected = OpenGamesResponse(List(OpenGame("AAAAAA", 0.5), OpenGame("BBBBBB", 0.5)))
    Assertions.assertEquals(
      Right(expected),
      decodeOpenGames("""{"ids": ["AAAAAA", "BBBBBB"]}""")
    )
    Assertions.assertEquals(
      Right(expected),
      decodeOpenGames("""{"ids": ["AAAAAA", "BBBBBB"], "games": null}""")
    )
    Assertions.assertEquals(
      Right(OpenGamesResponse(List())),
      decodeOpenGames("""{"ids": []}""")
    )

  @Test def testOpenGamesListingWithOtherIdsThanItsGamesFailsToDecode(): Unit =
    val game = """{"id": "AAAAAA", "komi": 0.5}"""
    val other = """{"id": "BBBBBB", "komi": 0.5}"""
    val contradicting = List(
      s"""{"ids": ["AAAAAA"], "games": [$other]}""",
      s"""{"ids": ["AAAAAA", "BBBBBB"], "games": [$other, $game]}""",
      s"""{"ids": ["AAAAAA", "BBBBBB"], "games": [$game]}""",
      s"""{"ids": ["AAAAAA"], "games": [$game, $other]}""",
      s"""{"ids": [], "games": [$game]}""",
      s"""{"ids": ["AAAAAA"], "games": []}""",
      s"""{"ids": ["AAAAAA", "AAAAAA"], "games": [$game]}"""
    )
    for text <- contradicting do
      decodeOpenGames(text) match
        case Left(failure) => Assertions.assertInstanceOf(classOf[DecodingFailure], failure, text)
        case Right(response) => Assertions.fail(s"$text decoded to $response")

  @Test def testOpenGamesListingWithoutIdsOrWithMalformedGamesFailsToDecode(): Unit =
    val malformed = List(
      """{"games": [{"id": "AAAAAA", "komi": 0.5}]}""",
      """{"ids": ["AAAAAA"], "games": [{"id": "AAAAAA"}]}""",
      """{"ids": ["AAAAAA"], "games": [{"komi": 0.5}]}""",
      """{"ids": ["AAAAAA"], "games": [{"id": "AAAAAA", "komi": true}]}""",
      """{"ids": ["AAAAAA"], "games": {"id": "AAAAAA", "komi": 0.5}}""",
      """{"ids": "AAAAAA"}"""
    )
    for text <- malformed do Assertions.assertTrue(decodeOpenGames(text).isLeft, text)

  /** A listing has no board size, so a komi is valid if it is valid on the largest board. */
  @Test def testOpenGamesListingWithInvalidKomiFailsToDecode(): Unit =
    val largest = MaxBoardSize * MaxBoardSize * MaxBoardSize
    def listing(komi: String): String = s"""{"ids": ["A"], "games": [{"id": "A", "komi": $komi}]}"""
    for komi <- List("0", "7", "0.3", "6.50001", s"$largest.5", s"-$largest.5") do
      decodeOpenGames(listing(komi)) match
        case Left(failure: DecodingFailure) =>
          Assertions.assertEquals(
            List(CursorOp.DownField("komi"), CursorOp.DownArray, CursorOp.DownField("games")),
            failure.history,
            komi
          )
        case other => Assertions.fail(s"komi $komi decoded to $other")
    for komi <- List("-0.5", "6.5", s"${largest - 1}.5", s"-${largest - 1}.5") do
      Assertions.assertTrue(decodeOpenGames(listing(komi)).isRight, komi)

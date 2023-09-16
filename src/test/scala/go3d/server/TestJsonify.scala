package go3d.server

import go3d._
import io.circe.parser._
import io.circe.syntax._
import org.junit.jupiter.api.{Assertions, Test}

class TestJsonify:

  @Test def testEqualMapsAreEqual(): Unit =
    val map1 = Map(1->"a")
    val map2 = Map(1->"a")
    Assertions.assertEquals(map1, map2)

  @Test def testUseCirceForColorJson(): Unit =
    val col = Black
    val json = col.asJson.noSpaces
    val decoded = decode[Color](json).getOrElse(null)
    Assertions.assertEquals(col, decoded)

  @Test def testUseCirceForPositionJson(): Unit =
    val pos = Position(1, 1, 1)
    val json = pos.asJson.noSpaces
    val decoded = decode[Position](json).getOrElse(null)
    Assertions.assertEquals(pos, decoded)

  @Test def testUseCirceForMoveJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = move.asJson.noSpaces
    val decoded = decode[Move](json).getOrElse(null)
    Assertions.assertEquals(move, decoded)

  @Test def testUseCirceForListMovesJson(): Unit =
    val moves = List(Move(1, 1, 1, Black), Move(2, 1, 1, White))
    val json = moves.asJson.noSpaces
    val decoded = decode[List[Move]](json).getOrElse(null)
    Assertions.assertEquals(moves, decoded)

  @Test def testUseCirceForListMovePassJson(): Unit =
    val moves = List[Move | Pass](Move(1, 1, 1, Black), Pass(White))
    val json = moves.asJson.noSpaces
    val decoded = decode[List[Move | Pass]](json).getOrElse(null)
    Assertions.assertEquals(moves.toString, decoded.toString)

  @Test def testUseCirceForPrimitiveMapJson(): Unit =
    val map = Map(1 -> "a")
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Int, String]](json).getOrElse(null)
    Assertions.assertEquals(map, decoded)

  @Test def testUseCirceForMapWithColorValuesJson(): Unit =
    val map = Map(1 -> Black)
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Int, Color]](json).getOrElse(null)
    Assertions.assertEquals(map, decoded)

  @Test def testUseCirceForMapWithColorKeysJson(): Unit =
    val map = Map(Black -> "a")
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Color, String]](json).getOrElse(null)
    Assertions.assertEquals(map, decoded)

  @Test def testFromStringsWithPipes(): Unit =
    val goban = gobanFromStrings(
      Array("|   |\n|   |\n|   |", "|   |\n| @ |\n|   |", "|   |\n|   |\n|   |")
    )
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
    ))
    Assertions.assertEquals(Black, goban.at(2, 2, 2), goban.toString)

  @Test def testFromStringsWithoutPipesOrMargin(): Unit =
    val goban = gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n   "))
    Assertions.assertEquals(Black, goban.at(2, 2, 2))

  @Test def testToStringsIsInverseOfFromStrings(): Unit =
    val definition = Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n   ")
    val goban = gobanFromStrings(definition)
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
    ))
    val definition = gobanToStrings(goban)
    Assertions.assertEquals(gobanToStrings(goban).toList, definition.toList)

  @Test def testFromStringsWithTooFewLevels(): Unit =
    assertThrows[JsonDecodeError](
      {gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   "))}
    )

  @Test def testFromStringsWithTruncatedLastLevel(): Unit =
    assertThrows[JsonDecodeError](
      {gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n "))}
    )

  @Test def testUseCirceForEmptyGobanJson(): Unit =
    val goban = newGoban(TestSize)
    val json = goban.asJson.noSpaces
    val decoded = decode[Goban](json).getOrElse(null)
    Assertions.assertEquals(goban, decoded)

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
    val decoded = decode[Goban](json).getOrElse(null)
    Assertions.assertEquals(goban, decoded)

  @Test def testUseCirceForEmptyGameJson(): Unit =
    val game = newGame(TestSize)
    val json = game.asJson.noSpaces
    val decoded = decode[Game](json).getOrElse(null)
    Assertions.assertEquals(game, decoded, json)

  @Test def testUseCirceForNonEmptyGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val json = game.asJson.noSpaces
    val decoded = decode[Game](json).getOrElse(null)
    Assertions.assertEquals(game, decoded, json)

  @Test def testUseCirceForPlayerJson(): Unit =
    val player = Player(Black, "game ID", "token")
    val json = player.asJson.noSpaces
    val decoded = decode[Player](json).getOrElse(null)
    Assertions.assertEquals(player, decoded, json)

  @Test def testUseCirceForSaveGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val players = Map(
      (Black -> Player(Black, "game ID", "token")),
      (White -> Player(White, "game ID", "other token")),
    )
    val saveGame = SaveGame(game, players)
    val json = saveGame.asJson.noSpaces
    val decoded = decode[SaveGame](json).getOrElse(null)
    Assertions.assertEquals(saveGame, decoded, json)

  @Test def testUseCirceForErrorResponseJson(): Unit =
    val response = ErrorResponse("error")
    val json = response.asJson.noSpaces
    val decoded = decode[ErrorResponse](json).getOrElse(null)
    Assertions.assertEquals(response, decoded, json)

  @Test def testUseCirceForGameCreatedResponseJson(): Unit =
    val response = GameCreatedResponse("game ID", TestSize)
    val json = response.asJson.noSpaces
    val decoded = decode[GameCreatedResponse](json).getOrElse(null)
    Assertions.assertEquals(response, decoded, json)

  @Test def testUseCirceForRequestInfoJson(): Unit =
    val response = RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    val json = response.asJson.noSpaces
    val decoded = decode[RequestInfo](json).getOrElse(null)
    Assertions.assertEquals(response, decoded, json)

  @Test def testUseCirceForPlayerRegisteredResponseJson(): Unit =
    val response = PlayerRegisteredResponse(
      newGame(TestSize), Black, "token", true,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    val decoded = decode[PlayerRegisteredResponse](json).getOrElse(null)
    Assertions.assertEquals(response, decoded, json)

  @Test def testUseCirceForStatusResponseJson(): Unit =
    val response = StatusResponse(
      newGame(TestSize), List(Position(1, 1, 1)), true, false,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    val decoded = decode[StatusResponse](json).getOrElse(null)
    Assertions.assertEquals(response, decoded, json)

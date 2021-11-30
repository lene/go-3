package go3d.testing

import org.junit.{Assert, Test}
import go3d.server._
import go3d.{
  Black, Color, Empty, Game, Goban, Move, Pass, Position, Sentinel, White, newGame, newGoban
}
import io.circe.syntax._
import io.circe.parser._

class TestJsonify:

  @Test def testEqualMapsAreEqual(): Unit =
    val map1 = Map(1->"a")
    val map2 = Map(1->"a")
    Assert.assertEquals(map1, map2)

  @Test def testUseCirceForColorJson(): Unit =
    val col = Black
    val json = col.asJson.noSpaces
    val decoded = decode[Color](json).getOrElse(null)
    Assert.assertEquals(col, decoded)

  @Test def testUseCirceForPositionJson(): Unit =
    val pos = Position(1, 1, 1)
    val json = pos.asJson.noSpaces
    val decoded = decode[Position](json).getOrElse(null)
    Assert.assertEquals(pos, decoded)

  @Test def testUseCirceForMoveJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = move.asJson.noSpaces
    val decoded = decode[Move](json).getOrElse(null)
    Assert.assertEquals(move, decoded)

  @Test def testUseCirceForListMovesJson(): Unit =
    val moves = List(Move(1, 1, 1, Black), Move(2, 1, 1, White))
    val json = moves.asJson.noSpaces
    val decoded = decode[List[Move]](json).getOrElse(null)
    Assert.assertEquals(moves, decoded)

  @Test def testUseCirceForListMovePassJson(): Unit =
    val moves = List[Move | Pass](Move(1, 1, 1, Black), Pass(White))
    val json = moves.asJson.noSpaces
    val decoded = decode[List[Move | Pass]](json).getOrElse(null)
    Assert.assertEquals(moves.toString, decoded.toString)

  @Test def testUseCirceForPrimitiveMapJson(): Unit =
    val map = Map(1 -> "a")
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Int, String]](json).getOrElse(null)
    Assert.assertEquals(map, decoded)

  @Test def testUseCirceForMapWithColorValuesJson(): Unit =
    val map = Map(1 -> Black)
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Int, Color]](json).getOrElse(null)
    Assert.assertEquals(map, decoded)

  @Test def testUseCirceForMapWithColorKeysJson(): Unit =
    val map = Map(Black -> "a")
    val json = map.asJson.noSpaces
    val decoded = decode[Map[Color, String]](json).getOrElse(null)
    Assert.assertEquals(map, decoded)

  @Test def testFromStringsWithPipes(): Unit =
    val goban = gobanFromStrings(
      Array("|   |\n|   |\n|   |", "|   |\n| @ |\n|   |", "|   |\n|   |\n|   |")
    )
    Assert.assertEquals(Black, goban.at(2, 2, 2))

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
    Assert.assertEquals(goban.toString, Black, goban.at(2, 2, 2))

  @Test def testFromStringsWithoutPipesOrMargin(): Unit =
    val goban = gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n   "))
    Assert.assertEquals(Black, goban.at(2, 2, 2))

  @Test def testToStringsIsInverseOfFromStrings(): Unit =
    val definition = Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n   ")
    val goban = gobanFromStrings(definition)
    Assert.assertEquals(definition.toList, gobanToStrings(goban).toList)

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
    Assert.assertEquals(gobanToStrings(goban).toList, definition.toList)

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
    Assert.assertEquals(goban, decoded)

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
    Assert.assertEquals(goban, decoded)

  @Test def testUseCirceForEmptyGameJson(): Unit =
    val game = newGame(TestSize)
    val json = game.asJson.noSpaces
    val decoded = decode[Game](json).getOrElse(null)
    Assert.assertEquals(json, game, decoded)

  @Test def testUseCirceForNonEmptyGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val json = game.asJson.noSpaces
    val decoded = decode[Game](json).getOrElse(null)
    Assert.assertEquals(json, game, decoded)

  @Test def testUseCirceForPlayerJson(): Unit =
    val player = Player(Black, "game ID", "token")
    val json = player.asJson.noSpaces
    val decoded = decode[Player](json).getOrElse(null)
    Assert.assertEquals(json, player, decoded)

  @Test def testUseCirceForSaveGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val players = Map(
      (Black -> Player(Black, "game ID", "token")),
      (White -> Player(White, "game ID", "other token")),
    )
    val saveGame = SaveGame(game, players)
    val json = saveGame.asJson.noSpaces
    val decoded = decode[SaveGame](json).getOrElse(null)
    Assert.assertEquals(json, saveGame, decoded)

  @Test def testUseCirceForErrorResponseJson(): Unit =
    val response = ErrorResponse("error")
    val json = response.asJson.noSpaces
    val decoded = decode[ErrorResponse](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  @Test def testUseCirceForGameCreatedResponseJson(): Unit =
    val response = GameCreatedResponse("game ID", TestSize)
    val json = response.asJson.noSpaces
    val decoded = decode[GameCreatedResponse](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  @Test def testUseCirceForRequestInfoJson(): Unit =
    val response = RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    val json = response.asJson.noSpaces
    val decoded = decode[RequestInfo](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  @Test def testUseCirceForPlayerRegisteredResponseJson(): Unit =
    val response = PlayerRegisteredResponse(
      newGame(TestSize), Black, "token", true,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    val decoded = decode[PlayerRegisteredResponse](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  @Test def testUseCirceForStatusResponseJson(): Unit =
    val response = StatusResponse(
      newGame(TestSize), List(Position(1, 1, 1)), true,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    val decoded = decode[StatusResponse](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

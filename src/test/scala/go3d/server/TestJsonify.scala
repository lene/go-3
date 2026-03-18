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
    Assertions.assertThrows(
      classOf[JsonDecodeError], () => gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   "))
    )

  @Test def testFromStringsWithTruncatedLastLevel(): Unit =
    Assertions.assertThrows(
      classOf[JsonDecodeError],
      () => gobanFromStrings(Array("   \n   \n   ", "   \n @ \n   ", "   \n   \n "))
    )

  @Test def testUseCirceForEmptyGobanJson(): Unit =
    val goban = Goban.start(TestSize)
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
    val game = Game.start(TestSize)
    val json = game.asJson.noSpaces
    Assertions.assertEquals(Right(game), decode[Game](json), json)

  @Test def testUseCirceForNonEmptyGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val json = game.asJson.noSpaces
    Assertions.assertEquals(Right(game), decode[Game](json), json)

  @Test def testUseCirceForPlayerJson(): Unit =
    val player = Player(Black, "game ID")
    val json = player.asJson.noSpaces
    Assertions.assertEquals(Right(player), decode[Player](json), json)

  @Test def testUseCirceForSaveGameJson(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val players = Map(
      (Black -> Player(Black, "game ID")),
      (White -> Player(White, "game ID")),
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
      Game.start(TestSize), Black, "token", true,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[PlayerRegisteredResponse](json), json)

  @Test def testUseCirceForStatusResponseJson(): Unit =
    val response = StatusResponse(
      Game.start(TestSize), List(Position(1, 1, 1)), true, false, None,
      RequestInfo(Map("header name" -> "header value"), "query", "path", false)
    )
    val json = response.asJson.noSpaces
    Assertions.assertEquals(Right(response), decode[StatusResponse](json), json)

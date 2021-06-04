package go3d.testing

import com.google.gson.Gson
import org.junit.{Assert, Ignore, Test}
import go3d.server._
import go3d.{
  Black, Color, Empty, Game, Goban, Move, Pass, Position, Sentinel, White, newGame, newGoban
}
import scala.collection.mutable
import collection.JavaConverters.{
  mapAsJavaMapConverter, mapAsScalaMapConverter, seqAsJavaListConverter, asScalaBufferConverter
}
import io.circe.syntax._
import io.circe.parser._

class TestJsonify:

  @Test def testColorJson(): Unit =
    for color <- List(Black, White, Empty, Sentinel) do
      val json = Jsonify.toJson(color)
      val decoded = Jsonify.fromJson[Color](json)
      Assert.assertEquals(color, decoded)

  @Test def testEqualMapsAreEqual(): Unit =
    val map1 = Map(1->"a")
    val map2 = Map(1->"a")
    Assert.assertEquals(map1, map2)

  @Test def testPrimitiveJavaMapToJson(): Unit =
    val map = Map(1 -> "a").asJava
    val json = Jsonify.toJson(map)
    val decoded = Jsonify.fromJson[java.util.Map[Int, String]](json)
    Assert.assertEquals(
      map.asScala.toMap[Int, String].toString, decoded.asScala.toMap[Int, String].toString
    )
    if false then
      Assert.assertEquals(map.asScala.toMap[Int, String], decoded.asScala.toMap[Int, String])

  @Test def testJavaMapWithColorValuesToJson(): Unit =
    val map = Map(1 -> Black).asJava
    val json = Jsonify.toJson(map)
    val decoded = Jsonify.fromJson[java.util.Map[Int, Color]](json)
    Assert.assertEquals(
      map.asScala.toMap[Int, Color].toString, decoded.asScala.toMap[Int, Color].toString
    )
    if false then
      Assert.assertEquals(map.asScala.toMap[Int, Color], decoded.asScala.toMap[Int, Color])

  @Test def testJavaMapWithColorKeysToJson(): Unit =
    val map = Map(Black -> "a").asJava
    val json = Jsonify.toJson(map)
    val decoded = Jsonify.fromJson[java.util.Map[Color, String]](json)
    Assert.assertEquals(
      map.asScala.toMap[Color, String].toString, decoded.asScala.toMap[Color, String].toString
    )
    if false then
      Assert.assertEquals(map.asScala.toMap[Color, String], decoded.asScala.toMap[Color, String])

  @Test def testPositionJson(): Unit =
    val pos = Position(1, 1, 1)
    val json = Jsonify.toJson(pos)
    val decoded = Jsonify.fromJson[Position](json)
    Assert.assertEquals(pos, decoded)

  @Test def testMoveJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = Jsonify.toJson(move)
    val decoded = Jsonify.fromJson[Move](json)
    Assert.assertEquals(move, decoded)

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

  @Test def testUseCirceForRequestDebugInfoJson(): Unit =
    val response = RequestDebugInfo(Map("header name" -> "header value"), "query", "path")
    val json = response.asJson.noSpaces
    val decoded = decode[RequestDebugInfo](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  @Test def testUseCirceForPlayerRegisteredResponseJson(): Unit =
    val response = PlayerRegisteredResponse(
      newGame(TestSize), Black, "token",
      RequestDebugInfo(Map("header name" -> "header value"), "query", "path")
    )
    val json = response.asJson.noSpaces
    val decoded = decode[PlayerRegisteredResponse](json).getOrElse(null)
    Assert.assertEquals(json, response, decoded)

  // TODO: remove Jsonify and replace with circe
  // TODO: remove remaining junk

  @Ignore
  @Test def testListMovesJson(): Unit =
    val moves = List(Move(1, 1, 1, Black), Move(2, 1, 1, White))
    val json = Jsonify.toJson(moves.asJava)
    val decoded = Jsonify.fromJson[java.util.List[Move]](json)
    Assert.assertEquals(moves, decoded.asScala.toList)

  @Ignore
  @Test def testGameToJson(): Unit =
    val game = newGame(TestSize)
    val json = Jsonify.toJson(game)
    Assert.assertTrue(json, json.contains(""""size":"""+TestSize.toString))
    Assert.assertTrue(json, json.contains(""""goban":"""))
    Assert.assertTrue(json, json.contains(""""moves":[]"""))
    Assert.assertTrue(json, json.contains(""""captures":{}"""))
    val decoded = Jsonify.fromJson[Game](json)
    Assert.assertEquals(game.size, decoded.size)
    Assert.assertEquals(game, decoded)

  @Test def testSaveGameToJson(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val saveGame = SaveGame(Games(gameId), Players(gameId))
    val json = Jsonify.toJson(saveGame)
    Assert.assertTrue(json, json.contains(""""size":"""+TestSize.toString))
    Assert.assertTrue(json, json.contains(""""goban":"""))
    Assert.assertTrue(json, json.contains(""""moves":[]"""))
    Assert.assertTrue(json, json.contains(""""captures":{}"""))

  @Ignore
  @Test def testSaveAndRestoreMapOfColorToString(): Unit =
    val mapToTest = Map[Color, String](Black -> "Black")
    val json = Jsonify.toJson(mapToTest)
    val restoredMap = Jsonify.fromJson[Map[Color, Player]](json)
    Assert.assertTrue("restored: "+restoredMap.toString, restoredMap.contains(Black))

  @Ignore
  @Test def testSaveAndRestoreMapOfPlayers(): Unit =
    val mapToTest = Map[Color, Player](Black -> Player(Black, "mockGameId", "mockToken"))
    val json = Jsonify.toJson(mapToTest)
    val restoredMap = Jsonify.fromJson[Map[Color, Player]](json)
    Assert.assertTrue("restored: "+restoredMap.toString, restoredMap.contains(Black))

  @Ignore
  @Test def testSaveGameFromJson(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val saveGame = SaveGame(Games(gameId), Players(gameId))
    val json = Jsonify.toJson(saveGame)
    val restoredGame = Jsonify.fromJson[SaveGame](json)
    Assert.assertEquals(TestSize, restoredGame.game.size)
    Assert.assertTrue(restoredGame.players.contains(Black))
    Assert.assertFalse(restoredGame.players.contains(White))

  @Test def testJsonToEmptyGame(): Unit =
    val json =
      """{
        |"size":3,
        |"goban":{
        |  "size":3,
        |  "stones":[
        |    [["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"]]
        |  ]},
        |  "moves":[],
        |  "captures":{}
        |}""".stripMargin
    val game = Jsonify.fromJson[Game](json)
    Assert.assertEquals(3, game.size)
    for pos <- game.goban.allPositions do
      Assert.assertEquals(Empty, game.at(pos))

  @Test def testJsonToGame(): Unit =
    val json =
      """{
        |"size":3,
        |"goban":{
        |  "size":3,
        |  "stones":[
        |    [["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·","@"," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·"," "," "," ","·"],
        |     ["·","·","·","·","·"]],
        |    [["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"],
        |     ["·","·","·","·","·"]]
        |  ]},
        |  "moves":[],
        |  "captures":{}
        |}""".stripMargin
    val game = Jsonify.fromJson[Game](json)
    Assert.assertEquals(game.size*game.size*game.size-1, game.goban.emptyPositions.length)
    Assert.assertFalse(game.goban.emptyPositions.contains(Position(1, 1, 1)))
    for pos <- game.goban.emptyPositions do
      Assert.assertEquals(Empty, game.at(pos))
    Assert.assertEquals(Black, game.at(1, 1, 1))

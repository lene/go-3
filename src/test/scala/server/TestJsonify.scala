package go3d.testing

import com.google.gson.Gson
import org.junit.{Assert, Ignore, Test}
import go3d.server._
import go3d.{Black, Color, Empty, Game, Move, Position, Sentinel, White, newGame}

class TestJsonify:

  @Test def testColorToJson(): Unit =
    for color <- List(Black, White, Empty, Sentinel) do
      val json = Jsonify.toJson(color)
      Assert.assertEquals(s""""${color.toString}"""", json)

  @Test def testPositionToJson(): Unit =
    val pos = Position(1, 1, 1)
    val json = Jsonify.toJson(pos)
    Assert.assertEquals("""{"x":1,"y":1,"z":1}""",json)

  @Test def testMoveToJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = Jsonify.toJson(move)
    Assert.assertEquals("""{"position":{"x":1,"y":1,"z":1},"color":"@"}""", json)

  @Test def testGameToJson(): Unit =
    val game = newGame(TestSize)
    val json = Jsonify.toJson(game)
    Assert.assertTrue(json, json.contains(""""size":"""+TestSize.toString))
    Assert.assertTrue(json, json.contains(""""goban":"""))
    Assert.assertTrue(json, json.contains(""""moves":[]"""))
    Assert.assertTrue(json, json.contains(""""captures":{}"""))

  @Test def testSaveGameToJson(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val saveGame = SaveGame(Games(gameId), Players(gameId))
    val json = Jsonify.toJson(saveGame)
    Assert.assertTrue(json, json.contains(""""size":"""+TestSize.toString))
    Assert.assertTrue(json, json.contains(""""goban":"""))
    Assert.assertTrue(json, json.contains(""""moves":[]"""))
    Assert.assertTrue(json, json.contains(""""captures":{}"""))

  @Test def testJsonToColor(): Unit =
    for color <- List(Black, White, Empty, Sentinel) do
      val json = Jsonify.fromJson[Color](s""""${color.toString}"""")
      Assert.assertEquals(color, json)

  @Test def testJsonToPosition(): Unit =
    val json = """{"x":1,"y":1,"z":1}"""
    val pos = Jsonify.fromJson[Position](json)
    Assert.assertEquals(Position(1, 1, 1), pos)

  @Test def testJsonToMove(): Unit =
    import com.google.gson._
    val json = """{"position":{"x":1,"y":1,"z":1},"color":"@"}"""
    val move = Jsonify.fromJson[Move](json)
    Assert.assertEquals(Move(1, 1, 1, Black), move)

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

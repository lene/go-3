package go3d.testing

import org.junit.{Assert, Ignore, Test}
import go3d.server._
import go3d.{Black, White, Empty, newGame, Position, Move}

class TestJsonify:

  @Test def testColorToJson(): Unit =
    for color <- List(Black, White, Empty) do
      val json = Jsonify.toJson(color)
      Assert.assertEquals(s""""${color.toString}"""", json)

  @Test def testGameToJson(): Unit =
    val game = newGame(TestSize)
    val json = Jsonify.toJson(game)
    Assert.assertTrue(json, json.contains(""""size":"""+TestSize.toString))
    Assert.assertTrue(json, json.contains(""""goban":"""))
    Assert.assertTrue(json, json.contains(""""moves":[]"""))
    Assert.assertTrue(json, json.contains(""""captures":{}"""))

  @Test def testPositionToJson(): Unit = {
    val pos = Position(1, 1, 1)
    val json = Jsonify.toJson(pos)
    Assert.assertEquals("""{"x":1,"y":1,"z":1}""",json)
  }

  @Test def testMoveToJson(): Unit =
    val move = Move(1, 1, 1, Black)
    val json = Jsonify.toJson(move)
    Assert.assertEquals("""{"position":{"x":1,"y":1,"z":1},"color":"@"}""", json)

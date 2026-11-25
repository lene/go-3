package go3d.client

import go3d.{Game, Move}
import go3d.engine.fixtureStore
import go3d.server.{RequestInfo, StatusResponse}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestBotClientWithEngine:

  @Test def testModelStrategyPlaysWithTheEngineOnTheClasspath(): Unit =
    // a game joined with an empty id and token needs no server
    val args = Seq(
      "--server",
      "localhost",
      "--port",
      "6030",
      "--game-id",
      "",
      "--token",
      "",
      "--strategy",
      "model:0",
      "--model-store",
      fixtureStore.toString,
      "--simulations",
      "4"
    )
    BotClient.parseArgs(args.toArray).success.value
    val game = Game.start(5).success.value
    val choice = BotClient.moveChoice(game).success.value
    val ready = StatusResponse(game, List(), true, None, RequestInfo(Map(), "", "", false))
    choice(ready).success.value.foreach(position =>
      Assertions.assertTrue(game.makeMove(Move(position, game.moveColor)).isSuccess)
    )

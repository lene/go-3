package go3d.server.http4s

import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.OpenGame
import go3d.server.OpenGamesResponse
import go3d.server.Players

import scala.util.{Success, Try}

/** Usage example for this endpoint:
  * To get an essentially random game ID waiting for a white player to connect:
  * $ curl -s http://$HOST/openGames | jq -r .ids[0]
  * To see the komi of each of those games, `games` has an `{id, komi}` object per id:
  * $ curl -s http://$HOST/openGames | jq -r '.games[] | "\(.id) \(.komi)"'
  * To join a game without knowing the game ID waiting for a player:
  * $ bot-client --server $HOST \
  *   --game-id $(curl -s http://localhost:6030/openGames | jq -r .ids[0]) \
  *   --color w --strategy random
  */
class ListOpenGames extends BaseHandler:
  def handle: Try[GoResponse] =
    Thread.sleep(100) // very basic DoS protection
    Success(OpenGamesResponse(openGames))

  /** The open games with their komi; a game that was archived or deleted since the players were
    * listed is left out.
    */
  private def openGames: List[OpenGame] =
    Players.openGames().toList.flatMap(id =>
      Games.activeGame(id).map(game => OpenGame(id, game.komi))
    )

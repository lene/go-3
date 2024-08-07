package go3d.server.http4s

import go3d.server.{OpenGamesResponse, GoResponse, Players}

/**
 *Usage example for this endpoint:
  *To get an essentially random game ID waiting for a white player to connect:
  *$ curl -s http://$HOST/openGames | jq -r .ids[0]
  *To join a game without knowing the game ID waiting for a player:
  *$ bot-client --server $HOST --game-id $(curl -s http://localhost:6030/openGames | jq -r .ids[0]) \
      *--color w --strategy random
**/
class ListOpenGames extends BaseHandler:
  def handle: GoResponse =
    Thread.sleep(100) // very basic DoS protection
    OpenGamesResponse(Players.openGames())

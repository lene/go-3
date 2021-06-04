package go3d.server

var Games: Map[String, go3d.Game] = Map()

def registerGame(boardSize: Int): String =
  val gameId = IdGenerator.getId
  val game = go3d.newGame(boardSize)
  Games = Games + (gameId -> game)
  return gameId

def restoreGame(saveGame: SaveGame): Unit =
  val gameId = saveGame.players.last._2.gameId
  Players(gameId) = saveGame.players
  Games = Games + (gameId -> saveGame.game)

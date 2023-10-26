package go3d.server

import go3d.{Color, Move, Black, White}

case class GameData(id: String, token: Map[Color, String]):
  def this(gameId: String, blackToken: String, whiteToken: String) =
    this(gameId, Map(Black -> blackToken, White -> whiteToken))

  def status(color: Color): StatusResponse =
    getSR(
      s"${GameData.ServerURL}/status/${id}", Map("Authentication" -> s"Bearer ${token(color)}")
    )
  def status(): StatusResponse = getSR(s"${GameData.ServerURL}/status/${id}", Map())

  def set(color: Color, x: Int, y: Int, z: Int): StatusResponse =
    getSR(
      s"${GameData.ServerURL}/set/${id}/${x}/${y}/${z}",
      Map("Authentication" -> s"Bearer ${token(color)}")
    )
  def set(move: Move): StatusResponse = set(move.color, move.x, move.y, move.z)

  def pass(color: Color): StatusResponse =
    getSR(s"${GameData.ServerURL}/pass/${id}", Map("Authentication" -> s"Bearer ${token(color)}"))

  def playRandomGame(finish: Boolean): Unit =
    var gameOver = false
    var color = Black
    var numMoves = 0

    def setOrPass(statusResponse: StatusResponse): StatusResponse =
      if statusResponse.ready && statusResponse.game.possibleMoves(color).nonEmpty then
        set(Move(randomChoice(statusResponse.game.possibleMoves(color)), color))
      else
        pass(color)


    while !gameOver do
      val statusResponse = status(color)
      val boardSize: Int = statusResponse.game.size
      val maxNumMoves = boardSize * boardSize * boardSize
      val newStatusResponse = setOrPass(statusResponse)
      gameOver = if finish then newStatusResponse.game.isOver else numMoves >= maxNumMoves - 2
      color = !color
      numMoves += 1

object GameData:
  val ServerURL = s"http://localhost:$TestPort"
  def create(size: Int): GameCreatedResponse = getGCR(s"$ServerURL/new/$size")
  def register(id: String, color: Color): PlayerRegisteredResponse =
    getPRR(s"$ServerURL/register/$id/$color")

package go3d.server.http4s

import cats.effect.IO
import go3d.server.http4s.MakeMove
import go3d.{Color, Move}
import org.http4s.Request

class DoSet(
    override val gameId: String, override val request: Request[IO],
    val x: Int, val y: Int, val z: Int
) extends MakeMove(gameId, request):
  def makeMove(pathInfo: String, color: Color): Move = Move(x, y, z, color)

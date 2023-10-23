package go3d.server

import go3d.{Color, Pass}

class PassServlet extends MakeMoveServlet:

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength

  def makeMove(pathInfo: String, color: Color): Pass = Pass(color)

import cats.effect.IO
import org.http4s.Request
class PassHandler(override val gameId: String, override val request: Request[IO])
  extends MakeMoveHandler(gameId, request):
  def makeMove(pathInfo: String, color: Color): Pass = Pass(color)

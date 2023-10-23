package go3d.server

import go3d.{Color, Pass}

import cats.effect.IO
import org.http4s.Request

class DoPass(override val gameId: String, override val request: Request[IO])
  extends MakeMove(gameId, request):
  def makeMove(pathInfo: String, color: Color): Pass = Pass(color)

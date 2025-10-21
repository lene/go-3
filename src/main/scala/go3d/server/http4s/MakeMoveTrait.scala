package go3d.server.http4s

import go3d.Color
import go3d.Move
import go3d.Pass

trait MakeMoveTrait:
  def makeMove(pathInfo: String, color: Color): Move | Pass
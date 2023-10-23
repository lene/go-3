package go3d.server

import go3d.{Color, Move, Pass}

trait MakeMoveTrait:
  def makeMove(pathInfo: String, color: Color): Move | Pass
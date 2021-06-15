package go3d.server

import go3d.{Color, Move, Pass}

trait MakeMove:
  def makeMove(pathInfo: String, color: Color): Move | Pass
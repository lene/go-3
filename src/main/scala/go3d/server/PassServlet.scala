package go3d.server

import go3d.{Color, Pass}

class PassServlet extends MakeMoveServlet:

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength

  def makeMove(pathInfo: String, color: Color): Pass = Pass(color)

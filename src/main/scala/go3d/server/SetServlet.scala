package go3d.server

import go3d.{Color, Move}

class SetServlet extends MakeMoveServlet:

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength + 3*3

  def makeMove(pathInfo: String, color: Color): Move =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.length < 4 then throw MalformedRequest(pathInfo)
    val (x, y, z) = (parts(1).toInt, parts(2).toInt, parts(3).toInt)
    return Move(x, y, z, color)

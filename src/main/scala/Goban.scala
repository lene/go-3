package go3d

import Array._

class Goban(val size: Int, val numPlayers: Int = DefaultPlayers):

  if size < MinBoardSize then throw IllegalArgumentException("size too small: "+size)
  if size > MaxBoardSize then throw IllegalArgumentException("size too big: "+size)
  if size % 2 == 0 then throw IllegalArgumentException("size is even: "+size)
  if numPlayers > MaxPlayers then throw IllegalArgumentException("too many players: "+numPlayers)
  if numPlayers < 2 then throw IllegalArgumentException("too few players: "+numPlayers)

  var stones = ofDim[Color](size+2, size+2, size+2)
  initializeBoard

  override def toString: String =
    var out = ""
    for z <- 0 to size + 1 do
      for y <- 0 to size + 1 do
        for x <- 0 to size + 1 do
          stones(x)(y)(z) match
            case Color.Empty => out += " "
            // todo
            case _ => out += "*"
        out += "\n"
      out += "\n"
    out

  private def initializeBoard =
    for x <- 0 to size+1 do
      for y <- 0 to size+1 do
        for z <- 0 to size+1 do
          stones(x)(y)(z) = if x*y*z == 0 || x == size+1 || y == size+1 || z == size+1 then
            Color.Sentinel
          else
            Color.Empty

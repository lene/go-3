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
    for z <- 1 to size do
      for y <- 0 to size + 1 do
        for x <- 0 to size + 1 do
          out += stones(x)(y)(z)
        out += "\n"
    out

  private def initializeBoard =
    for x <- 0 to size+1 do
      for y <- 0 to size+1 do
        for z <- 0 to size+1 do
          stones(x)(y)(z) = if x*y*z == 0 || x > size || y > size || z > size then
            Color.Sentinel
          else
            Color.Empty

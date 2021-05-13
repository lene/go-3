package go3d

class Position(val x: Int, val y: Int, val z: Int):
  if x < 1 || y < 1 || z < 1 then
    throw IllegalArgumentException("coordinate < 1: "+x+", "+y+", "+z)

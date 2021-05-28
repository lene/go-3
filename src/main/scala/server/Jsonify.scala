package go3d.server

import ujson._

object Jsonify:
  def toJson(game: go3d.Game): String =
    val obj = ujson.Obj("size" -> game.size)
    return ujson.write(obj)




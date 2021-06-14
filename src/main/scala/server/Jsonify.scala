package go3d.server

import go3d.{Color, Game, Goban, HasColor, Move, Pass, Position}
import io.circe._
import io.circe.parser._
import io.circe.syntax._

import scala.io.Source

implicit val encodeColor: Encoder[Color] = new Encoder[Color] {
  final def apply(col: Color): Json = Json.obj(("color", Json.fromString(col.toString)))
}

implicit val decodeColor: Decoder[Color] = new Decoder[Color] {
  final def apply(c: HCursor): Decoder.Result[Color] =
    for col <- c.downField("color").as[Char]
    yield new Color(col)
}

implicit val colorKeyEncoder: KeyEncoder[Color] = new KeyEncoder[Color] {
  override def apply(col: Color): String = col.toString
}

implicit val colorKeyDecoder: KeyDecoder[Color] = new KeyDecoder[Color] {
  override def apply(key: String): Option[Color] = Some(Color(key(0)))
}

implicit val encodePosition: Encoder[Position] = new Encoder[Position] {
  final def apply(pos: Position): Json = Json.obj(
    ("x", Json.fromInt(pos.x)),
    ("y", Json.fromInt(pos.y)),
    ("z", Json.fromInt(pos.z))
  )
}

implicit val decodePosition: Decoder[Position] = new Decoder[Position] {
  final def apply(c: HCursor): Decoder.Result[Position] =
    for
      x <- c.downField("x").as[Int]
      y <- c.downField("y").as[Int]
      z <- c.downField("z").as[Int]
    yield new Position(x, y, z)
}

implicit val encodeMove: Encoder[Move] = new Encoder[Move] {
  final def apply(move: Move): Json = Json.obj(
    ("position", encodePosition(move.position)),
    ("color", encodeColor(move.color))
  )
}

implicit val decodeMove: Decoder[Move] = new Decoder[Move] {
  final def apply(c: HCursor): Decoder.Result[Move] =
    for
      pos <- c.downField("position").as[Position]
      col <- c.downField("color").as[Color]
    yield new Move(pos, col)
}

implicit val encodeMovePass: Encoder[Move | Pass] = new Encoder[Move | Pass] {
  final def apply(move: Move | Pass): Json = encodeHasColor(move)
}

implicit val decodeMovePass: Decoder[Move | Pass] = new Decoder[Move | Pass] {
  final def apply(c: HCursor): Decoder.Result[Move | Pass] =
    val keys = c.keys.getOrElse(List[String]()).toSet
    if keys.contains("position") then
      for
        pos <- c.downField("position").as[Position]
        col <- c.downField("color").as[Color]
      yield new Move(pos, col)
    else
      for
        pass <- c.downField("pass").as[Boolean]
        col <- c.downField("color").as[Color]
      yield new Pass(col)
}

implicit val encodeHasColor: Encoder[HasColor] = new Encoder[HasColor] {
  final def apply(move: HasColor): Json =
    move match
      case m: Move => encodeMove(m)
      case p: Pass => Json.obj(
        ("pass", Json.fromBoolean(true)),
        ("color", encodeColor(p.color))
      )
}

implicit val decodeHasColor: Decoder[HasColor] = new Decoder[HasColor] {
  final def apply(c: HCursor): Decoder.Result[HasColor] =
    val keys = c.keys.getOrElse(List[String]()).toSet
    if keys.contains("position") then
      for
        pos <- c.downField("position").as[Position]
        col <- c.downField("color").as[Color]
      yield new Move(pos, col)
    else
      for
        pass <- c.downField("pass").as[Boolean]
        col <- c.downField("color").as[Color]
      yield new Pass(col)
}

implicit val encodeGoban: Encoder[Goban] = new Encoder[Goban] {
  final def apply(goban: Goban): Json = Json.obj(
    ("size", Json.fromInt(goban.size)), ("stones", Goban.toStrings(goban).asJson)
  )
}

implicit val decodeGoban: Decoder[Goban] = new Decoder[Goban] {
  final def apply(c: HCursor): Decoder.Result[Goban] =
    for
      size <- c.downField("size").as[Int]
      stones <- c.downField("stones").as[Array[String]]
    yield Goban.fromStrings(stones)
}

implicit val encodeGame: Encoder[Game] = new Encoder[Game] {
  final def apply(game: Game): Json = Json.obj(
    ("size", Json.fromInt(game.size)),
    ("goban", game.goban.asJson),
    ("moves", game.moves.asJson),
    ("captures", game.captures.asJson)
  )
}

implicit val decodeGame: Decoder[Game] = new Decoder[Game] {
  final def apply(c: HCursor): Decoder.Result[Game] =
    for
      size <- c.downField("size").as[Int]
      goban <- c.downField("goban").as[Goban]
      moves <- c.downField("moves").as[Array[Move | Pass]]
      captures <- c.downField("captures").as[Map[Int, Array[Move]]]
    yield new Game(size, goban, moves, captures)
}

implicit val encodePlayer: Encoder[Player] = new Encoder[Player] {
  final def apply(player: Player): Json = Json.obj(
    ("color", player.color.asJson),
    ("gameId", Json.fromString(player.gameId)),
    ("token", Json.fromString(player.token))
  )
}

implicit val decodePlayer: Decoder[Player] = new Decoder[Player] {
  final def apply(c: HCursor): Decoder.Result[Player] =
    for
      color <- c.downField("color").as[Color]
      gameId <- c.downField("gameId").as[String]
      token <- c.downField("token").as[String]
    yield new Player(color, gameId, token)
}

implicit val encodeSaveGame: Encoder[SaveGame] = new Encoder[SaveGame] {
  final def apply(saveGame: SaveGame): Json = Json.obj(
    ("game", saveGame.game.asJson),
    ("players", saveGame.players.asJson),
  )
}

implicit val decodeSaveGame: Decoder[SaveGame] = new Decoder[SaveGame] {
  final def apply(c: HCursor): Decoder.Result[SaveGame] =
    for
      game <- c.downField("game").as[Game]
      players <- c.downField("players").as[Map[Color, Player]]
    yield new SaveGame(game, players)
}

implicit val encodeErrorResponse: Encoder[ErrorResponse] = new Encoder[ErrorResponse] {
  final def apply(response: ErrorResponse): Json = Json.obj(
    ("error", Json.fromString(response.err))
  )
}

implicit val decodeErrorResponse: Decoder[ErrorResponse] = new Decoder[ErrorResponse] {
  final def apply(c: HCursor): Decoder.Result[ErrorResponse] =
    for
      err <- c.downField("error").as[String]
    yield new ErrorResponse(err)
}

implicit val encodeGameCreatedResponse: Encoder[GameCreatedResponse] = new Encoder[GameCreatedResponse] {
  final def apply(response: GameCreatedResponse): Json = Json.obj(
    ("id", Json.fromString(response.id)),
    ("size", Json.fromInt(response.size))
  )
}

implicit val decodeGameCreatedResponse: Decoder[GameCreatedResponse] = new Decoder[GameCreatedResponse] {
  final def apply(c: HCursor): Decoder.Result[GameCreatedResponse] =
    for
      id <- c.downField("id").as[String]
      size <- c.downField("size").as[Int]
    yield new GameCreatedResponse(id, size)
}

implicit val encodeRequestInfo: Encoder[RequestInfo] = new Encoder[RequestInfo] {
  final def apply(response: RequestInfo): Json = Json.obj(
    ("headers", response.headers.asJson),
    ("query", Json.fromString(response.query)),
    ("pathInfo", Json.fromString(response.path))
  )
}

implicit val decodeRequestInfo: Decoder[RequestInfo] = new Decoder[RequestInfo] {
  final def apply(c: HCursor): Decoder.Result[RequestInfo] =
    for
      headers <- c.downField("headers").as[Map[String, String]]
      query <- c.downField("query").as[String]
      pathInfo <- c.downField("pathInfo").as[String]
    yield new RequestInfo(headers, query, pathInfo)
}

implicit val encodePlayerRegisteredResponse: Encoder[PlayerRegisteredResponse] = new Encoder[PlayerRegisteredResponse] {
  final def apply(response: PlayerRegisteredResponse): Json = Json.obj(
    ("game", response.game.asJson),
    ("color", response.color.asJson),
    ("authToken", Json.fromString(response.authToken)),
    ("ready", Json.fromBoolean(response.ready)),
    ("debug", response.debug.asJson)
  )
}

implicit val decodePlayerRegisteredResponse: Decoder[PlayerRegisteredResponse] = new Decoder[PlayerRegisteredResponse] {
  final def apply(c: HCursor): Decoder.Result[PlayerRegisteredResponse] =
    for
      game <- c.downField("game").as[Game]
      color <- c.downField("color").as[Color]
      authToken <- c.downField("authToken").as[String]
      ready <- c.downField("ready").as[Boolean]
      debug <- c.downField("debug").as[RequestInfo]
    yield new PlayerRegisteredResponse(game, color, authToken, ready, debug)
}

implicit val encodeStatusResponse: Encoder[StatusResponse] = new Encoder[StatusResponse] {
  final def apply(response: StatusResponse): Json = Json.obj(
    ("game", response.game.asJson),
    ("moves", response.moves.asJson),
    ("ready", Json.fromBoolean(response.ready)),
    ("debug", response.debug.asJson)
  )
}

implicit val decodeStatusResponse: Decoder[StatusResponse] = new Decoder[StatusResponse] {
  final def apply(c: HCursor): Decoder.Result[StatusResponse] =
    for
      game <- c.downField("game").as[Game]
      moves <- c.downField("moves").as[List[Position]]
      ready <- c.downField("ready").as[Boolean]
      debug <- c.downField("debug").as[RequestInfo]
    yield new StatusResponse(game, moves, ready, debug)
}

implicit val encodeGoResponse: Encoder[GoResponse] = new Encoder[GoResponse] {
  final def apply(response: GoResponse): Json =
    response match
      case r: StatusResponse => encodeStatusResponse(r)
      case r: PlayerRegisteredResponse => encodePlayerRegisteredResponse(r)
      case r: ErrorResponse => encodeErrorResponse(r)
      case r: GameCreatedResponse => encodeGameCreatedResponse(r)
}

//def getResponse[T<:GoResponse](url: String): T =
//  val json = Source.fromURL(url).mkString
//  return decode[T](json)

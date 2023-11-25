package go3d.server

import io.circe._
import io.circe.syntax._

import scala.reflect.ClassTag
import go3d.{Color, Game, Goban, HasColor, Move, Pass, Position}

implicit val encodeColor: Encoder[Color] =
  (col: Color) => Json.obj(("color", Json.fromString(col.toString)))

implicit val decodeColor: Decoder[Color] =
  (c: HCursor) => for col <- c.downField("color").as[Char]
    yield Color(col)

implicit val colorKeyEncoder: KeyEncoder[Color] = (col: Color) => col.toString

implicit val colorKeyDecoder: KeyDecoder[Color] = (key: String) => Some(Color(key(0)))

implicit val encodePosition: Encoder[Position] = (pos: Position) => Json.obj(
  ("x", Json.fromInt(pos.x)),
  ("y", Json.fromInt(pos.y)),
  ("z", Json.fromInt(pos.z))
)

implicit val decodePosition: Decoder[Position] =
  (c: HCursor) => for
    x <- c.downField("x").as[Int]
    y <- c.downField("y").as[Int]
    z <- c.downField("z").as[Int]
  yield new Position(x, y, z)

implicit val encodeMove: Encoder[Move] =
  (move: Move) => Json.obj(
    ("position", encodePosition(move.position)),
    ("color", encodeColor(move.color))
  )

implicit val decodeMove: Decoder[Move] =
  (c: HCursor) => for
    pos <- c.downField("position").as[Position]
    col <- c.downField("color").as[Color]
  yield new Move(pos, col)

implicit val encodeMovePass: Encoder[Move | Pass] = (move: Move | Pass) => encodeHasColor(move)

implicit val decodeMovePass: Decoder[Move | Pass] =
  (c: HCursor) =>
    val keys = c.keys.getOrElse(List[String]()).toSet
    if keys.contains("position") then
      for
        pos <- c.downField("position").as[Position]
        col <- c.downField("color").as[Color]
      yield new Move(pos, col)
    else
      for
        _ <- c.downField("pass").as[Boolean]
        col <- c.downField("color").as[Color]
      yield new Pass(col)

implicit val encodeHasColor: Encoder[HasColor] =
    case m: Move => encodeMove(m)
    case p: Pass => Json.obj(
      ("pass", Json.fromBoolean(true)),
      ("color", encodeColor(p.color))
    )

def gobanFromStrings(levels: Array[String]): Goban =
  if levels.isEmpty then throw IllegalArgumentException("nothing to generate")
  val size = levels(0).stripMargin.replace("|", "").split("\n").length
  if levels.length != size then throw JsonDecodeError(s"${levels.length} != $size")
  val goban = Goban.start(size)
  for (level, z) <- levels.zipWithIndex do
    val lines = level.stripMargin.replace("|", "").split("\n")
    if lines.length != size then throw JsonDecodeError(s"${lines.toString}: ${lines.length} != $size")
    for (line, y) <- lines.zipWithIndex do
      if line.length != size then throw JsonDecodeError(s"\"$line\": ${line.length} != $size")
      for (stone, x) <- line.zipWithIndex do
        goban.stones(x+1)(y+1)(z+1) = Color(stone)
  goban

def gobanToStrings(goban: Goban): Array[String] =
  val strings = Array.fill(goban.size){""}
  for z <- 1 to goban.size do
    for y <- 1 to goban.size do
      for x <- 1 to goban.size do
        strings(z-1) += goban.at(x, y, z)
      if y < goban.size then strings(z-1) += "\n"
  strings

implicit val encodeGoban: Encoder[Goban] =
  (goban: Goban) => Json.obj(
    ("size", Json.fromInt(goban.size)), ("stones", gobanToStrings(goban).asJson)
  )

implicit val decodeGoban: Decoder[Goban] =
  (c: HCursor) => for
    _ <- c.downField("size").as[Int]
    stones <- c.downField("stones").as[Array[String]]
  yield gobanFromStrings(stones)

implicit val encodeGame: Encoder[Game] =
  (game: Game) => Json.obj(
    ("size", Json.fromInt(game.size)),
    ("goban", game.goban.asJson),
    ("moves", game.moves.asJson),
    ("captures", game.captures.asJson)
  )

implicit val decodeGame: Decoder[Game] =
  (c: HCursor) => for
    size <- c.downField("size").as[Int]
    goban <- c.downField("goban").as[Goban]
    moves <- c.downField("moves").as[Array[Move | Pass]]
    captures <- c.downField("captures").as[Map[Int, Array[Move]]]
  yield new Game(size, goban, moves, captures)

implicit val encodePlayer: Encoder[Player] =
  (player: Player) => Json.obj(
    ("color", player.color.asJson),
    ("gameId", Json.fromString(player.gameId)),
    ("token", Json.fromString(player.token))
  )

implicit val decodePlayer: Decoder[Player] =
  (c: HCursor) => for
    color <- c.downField("color").as[Color]
    gameId <- c.downField("gameId").as[String]
    token <- c.downField("token").as[String]
  yield Player(color, gameId, token)

implicit val encodeSaveGame: Encoder[SaveGame] =
  (saveGame: SaveGame) => Json.obj(
    ("game", saveGame.game.asJson),
    ("players", saveGame.players.asJson),
  )

implicit val decodeSaveGame: Decoder[SaveGame] =
  (c: HCursor) => for
    game <- c.downField("game").as[Game]
    players <- c.downField("players").as[Map[Color, Player]]
  yield SaveGame(game, players)

implicit val encodeErrorResponse: Encoder[ErrorResponse] =
  (response: ErrorResponse) => Json.obj(("error", Json.fromString(response.err)))

implicit val decodeErrorResponse: Decoder[ErrorResponse] =
  (c: HCursor) => for err <- c.downField("error").as[String]
    yield ErrorResponse(err)

implicit val encodeGameCreatedResponse: Encoder[GameCreatedResponse] =
  (response: GameCreatedResponse) => Json.obj(
    ("id", Json.fromString(response.id)),
    ("size", Json.fromInt(response.size))
  )

implicit val decodeGameCreatedResponse: Decoder[GameCreatedResponse] =
  (c: HCursor) => for
    id <- c.downField("id").as[String]
    size <- c.downField("size").as[Int]
  yield GameCreatedResponse(id, size)

implicit val encodeRequestInfo: Encoder[RequestInfo] =
  (response: RequestInfo) => Json.obj(
    ("headers", response.headers.asJson),
    ("query", Json.fromString(response.query)),
    ("pathInfo", Json.fromString(response.path))
  )

implicit val decodeRequestInfo: Decoder[RequestInfo] =
  (c: HCursor) => for
    headers <- c.downField("headers").as[Map[String, String]]
    query <- c.downField("query").as[String]
    pathInfo <- c.downField("pathInfo").as[String]
  yield new RequestInfo(headers, query, pathInfo, false)

implicit val encodePlayerRegisteredResponse: Encoder[PlayerRegisteredResponse] =
  (response: PlayerRegisteredResponse) => Json.obj(
    ("game", response.game.asJson),
    ("color", response.color.asJson),
    ("authToken", Json.fromString(response.authToken)),
    ("ready", Json.fromBoolean(response.ready)),
    ("debug", response.debug.asJson)
  )

implicit val decodePlayerRegisteredResponse: Decoder[PlayerRegisteredResponse] =
  (c: HCursor) => for
    game <- c.downField("game").as[Game]
    color <- c.downField("color").as[Color]
    authToken <- c.downField("authToken").as[String]
    ready <- c.downField("ready").as[Boolean]
    debug <- c.downField("debug").as[RequestInfo]
  yield PlayerRegisteredResponse(game, color, authToken, ready, debug)

implicit val encodeStatusResponse: Encoder[StatusResponse] =
  (response: StatusResponse) => Json.obj(
      ("game", response.game.asJson),
      ("moves", response.moves.asJson),
      ("ready", Json.fromBoolean(response.ready)),
      ("over", Json.fromBoolean(response.over)),
      ("debug", response.debug.asJson)
    )

implicit val decodeStatusResponse: Decoder[StatusResponse] =
  (c: HCursor) => for
    game <- c.downField("game").as[Game]
    moves <- c.downField("moves").as[List[Position]]
    ready <- c.downField("ready").as[Boolean]
    over <- c.downField("over").as[Boolean]
    debug <- c.downField("debug").as[RequestInfo]
  yield StatusResponse(game, moves, ready, over, debug)

implicit val encodeOpenGamesResponse: Encoder[GameListResponse] =
  (response: GameListResponse) => Json.obj(("ids", response.ids.asJson))

implicit val decodeOpenGamesResponse: Decoder[GameListResponse] =
  (c: HCursor) => for
    ids <- c.downField("ids").as[Array[String]]
  yield GameListResponse(ids)

implicit val encodeGoResponse: Encoder[GoResponse] =
    case r: StatusResponse => encodeStatusResponse(r)
    case r: PlayerRegisteredResponse => encodePlayerRegisteredResponse(r)
    case r: ErrorResponse => encodeErrorResponse(r)
    case r: GameCreatedResponse => encodeGameCreatedResponse(r)
    case r: GameListResponse => encodeOpenGamesResponse(r)


//import scala.io.Source
//def getResponse[T<:GoResponse](url: String)(implicit cType:ClassTag[T]): T =
//  val json = Source.fromURL(url).mkString
//  return Decoder[T].decodeJson(json)

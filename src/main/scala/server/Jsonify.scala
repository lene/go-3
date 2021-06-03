package go3d.server

import go3d.{Color, Game, Goban, HasColor, Move, Pass, Position}

import java.lang.reflect.Type
import com.google.gson._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.Encoder.AsObject.importedAsObjectEncoder

import scala.reflect.ClassTag
import scala.collection.mutable

object ColorSerializer extends JsonSerializer[Color]:
  override def serialize(color: Color, t2: Type,
                         jsonSerializationContext: JsonSerializationContext): JsonElement =
    JsonPrimitive(color.toString)

object ColorDeserializer extends JsonDeserializer[Color]:
  override def deserialize(json: JsonElement, typeOfT: Type,
                           context: JsonDeserializationContext): Color =
    val res = json.getAsJsonPrimitive().getAsString
    Color(res.charAt(0))

object MoveMapInstanceCreator extends InstanceCreator[Map[Int, List[Move]]]:
  override def createInstance(typeOfT: Type) = Map()

object PlayerMapInstanceCreator extends InstanceCreator[mutable.Map[Color, Player]]:
  override def createInstance(typeOfT: Type) = mutable.Map[Color, Player]()

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
    ("size", Json.fromInt(goban.size)), ("stones", goban.stones.asJson)
  )
}

implicit val decodeGoban: Decoder[Goban] = new Decoder[Goban] {
  final def apply(c: HCursor): Decoder.Result[Goban] =
    for
      size <- c.downField("size").as[Int]
      stones <- c.downField("stones").as[Array[Array[Array[Color]]]]
    yield new Goban(size, stones)
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

object Jsonify:
  val mygson = new GsonBuilder()
    .registerTypeAdapter(classOf[Color], ColorSerializer)
    .registerTypeAdapter(classOf[Color], ColorDeserializer)
    .registerTypeAdapter(classOf[Map[Int, List[Move]]], MoveMapInstanceCreator)
//    .registerTypeAdapter(classOf[mutable.Map[Color, Player]], PlayerMapInstanceCreator)
    .create()

  def toJson[T](obj: T): String = mygson.toJson(obj)
//  def toJson[T](obj: T): String = obj.asJson.noSpaces

//  def fromJson[Map[Int, String]](json: String) =
//    mygson.fromJson(json, classOf[java.util.Map[Int, String]]).asInstanceOf[Map[Int, String]]

//  def toJson(obj: Position): String = obj.asJson.noSpaces
//  def toJson(obj: Move): String = obj.asJson.noSpaces

  def fromJson[T](json: String)(implicit ct: ClassTag[T]) =
    mygson.fromJson(json, ct.runtimeClass).asInstanceOf[T]

//  def fromJson[T](json: String)(implicit ct: ClassTag[T]): T = decode[T](json).getOrElse(null)
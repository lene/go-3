package go3d.server

import go3d.{Color, Game, Goban, Move, colorFromChar}

import java.lang.reflect.Type
import com.google.gson._

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
    colorFromChar(res.charAt(0))

object MoveMapInstanceCreator extends InstanceCreator[Map[Int, List[Move]]]:
  override def createInstance(typeOfT: Type) = Map()

object PlayerMapInstanceCreator extends InstanceCreator[mutable.Map[Color, Player]]:
  override def createInstance(typeOfT: Type) = mutable.Map()

object Jsonify:
  val mygson = new GsonBuilder()
    .registerTypeAdapter(classOf[Color], ColorSerializer)
    .registerTypeAdapter(classOf[Color], ColorDeserializer)
    .registerTypeAdapter(classOf[Map[Int, List[Move]]], MoveMapInstanceCreator)
    .registerTypeAdapter(classOf[mutable.Map[Color, Player]], PlayerMapInstanceCreator)
    .create()

  def toJson(game: Game): String = mygson.toJson(game)
  def toJson(color: Color): String = mygson.toJson(color)
  def toJson(obj: Any): String = mygson.toJson(obj)

  def fromJson[T](json: String)(implicit ct: ClassTag[T]) =
    mygson.fromJson(json, ct.runtimeClass).asInstanceOf[T]

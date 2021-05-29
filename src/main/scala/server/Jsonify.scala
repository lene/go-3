package go3d.server

import go3d.{Color, Game, Goban, colorFromChar}
import java.lang.reflect.Type
import com.google.gson._

class ColorSerializer extends JsonSerializer[Color]:
  override def serialize(color: Color, t2: Type, jsonSerializationContext: JsonSerializationContext): JsonElement =
    JsonPrimitive(color.toString)

class ColorDeserializer extends JsonDeserializer[Color]:
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Color =
    val res = json.getAsJsonPrimitive().getAsString
    colorFromChar(res.charAt(0))

object Jsonify:
  val mygson = new GsonBuilder()
    .registerTypeAdapter(classOf[Color], ColorSerializer())
    //  .registerTypeAdapter(classOf[Color], ColorDeserializer())
    .create()

  def toJson(game: Game): String = mygson.toJson(game)
  def toJson(color: Color): String = mygson.toJson(color)
  def toJson(obj: Any): String = mygson.toJson(obj)




package go3d

class Color(ascii: Char):
  override def toString: String = ascii.toString
   
  def unary_! =
    ascii match
      case '@' => White
      case 'O' => Black
      case _ => throw IllegalArgumentException(toString)

val Empty = Color(' ')
val Black = Color('@')
val White = Color('O')
val Sentinel = Color('·')

def colorFromChar(c: Char): Color =
  c match
    case '@' => Black
    case 'O' => White
    case ' ' => Empty
    case '·' => Sentinel
    case _ => throw IllegalArgumentException(toString)

package go3d

case class Color(ascii: Char):
  if !Set(' ', '@', 'O', '·').contains(ascii) then throw BadColor(ascii)
  override def toString: String = ascii.toString
  override def hashCode(): Int = toString.hashCode()

  def unary_! : Color =
    ascii match
      case '@' => White
      case 'O' => Black
      case _ => throw IllegalArgumentException(toString)

val Empty = Color(' ')
val Black = Color('@')
val White = Color('O')
val Sentinel = Color('·')

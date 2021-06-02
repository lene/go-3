package go3d

import scala.language.implicitConversions

case class Color(ascii: Char):
  if !Set(' ', '@', 'O', '·').contains(ascii) then throw BadColor(ascii)
  override def toString: String = ascii.toString
  override def hashCode(): Int = toString.hashCode()

  def unary_! =
    ascii match
      case '@' => White
      case 'O' => Black
      case _ => throw IllegalArgumentException(toString)

val Empty = Color(' ')
val Black = Color('@')
val White = Color('O')
val Sentinel = Color('·')

implicit def colorToChar(col: Color): Char = col.ascii
implicit def charToColor(c: Char): Color = Color(c)

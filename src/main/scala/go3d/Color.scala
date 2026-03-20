package go3d

import scala.util.{Failure, Success, Try}

case class Color private(ascii: Char):
  override def toString: String = ascii.toString
  override def hashCode(): Int = toString.hashCode()

  def unary_! : Color =
    ascii match
      case '@' => Color.White
      case 'O' => Color.Black
      case _ => sys.error(s"no opponent for $this")

object Color:
  val Empty: Color = new Color(' ')
  val Black: Color = new Color('@')
  val White: Color = new Color('O')
  val Sentinel: Color = new Color('·')

  def apply(ascii: Char): Try[Color] =
    if Set(' ', '@', 'O', '·').contains(ascii) then Success(new Color(ascii))
    else Failure(BadColor(ascii))

val Empty: Color = Color.Empty
val Black: Color = Color.Black
val White: Color = Color.White
val Sentinel: Color = Color.Sentinel

package go3d

case class Pass(val color: Color) extends HasColor:
  override def toString: String = "pass "+color.toString

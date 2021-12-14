package go3d

class Pass(val color: Color) extends HasColor:
  override def toString: String = "pass "+color.toString

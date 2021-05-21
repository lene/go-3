package go3d

class IllegalMove(val message: String) extends IllegalArgumentException
class OutsideBoard(x: Int, y: Int, z: Int)
  extends IllegalMove(message = s"outside board: $x, $y, $z")
class PositionOccupied(move: Move, color: Color)
  extends IllegalMove(message = s"occupied: $move with $color")
class Ko(move: Move) extends IllegalMove(message = "ko at "+move.toString)
class Suicide(move: Move) extends IllegalMove(message = "suicide at "+move.toString)
class WrongTurn(move: Move) extends IllegalMove(message = "not your turn at "+move.toString)

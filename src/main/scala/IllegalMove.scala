package go3d

class IllegalMove(val message: String) extends IllegalArgumentException
class Ko(move: Move) extends IllegalMove(message = "ko at "+move.toString)
class Suicide(move: Move) extends IllegalMove(message = "suicide at "+move.toString)
class WrongTurn(move: Move) extends IllegalMove(message = "not your turn at "+move.toString)

import go3d._

@main def printDefinitions: Unit =
  println("Just some random output to verify definitions are ok:")
  val goban = Goban(9)
  val moved = goban.makeMove(Move(2, 2, 2, Color.Black))
  println(goban.toString)

import go3d._

@main def printDefinitions: Unit =
  println("Just some output to verify definitions are ok:")
  print(Color.Black)
  print(Color.White)
  print(Color.Empty)
  print(Color.Undefined)
  print(Color.Sentinel)
  print(Color.Red)
  print(Color.Green)
  print(Color.Blue)
  println()
  val goban = Goban(3)
  val moved = goban.newBoard(goban.Move(2, 2, 2, Color.Black))
  println(goban.toString)

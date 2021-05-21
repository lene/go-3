import go3d._

def replayGame(goban: Game, moves: List[Move | Pass], delayMs: Int): Unit =
  var board = goban
  for move <- moves do {
    board = board.makeMove(move)
    println(move.toString+"\n"+goban)
    Thread.sleep(delayMs)
  }


@main def printAGame: Unit =
  val Delay = 0
  // watch black capture a white stone
  val moves1 =  Move(2, 2, 2, Color.Black) :: Move(2, 2, 1, Color.White) ::
    Move(2, 1, 1, Color.Black) :: Move(2, 2, 3, Color.White) ::
    Move(2, 3, 1, Color.Black) :: Move(2, 1, 2, Color.White) ::
    Move(3, 2, 1, Color.Black) :: Move(2, 3, 2, Color.White) ::
    Move(1, 2, 1, Color.Black) :: Nil
  replayGame(Game(5), moves1, Delay)
  Thread.sleep(2*Delay)
  // black builds an eye, then white captures it
  val moves2 = List[Move | Pass](
    Move(2, 1, 1, Color.Black), Pass(Color.White), Move(1, 2, 1, Color.Black), Pass(Color.White),
    Move(2, 1, 2, Color.Black), Pass(Color.White), Move(1, 2, 2, Color.Black), Pass(Color.White),
    Move(1, 1, 2, Color.Black),
    // build the eye first and then encircle it, IMHO that is easier to read
    Move(1, 3, 1, Color.White), Pass(Color.Black), Move(2, 2, 1, Color.White), Pass(Color.Black),
    Move(3, 1, 1, Color.White), Pass(Color.Black), Move(1, 3, 2, Color.White), Pass(Color.Black),
    Move(2, 2, 2, Color.White), Pass(Color.Black), Move(3, 1, 2, Color.White), Pass(Color.Black),
    Move(1, 1, 3, Color.White), Pass(Color.Black), Move(2, 1, 3, Color.White), Pass(Color.Black),
    Move(1, 2, 3, Color.White), Pass(Color.Black), Move(1, 1, 1, Color.White)
  )
  replayGame(Game(5), moves2, Delay)

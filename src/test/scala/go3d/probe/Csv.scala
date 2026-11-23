package go3d.probe

/** The per-game rows of a probe run. Win rates, flips and any komi derive from the rows. */
object Csv:
  val Header: String = List(
    "size",
    "black",
    "white",
    "game",
    "moves",
    "ending",
    "black_stones",
    "black_area",
    "black_captures",
    "white_stones",
    "white_area",
    "white_captures",
    "record"
  ).mkString(",")

  /** The header and one line for each result, ending with a newline. */
  def render(results: Seq[GameResult]): String = (Header +: results.map(line)).mkString(
    "",
    "\n",
    "\n"
  )

  def line(result: GameResult): String =
    val fields = List(
      result.size.toString,
      result.black,
      result.white,
      result.game.toString,
      result.moves.toString,
      result.ending.text,
      result.blackSide.stones.toString,
      result.blackSide.area.toString,
      result.blackSide.captures.toString,
      result.whiteSide.stones.toString,
      result.whiteSide.area.toString,
      result.whiteSide.captures.toString,
      result.record.getOrElse("")
    )
    fields.mkString(",")

  /** The results in `text`, which starts with the [[Header]]; or the first line that is not one. */
  def parse(text: String): Either[String, List[GameResult]] =
    text.linesIterator.toList match
      case header :: rows if header == Header =>
        rows.zipWithIndex.foldRight[Either[String, List[GameResult]]](Right(Nil)) {
          case ((row, index), acc) =>
            for
              head <- parseLine(row).left.map(reason => s"line ${index + 2}: $reason")
              tail <- acc
            yield head :: tail
        }
      case _ => Left(s"the first line is not '$Header'")

  def parseLine(line: String): Either[String, GameResult] =
    line.split(",", -1).toList match
      case size :: black :: white :: game :: moves :: ending :: bs :: ba :: bc :: ws :: wa :: wc ::
          record :: Nil =>
        for
          s <- number(size)
          g <- number(game)
          m <- number(moves)
          e <- Ending.fromText(ending).toRight(s"unknown ending '$ending'")
          blackSide <- side(bs, ba, bc)
          whiteSide <- side(ws, wa, wc)
        yield GameResult(
          s,
          black,
          white,
          g,
          m,
          e,
          blackSide,
          whiteSide,
          Option.when(record.nonEmpty)(record)
        )
      case fields => Left(s"${fields.length} fields instead of 13")

  private def side(stones: String, area: String, captures: String): Either[String, SideScore] =
    for
      s <- number(stones)
      a <- number(area)
      c <- number(captures)
    yield SideScore(s, a, c)

  private def number(text: String): Either[String, Int] =
    text.toIntOption.toRight(s"'$text' is not a number")

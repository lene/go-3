package go3d.probe

/** What a rules probe run does. [[ProbeOptions.parse]] reads it from the command line, and a run
  * whose options do not parse plays nothing.
  *
  * `gamesPerPairing` has an entry for each of the `sizes`; there is one game per entry for each
  * ordered pair of `strategies`, and the pairs include each strategy against itself.
  */
final case class ProbeOptions(
    sizes: List[Int],
    gamesPerPairing: Map[Int, Int],
    seed: Long,
    parallelism: Int,
    outDir: String,
    strategies: List[String]
):
  def pairings: List[(String, String)] =
    for black <- strategies; white <- strategies yield (black, white)

object ProbeOptions:
  /** The board sizes of the project: [[go3d.Goban]] rejects the even ones. */
  val BoardSizes: List[Int] = List(5, 7, 9)

  /** The strategies of [[go3d.client.SetStrategy]], in the order the record ids count them. */
  val StrategyNames: List[String] = List(
    "random",
    "closestToCenter",
    "onStarPoints",
    "closestToStarPoints",
    "maximizeOwnLiberties",
    "minimizeOpponentLiberties",
    "maximizeDistance",
    "prioritiseCapture"
  )

  val DefaultGames: Int = 100
  val DefaultSeed: Long = 1L
  val DefaultOutDir: String = "results/rules-probe"

  /** The record id of a game has six digits for the game number. */
  val MaxGamesPerPairing: Int = 999_999

  val Usage: String =
    """Usage: sbt "Test/runMain go3d.probe.RulesProbe [options]"
      |  --sizes 5,7,9        board sizes to play (default 5,7,9)
      |  --games N            games per pairing, or per size as 5=200,7=100,9=20 (default 100)
      |  --seed N             seed that reproduces a run (default 1)
      |  --parallelism N      games played at the same time (default: the number of processors)
      |  --out DIR            output directory (default results/rules-probe)
      |  --strategies a,b,c   strategies to play (default: all 8)""".stripMargin

  private val Known = Set("sizes", "games", "seed", "parallelism", "out", "strategies")

  /** The defaults for a machine with `processors` processors. */
  def defaults(processors: Int): ProbeOptions =
    ProbeOptions(
      BoardSizes,
      gamesForEach(BoardSizes, DefaultGames),
      DefaultSeed,
      processors,
      DefaultOutDir,
      StrategyNames
    )

  private def gamesForEach(sizes: List[Int], count: Int): Map[Int, Int] =
    sizes.map(_ -> count).toMap

  /** The options in `args` (`--name value` pairs) on top of the defaults, or the reason they are
    * not valid. A size outside 5, 7 and 9, an unknown strategy or an unknown option is not valid.
    */
  def parse(args: Seq[String], processors: Int): Either[String, ProbeOptions] =
    val base = defaults(processors)
    for
      values <- optionValues(args)
      sizes <- values.get("sizes").fold(Right(base.sizes))(parseSizes)
      games <- values.get("games").fold(Right(gamesForEach(sizes, DefaultGames)))(
        parseGames(_, sizes)
      )
      seed <- values.get("seed").fold(Right(base.seed))(parseSeed)
      parallelism <- values.get("parallelism").fold(Right(base.parallelism))(parseParallelism)
      strategies <- values.get("strategies").fold(Right(base.strategies))(parseStrategies)
    yield ProbeOptions(
      sizes,
      games,
      seed,
      parallelism,
      values.getOrElse("out", base.outDir),
      strategies
    )

  private def optionValues(args: Seq[String]): Either[String, Map[String, String]] =
    args.grouped(2).toList.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      (acc, group) =>
        for
          values <- acc
          name <- optionName(group)
          value <- group.lift(1).toRight(s"--$name needs a value")
          _ <- Either.cond(!values.contains(name), (), s"--$name is given twice")
        yield values + (name -> value)
    }

  private def optionName(group: Seq[String]): Either[String, String] =
    val word = group.head
    if !word.startsWith("--") then Left(s"expected an option, found '$word'")
    else if !Known.contains(word.drop(2)) then Left(s"unknown option '$word'")
    else Right(word.drop(2))

  private def parseSizes(text: String): Either[String, List[Int]] =
    for
      sizes <- all(text.split(",", -1).toList)(parseSize)
      _ <- Either.cond(sizes.distinct == sizes, (), s"--sizes lists a size twice: $text")
    yield sizes

  private def parseSize(text: String): Either[String, Int] =
    text.trim.toIntOption.toRight(s"size '$text' is not a number").flatMap { size =>
      Either.cond(BoardSizes.contains(size), size, s"size $size is not one of 5, 7 and 9")
    }

  private def parseGames(text: String, sizes: List[Int]): Either[String, Map[Int, Int]] =
    if text.contains("=") then
      for
        entries <- all(text.split(",", -1).toList)(parseSizeGames)
        perSize = entries.toMap
        _ <- Either.cond(
          entries.length == perSize.size && perSize.keySet == sizes.toSet,
          (),
          s"--games needs one entry for each of the sizes ${sizes.mkString(",")}: $text"
        )
      yield perSize
    else parseGameCount(text).map(gamesForEach(sizes, _))

  private def parseSizeGames(entry: String): Either[String, (Int, Int)] =
    entry.split("=", -1).toList match
      case size :: count :: Nil => for s <- parseSize(size); c <- parseGameCount(count) yield (s, c)
      case _ => Left(s"'$entry' is not size=games")

  private def parseGameCount(text: String): Either[String, Int] =
    text.trim.toIntOption.filter(count => count >= 1 && count <= MaxGamesPerPairing).toRight(
      s"games per pairing '$text' is not a number from 1 to $MaxGamesPerPairing"
    )

  private def parseSeed(text: String): Either[String, Long] =
    text.trim.toLongOption.toRight(s"seed '$text' is not a number")

  private def parseParallelism(text: String): Either[String, Int] =
    text.trim.toIntOption.filter(_ >= 1).toRight(s"parallelism '$text' is not a positive number")

  private def parseStrategies(text: String): Either[String, List[String]] =
    val names = text.split(",", -1).toList.map(_.trim)
    names.find(!StrategyNames.contains(_)) match
      case Some(unknown) =>
        Left(s"unknown strategy '$unknown', known: ${StrategyNames.mkString(", ")}")
      case None =>
        if names.distinct == names then Right(names)
        else Left(s"--strategies lists a strategy twice: $text")

  /** `f` of every item, or the first failure. */
  private def all[A, B](items: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    items.foldRight[Either[String, List[B]]](Right(Nil)) { (item, acc) =>
      for
        head <- f(item)
        tail <- acc
      yield head :: tail
    }

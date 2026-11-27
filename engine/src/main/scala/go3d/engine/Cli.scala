package go3d.engine

import org.rogach.scallop.{ScallopConf, exceptions}

import scala.util.{Failure, Success, Try}

/** What the engine's commands ([[SelfPlay]], [[Match]]) share: the exit codes, `--help`, the
  * option checks and the report or error of a run.
  */
object Cli:
  val FailureExitCode: Int = 1
  val BadOptionsExitCode: Int = 2

  /** Ends the JVM with `code`, unless it is 0. */
  def exit(code: Int): Unit = if code != 0 then sys.exit(code)

  /** Whether the command line that `arguments` reads asks for the usage, as scallop's `--help`
    * does.
    */
  def asksForHelp(arguments: => ScallopConf): Boolean =
    Try(arguments).failed.toOption.exists {
      case _: exceptions.Help => true
      case _ => false
    }

  /** Runs a command and returns its exit code: the `usage` if `helpAsked`, else `run` with the
    * `options`. Its report goes to stdout; options that are not valid, with the usage, and a
    * failed run go to stderr.
    */
  def execute[O](usage: String, helpAsked: Boolean, options: => Either[String, O])(
      run: O => Try[String]
  ): Int =
    if helpAsked then
      println(usage)
      0
    else
      options match
        case Left(message) =>
          Console.err.println(s"$message\n$usage")
          BadOptionsExitCode
        case Right(valid) =>
          run(valid) match
            case Success(report) =>
              println(report)
              0
            case Failure(error) =>
              Console.err.println(error.getMessage)
              FailureExitCode

  /** `value` if it is `valid`, else the `rule` it breaks. */
  def check(value: Int, valid: Int => Boolean, rule: String): Either[String, Int] =
    Either.cond(valid(value), value, s"$rule, not $value")

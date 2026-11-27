package go3d.engine

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.hashing.MurmurHash3

/** Running the games of a command at the same time, each from a seed of its own. */
object Parallel:
  /** The results of `f` for the `items`, in the order of the items, `parallelism` at a time: in
    * the calling thread when that is 1.
    */
  def map[A, B](items: Seq[A], parallelism: Int)(f: A => B): Seq[B] =
    if parallelism == 1 then items.map(f)
    else
      val pool = Executors.newFixedThreadPool(parallelism)
      try
        given ExecutionContext = ExecutionContext.fromExecutor(pool)
        Await.result(Future.traverse(items)(item => Future(f(item))), Duration.Inf)
      finally pool.shutdownNow()

  /** About this many progress lines are printed while [[games]] play. */
  private val ProgressLines = 20

  /** [[map]] for games: `play` for each game number, with a line `<count> of <total> games
    * played` each time about a twentieth of the games has finished.
    */
  def games[B](numbers: Seq[Int], parallelism: Int)(play: Int => B): Seq[B] =
    val finished = AtomicInteger()
    val printEvery = (numbers.length / ProgressLines).max(1)
    map(numbers, parallelism) { number =>
      val played = play(number)
      val count = finished.incrementAndGet
      if count % printEvery == 0 || count == numbers.length then
        println(s"$count of ${numbers.length} games played")
      played
    }

  /** A 64-bit seed from `key`, so that a game does not depend on the order in which the games
    * are played. Two hashes make 64 bits.
    */
  def seed(key: String): Long = (MurmurHash3.stringHash(key, 1).toLong << 32) |
    (MurmurHash3.stringHash(key, 2).toLong & 0xffffffffL)

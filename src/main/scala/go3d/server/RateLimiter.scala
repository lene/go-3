package go3d.server

import scala.collection.concurrent
import scala.util.{Failure, Success, Try}

/**
 * Per-IP rate limiter using a sliding window.
 *
 * Protects the /new and /register endpoints from being used to create
 * large numbers of games or registrations from a single source.
 */
object RateLimiter:

  val DefaultWindowSecs: Long = 60L
  val DefaultMaxRequests: Int = 10

  private val requestLog = concurrent.TrieMap[String, List[Long]]()

  def check(
    ip: String,
    maxRequests: Int = DefaultMaxRequests,
    windowSecs: Long = DefaultWindowSecs
  ): Try[Unit] =
    val now = System.currentTimeMillis()
    val cutoff = now - windowSecs * 1000L
    val recent = requestLog.getOrElse(ip, List.empty).filter(_ > cutoff)
    if recent.size >= maxRequests then
      Failure(RateLimitExceeded(ip, maxRequests, windowSecs))
    else
      requestLog(ip) = now :: recent
      Success(())

  private[server] def reset(): Unit = requestLog.clear()

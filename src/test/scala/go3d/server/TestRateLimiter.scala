package go3d.server

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestRateLimiter:

  // a limiter of its own: the shared RateLimiter is reset by other suites, possibly in parallel
  private val limiter = SlidingWindowRateLimiter()
  private val client = "client"

  @Test def testFirstRequestSucceeds(): Unit =
    Assertions.assertTrue(limiter.check(client, maxRequests = 5).isSuccess)

  @Test def testRequestsUpToLimitSucceed(): Unit =
    for _ <- 1 to 5 do
      Assertions.assertTrue(limiter.check(client, maxRequests = 5).isSuccess)

  @Test def testRequestBeyondLimitFails(): Unit =
    for _ <- 1 to 10 do limiter.check(client, maxRequests = 10)
    Assertions.assertTrue(limiter.check(client, maxRequests = 10).isFailure)

  @Test def testRateLimitIsPerIp(): Unit =
    for _ <- 1 to 10 do limiter.check(client, maxRequests = 10)
    // different IP should still succeed
    Assertions.assertTrue(limiter.check("other client", maxRequests = 10).isSuccess)

  @Test def testFailureIsRateLimitExceeded(): Unit =
    for _ <- 1 to 3 do limiter.check(client, maxRequests = 3)
    val result = limiter.check(client, maxRequests = 3)
    Assertions.assertInstanceOf(classOf[RateLimitExceeded], result.failure.exception)

  @Test def testWindowExpiryAllowsRequests(): Unit =
    for _ <- 1 to 3 do limiter.check(client, maxRequests = 3, windowSecs = 0)
    // windowSecs = 0 means all previous requests are outside the window
    Assertions.assertTrue(limiter.check(client, maxRequests = 3, windowSecs = 0).isSuccess)

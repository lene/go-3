package go3d.server

import org.junit.jupiter.api.{Assertions, BeforeEach, Test}

class TestRateLimiter:

  @BeforeEach def resetState(): Unit = RateLimiter.reset()

  @Test def testFirstRequestSucceeds(): Unit =
    Assertions.assertTrue(RateLimiter.check("1.2.3.4", maxRequests = 5).isSuccess)

  @Test def testRequestsUpToLimitSucceed(): Unit =
    for _ <- 1 to 5 do
      Assertions.assertTrue(RateLimiter.check("1.2.3.5", maxRequests = 5).isSuccess)

  @Test def testRequestBeyondLimitFails(): Unit =
    for _ <- 1 to 10 do RateLimiter.check("1.2.3.6", maxRequests = 10)
    Assertions.assertTrue(RateLimiter.check("1.2.3.6", maxRequests = 10).isFailure)

  @Test def testRateLimitIsPerIp(): Unit =
    for _ <- 1 to 10 do RateLimiter.check("1.2.3.7", maxRequests = 10)
    // different IP should still succeed
    Assertions.assertTrue(RateLimiter.check("1.2.3.8", maxRequests = 10).isSuccess)

  @Test def testFailureIsRateLimitExceeded(): Unit =
    for _ <- 1 to 3 do RateLimiter.check("1.2.3.9", maxRequests = 3)
    val result = RateLimiter.check("1.2.3.9", maxRequests = 3)
    Assertions.assertInstanceOf(classOf[RateLimitExceeded], result.failed.get)

  @Test def testWindowExpiryAllowsRequests(): Unit =
    for _ <- 1 to 3 do RateLimiter.check("1.2.3.10", maxRequests = 3, windowSecs = 0)
    // windowSecs = 0 means all previous requests are outside the window
    Assertions.assertTrue(RateLimiter.check("1.2.3.10", maxRequests = 3, windowSecs = 0).isSuccess)

package go3d.server.service

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

/** Captures the log of a class, to check that a failure is logged. */
object LogCapture:
  /** The result of `action` and the messages that the logger of `loggerClass` logged at error
    * level while it ran. Other suites can log through the same logger meanwhile, so a caller looks
    * for its own message instead of counting them.
    */
  def errorsDuring[T](loggerClass: Class[?])(action: => T): (T, List[String]) =
    val logger = logbackLogger(loggerClass, attemptsLeft = 500)
    val appender = ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    try
      val result = action
      val errors = appender.list.asScala.toList.filter(_.getLevel == Level.ERROR)
      (result, errors.map(_.getFormattedMessage))
    finally
      val _ = logger.detachAppender(appender)

  /** The logback logger of `loggerClass`. While SLF4J is still initialising, which a suite running
    * in parallel can be doing, it hands out a substitute logger (this failed a test in #183), so
    * wait for the real one, for at most 5 seconds.
    */
  @tailrec
  private def logbackLogger(loggerClass: Class[?], attemptsLeft: Int): Logger =
    LoggerFactory.getLogger(loggerClass) match
      case logger: Logger => logger
      case _ if attemptsLeft > 0 =>
        Thread.sleep(10)
        logbackLogger(loggerClass, attemptsLeft - 1)
      case other => Assertions.fail(s"not a logback logger: $other")

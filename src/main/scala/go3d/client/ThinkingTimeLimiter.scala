package go3d.client

import go3d.Position

import scala.util.Random

/*
    Following lines are disabled, because when running tests instantiating a Logger outside of the
    Server thread leads to a race condition:
    - if two Loggers are instantiated at the same time in different threads, an error
        java.lang.ClassCastException: class org.slf4j.helpers.SubstituteLogger cannot be cast to
        class ch.qos.logback.classic.Logger (org.slf4j.helpers.SubstituteLogger and
        ch.qos.logback.classic.Logger are in unnamed module of loader
        sbt.internal.LayeredClassLoader @4acf1e69)
      results.
    - if either TestSetStrategy or TestServer finishes before the other is started, the test suite
      succeeds.
    I cannot find how to fix this, so I am disabling logging for now, by using the alternate
    declaration below.
import com.typesafe.scalalogging.Logger
val logger = Logger[ThinkingTimeLimiter]
*/
object logger:
  def info(msg: String): Unit = println(msg)

class ThinkingTimeLimiter(val maxThinkingTimeMs: Int):

  private var lastThinkingTimeMs: Int = 1
  private var numPreviousElements = 0

  def subsetToSatisfyMaxThinkingTime(possible: Seq[Position]): Seq[Position] =
    val baseSize = if numPreviousElements == 0 then possible.size else numPreviousElements
    numPreviousElements = numElementsToSatisfyMaxThinkingTime(possible, baseSize)
    Random.shuffle(possible).take(numPreviousElements)

  private def numElementsToSatisfyMaxThinkingTime(possible: Seq[Position], baseSize: Int) =
    if maxThinkingTimeMs <= 0
    then possible.size
    else
      val targetSize = (baseSize * maxThinkingTimeMs / lastThinkingTimeMs.max(1)).max(1).min(possible.size)
      logger.info(s"Previous thinking time: $lastThinkingTimeMs ms")
      logger.info(s"Choosing $targetSize / ${possible.size} to satisfy max thinking time of $maxThinkingTimeMs ms")
      targetSize

  def recordThinkingTime(choiceFunction: => Seq[Position]): Seq[Position] =
    val startTime = System.currentTimeMillis()
    val possiblePositions = choiceFunction
    lastThinkingTimeMs = (System.currentTimeMillis() - startTime).toInt
    possiblePositions

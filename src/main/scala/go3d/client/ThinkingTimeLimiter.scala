package go3d.client

import com.typesafe.scalalogging.LazyLogging
import scala.util.Random

import go3d.Position

class ThinkingTimeLimiter(val maxThinkingTimeMs: Int) extends LazyLogging:

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

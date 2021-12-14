package go3d.server

import scala.annotation.tailrec
import scala.util.Random

object IdGenerator:
  val IdLength = 6
  def getId: String = getBase62(IdLength)

  @tailrec def getBase62(length: Int, str: String = ""): String =
    if length <= 0 then str else getBase62(length-1, str+base62(random.nextInt(62)))

  private val base62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray
  private val random = new Random

package server

import scala.util.Random

object IdGenerator:
  val IdLength = 6
  def getId: String = getBase62(IdLength)

  def getBase62(length: Int, str: String = ""): String =
    if length <= 0 then return str
    return getBase62(length-1, str+base62chars(random.nextInt(62)))

  private val base62chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray
  private val random = new Random

package go3d.server

import go3d.Color

import java.security.SecureRandom
import scala.annotation.tailrec

object IdGenerator:
  private val IdLength = 6
  private val TokenLength = 10

  def getId: String = getBase62(IdLength)
  def generateAuthToken(gameId: String, color: Color): String = getBase62(TokenLength)
  def isValidId(id: String): Boolean = id.length == IdLength && id.forall(base62.contains)
  def isValidToken(token: String): Boolean =
    token.length == TokenLength && token.forall(base62.contains)

  @tailrec private def getBase62(length: Int, str: String = ""): String =
    if length <= 0 then str else getBase62(length-1, str+base62(random.nextInt(62)))

  private val base62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray
  assert(base62.length == 62)
  private val random = new SecureRandom()

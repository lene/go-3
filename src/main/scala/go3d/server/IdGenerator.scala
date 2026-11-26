package go3d.server

import java.security.SecureRandom
import scala.annotation.tailrec

object IdGenerator:
  /** The digits of base 62, of which game ids and auth tokens are made. */
  private val Digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
  assert(Digits.length == 62)
  private val Base = Digits.length

  /** The number of base 62 digits of a game id. */
  val IdLength: Int = 6
  private val TokenLength = 10

  /** How many game ids there are: 62 to the power of [[IdLength]]. */
  val IdCount: Long = BigInt(Base).pow(IdLength).toLong

  def getId: String = getBase62(IdLength)
  def generateAuthToken: String = getBase62(TokenLength)
  def isValidId(id: String): Boolean = id.length == IdLength && id.forall(Digits.contains)
  def isValidToken(token: String): Boolean =
    token.length == TokenLength && token.forall(Digits.contains)

  /** The game id of `number`: the last [[IdLength]] base 62 digits of `number` modulo
    * [[IdCount]], with leading zeros.
    */
  def base62Id(number: Long): String =
    @tailrec
    def digits(rest: Long, count: Int, acc: String): String =
      if count == IdLength then acc
      else
        val digit = Digits(Math.floorMod(rest, Base))
        digits(Math.floorDiv(rest, Base), count + 1, s"$digit$acc")
    digits(number, 0, "")

  @tailrec private def getBase62(length: Int, str: String = ""): String =
    if length <= 0 then str else getBase62(length - 1, str + Digits(random.nextInt(Base)).toString)

  private val random = new SecureRandom()

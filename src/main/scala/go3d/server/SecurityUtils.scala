package go3d.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

object SecurityUtils:

  /**
   * Computes SHA-256 hash of the input string.
   * Used for secure token hashing before storage/logging.
   *
   * @param input The string to hash
   * @return Hex-encoded SHA-256 hash (64 characters)
   */
  def sha256(input: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8))
    hashBytes.map("%02x".format(_)).mkString

  /**
   * Returns the first N characters of a SHA-256 hash for logging/debugging.
   * Safe to log as it doesn't reveal the original token.
   *
   * @param token The token to hash
   * @param length Number of characters to return (default: 8)
   * @return Truncated hash for logging (e.g., "a3f5b2c1")
   */
  def tokenHashPrefix(token: String, length: Int = 8): String =
    sha256(token).take(length)

  /**
   * Calculates expiration timestamp for tokens.
   *
   * @param hoursFromNow Number of hours until expiration (default: 24)
   * @return Unix timestamp (seconds since epoch)
   */
  def expirationTimestamp(hoursFromNow: Int = 24): Long =
    Instant.now().plusSeconds(hoursFromNow * 3600).getEpochSecond

  /**
   * Checks if a timestamp has expired.
   *
   * @param expiresAt Unix timestamp (seconds since epoch)
   * @return true if expired, false otherwise
   */
  def isExpired(expiresAt: Long): Boolean =
    Instant.now().getEpochSecond > expiresAt

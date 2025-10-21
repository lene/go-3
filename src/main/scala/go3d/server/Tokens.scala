package go3d.server

import com.typesafe.scalalogging.LazyLogging
import go3d.Color

import scala.collection.concurrent

/**
 * Manages authentication tokens for players.
 * Tokens expire after 24 hours and are automatically cleaned up.
 *
 * Thread-safety: Uses TrieMap for lock-free concurrent access.
 * Individual operations (put, get, remove) are atomic, but
 * compound operations (filter + remove) are not atomic across
 * the entire map. This is acceptable since token cleanup is
 * best-effort and doesn't require strong consistency.
 */
object Tokens extends LazyLogging:

  // Thread-safe map: token string -> AuthToken
  // TrieMap provides lock-free concurrent access for individual operations
  private val activeTokens = concurrent.TrieMap[String, AuthToken]()

  /**
   * Register a new authentication token.
   *
   * @param token The authentication token string
   * @param gameId The game this token grants access to
   * @param color The player color this token represents
   * @param hoursUntilExpiration Hours until token expires (default: 24)
   */
  def register(
    token: String,
    gameId: String,
    color: Color,
    hoursUntilExpiration: Int = 24
  ): Unit =
    cleanupExpiredTokens()
    val expiresAt = SecurityUtils.expirationTimestamp(hoursUntilExpiration)
    val authToken = AuthToken(token, gameId, color, expiresAt)
    activeTokens(token) = authToken
    logger.debug(s"Token registered: gameId=$gameId, color=$color, " +
      s"tokenHash=${SecurityUtils.tokenHashPrefix(token)}, expiresAt=$expiresAt")

  /**
   * Validate a token and return the associated AuthToken if valid.
   *
   * @param token The token to validate
   * @return Some(AuthToken) if valid and not expired, None otherwise
   */
  def validate(token: String): Option[AuthToken] =
    activeTokens.get(token).filter(authToken => !SecurityUtils.isExpired(authToken.expiresAt))

  /**
   * Check if a token is valid for a specific game and color.
   *
   * @param token The token to check
   * @param gameId The game ID
   * @param color The player color
   * @return true if token is valid for the given game and color
   */
  def isValid(token: String, gameId: String, color: Color): Boolean =
    validate(token) match
      case Some(authToken) =>
        authToken.gameId == gameId && authToken.color == color
      case None => false

  /**
   * Get the color associated with a token for a specific game.
   *
   * @param token The token
   * @param gameId The game ID
   * @return Some(Color) if token is valid for the game, None otherwise
   */
  def getColor(token: String, gameId: String): Option[Color] =
    validate(token).filter(_.gameId == gameId).map(_.color)

  /**
   * Remove all tokens associated with a game.
   *
   * @param gameId The game ID
   */
  def unregisterGame(gameId: String): Unit =
    val tokensToRemove = activeTokens.filter(_._2.gameId == gameId).keys.toSeq
    tokensToRemove.foreach(activeTokens.remove)
    if tokensToRemove.nonEmpty then
      logger.debug(s"Unregistered ${tokensToRemove.size} tokens for game $gameId")

  /**
   * Remove a specific token.
   *
   * @param token The token to remove
   */
  def unregister(token: String): Unit =
    activeTokens.remove(token)

  /**
   * Remove all expired tokens from the active tokens map.
   * Called automatically on register, but can be called manually for cleanup.
   *
   * @return Number of tokens cleaned up
   */
  def cleanupExpiredTokens(): Int =
    val before = activeTokens.size
    java.time.Instant.now().getEpochSecond
    activeTokens.filterInPlace((_, authToken) => !SecurityUtils.isExpired(authToken.expiresAt))
    val removed = before - activeTokens.size
    if removed > 0 then
      logger.info(s"Cleaned up $removed expired tokens")
    removed

  /**
   * Get all active tokens (for testing/debugging).
   *
   * @return Map of token string to AuthToken
   */
  def allTokens: Map[String, AuthToken] = activeTokens.toMap

  /**
   * Get count of active tokens.
   *
   * @return Number of active tokens
   */
  def size: Int = activeTokens.size

  /**
   * Clear all tokens (for testing).
   */
  def clear(): Unit = activeTokens.clear()

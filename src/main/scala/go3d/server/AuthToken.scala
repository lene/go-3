package go3d.server

import go3d.Color

/**
 * Represents an authentication token for a player in a game.
 * Tokens expire after a configurable time period (default 24 hours).
 *
 * @param token The authentication token string
 * @param gameId The game this token grants access to
 * @param color The player color this token represents
 * @param expiresAt Unix timestamp (seconds since epoch) when token expires
 */
case class AuthToken(
  token: String,
  gameId: String,
  color: Color,
  expiresAt: Long = SecurityUtils.expirationTimestamp()
)

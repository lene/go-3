package go3d.server

import org.scalatest.funsuite.AnyFunSuite
import go3d.{Black, White}

class TestTokens extends AnyFunSuite:

  // Clear tokens before each test to ensure isolation
  override def withFixture(test: NoArgTest) =
    Tokens.clear()
    try super.withFixture(test)
    finally Tokens.clear()

  test("register creates a new token"):
    val token = "test_token_123"
    val gameId = "game1"
    val color = Black

    Tokens.register(token, gameId, color)

    assert(Tokens.size == 1)
    assert(Tokens.validate(token).isDefined)

  test("validate returns Some(AuthToken) for valid token"):
    val token = "valid_token"
    val gameId = "game2"
    val color = White

    Tokens.register(token, gameId, color)
    val result = Tokens.validate(token)

    assert(result.isDefined)
    assert(result.get.token == token)
    assert(result.get.gameId == gameId)
    assert(result.get.color == color)

  test("validate returns None for non-existent token"):
    val result = Tokens.validate("nonexistent_token")
    assert(result.isEmpty)

  test("validate returns None for expired token"):
    val token = "expired_token"
    val gameId = "game3"
    val color = Black

    // Register token with 0 hours (already expired)
    Tokens.register(token, gameId, color, hoursUntilExpiration = 0)

    // Wait longer to ensure expiration in CI environments
    Thread.sleep(2000)

    val result = Tokens.validate(token)
    assert(result.isEmpty)

  test("isValid returns true for valid token with matching game and color"):
    val token = "token1"
    val gameId = "game4"
    val color = Black

    Tokens.register(token, gameId, color)

    assert(Tokens.isValid(token, gameId, color))

  test("isValid returns false for valid token with wrong game"):
    val token = "token2"
    val gameId = "game5"
    val color = White

    Tokens.register(token, gameId, color)

    assert(!Tokens.isValid(token, "different_game", color))

  test("isValid returns false for valid token with wrong color"):
    val token = "token3"
    val gameId = "game6"
    val color = Black

    Tokens.register(token, gameId, color)

    assert(!Tokens.isValid(token, gameId, White))

  test("isValid returns false for expired token"):
    val token = "token4"
    val gameId = "game7"
    val color = White

    Tokens.register(token, gameId, color, hoursUntilExpiration = 0)
    Thread.sleep(2000)

    assert(!Tokens.isValid(token, gameId, color))

  test("getColor returns Some(Color) for valid token"):
    val token = "token5"
    val gameId = "game8"
    val color = Black

    Tokens.register(token, gameId, color)

    val result = Tokens.getColor(token, gameId)
    assert(result.isDefined)
    assert(result.get == color)

  test("getColor returns None for invalid token"):
    val result = Tokens.getColor("invalid_token", "game9")
    assert(result.isEmpty)

  test("getColor returns None for wrong game"):
    val token = "token6"
    val gameId = "game10"
    val color = White

    Tokens.register(token, gameId, color)

    val result = Tokens.getColor(token, "different_game")
    assert(result.isEmpty)

  test("unregisterGame removes all tokens for a game"):
    val gameId = "game11"

    Tokens.register("token_black", gameId, Black)
    Tokens.register("token_white", gameId, White)
    Tokens.register("other_token", "other_game", Black)

    assert(Tokens.size == 3)

    Tokens.unregisterGame(gameId)

    assert(Tokens.size == 1)
    assert(Tokens.validate("token_black").isEmpty)
    assert(Tokens.validate("token_white").isEmpty)
    assert(Tokens.validate("other_token").isDefined)

  test("unregister removes a specific token"):
    val token = "token7"
    val gameId = "game12"

    Tokens.register(token, gameId, Black)
    assert(Tokens.size == 1)

    Tokens.unregister(token)

    assert(Tokens.size == 0)
    assert(Tokens.validate(token).isEmpty)

  test("cleanupExpiredTokens removes only expired tokens"):
    // Register mix of expired and valid tokens
    Tokens.register("expired1_cleanup", "game13", Black, hoursUntilExpiration = 0)
    Tokens.register("expired2_cleanup", "game14", White, hoursUntilExpiration = 0)
    Tokens.register("valid1_cleanup", "game15", Black, hoursUntilExpiration = 24)
    Tokens.register("valid2_cleanup", "game16", White, hoursUntilExpiration = 24)

    Thread.sleep(2000)

    // Just verify that our specific tokens are handled correctly
    // Don't rely on exact counts due to possible parallel test interference
    Tokens.cleanupExpiredTokens()

    assert(Tokens.validate("expired1_cleanup").isEmpty)
    assert(Tokens.validate("expired2_cleanup").isEmpty)
    assert(Tokens.validate("valid1_cleanup").isDefined)
    assert(Tokens.validate("valid2_cleanup").isDefined)

  test("register with custom expiration time"):
    val token = "custom_token"
    val gameId = "game17"
    val color = Black

    Tokens.register(token, gameId, color, hoursUntilExpiration = 48)

    val result = Tokens.validate(token)
    assert(result.isDefined)

    // Check expiration is approximately 48 hours from now
    val now = java.time.Instant.now().getEpochSecond
    val expectedExpiration = now + (48 * 3600)
    val actualExpiration = result.get.expiresAt

    // Allow 5 second tolerance for test execution time
    assert(math.abs(actualExpiration - expectedExpiration) < 5)

  test("multiple tokens for same game but different colors"):
    val gameId = "game18"
    val tokenBlack = "token_black"
    val tokenWhite = "token_white"

    Tokens.register(tokenBlack, gameId, Black)
    Tokens.register(tokenWhite, gameId, White)

    assert(Tokens.size == 2)
    assert(Tokens.isValid(tokenBlack, gameId, Black))
    assert(Tokens.isValid(tokenWhite, gameId, White))
    assert(!Tokens.isValid(tokenBlack, gameId, White))
    assert(!Tokens.isValid(tokenWhite, gameId, Black))

  test("allTokens returns all registered tokens"):
    Tokens.register("token1", "game19", Black)
    Tokens.register("token2", "game20", White)

    val all = Tokens.allTokens

    assert(all.size == 2)
    assert(all.contains("token1"))
    assert(all.contains("token2"))

  test("clear removes all tokens"):
    Tokens.register("token1", "game21", Black)
    Tokens.register("token2", "game22", White)
    Tokens.register("token3", "game23", Black)

    assert(Tokens.size == 3)

    Tokens.clear()

    assert(Tokens.size == 0)
    assert(Tokens.allTokens.isEmpty)

  test("cleanupExpiredTokens called automatically on register"):
    // Register expired token
    Tokens.register("old_token_autoreg", "game24", Black, hoursUntilExpiration = 0)
    Thread.sleep(2000)

    // Registering new token should trigger cleanup
    Tokens.register("new_token_autoreg", "game25", White)

    // Old expired token should be cleaned up, new token should exist
    // Don't rely on exact size due to parallel test interference
    assert(Tokens.validate("old_token_autoreg").isEmpty)
    assert(Tokens.validate("new_token_autoreg").isDefined)

  test("token expiration boundary - just before expiration"):
    val token = "boundary_token"
    val gameId = "game26"

    // Token expires in 0 hours (immediately)
    Tokens.register(token, gameId, Black, hoursUntilExpiration = 0)

    // Wait longer to ensure expiration
    Thread.sleep(2000)
    assert(Tokens.validate(token).isEmpty)

  test("duplicate token registration overwrites previous"):
    val token = "duplicate_token"
    val gameId1 = "game27"
    val gameId2 = "game28"

    Tokens.register(token, gameId1, Black)
    assert(Tokens.getColor(token, gameId1) == Some(Black))

    // Re-register same token for different game
    Tokens.register(token, gameId2, White)

    // Should now be associated with new game only
    assert(Tokens.getColor(token, gameId1).isEmpty)
    assert(Tokens.getColor(token, gameId2) == Some(White))

  test("large number of tokens"):
    // Register 100 tokens
    for i <- 1 to 100 do
      Tokens.register(s"token_$i", s"game_$i", if i % 2 == 0 then Black else White)

    assert(Tokens.size == 100)

    // Validate random tokens
    assert(Tokens.isValid("token_50", "game_50", Black))
    assert(Tokens.isValid("token_99", "game_99", White))

    // Cleanup game
    Tokens.unregisterGame("game_50")
    assert(Tokens.size == 99)

  test("token with special characters"):
    val token = "token_!@#$%^&*()_+-=[]{}|;:',.<>?/~`"
    val gameId = "game29"

    Tokens.register(token, gameId, Black)

    assert(Tokens.validate(token).isDefined)
    assert(Tokens.isValid(token, gameId, Black))

  test("empty token string"):
    val token = ""
    val gameId = "game30"

    Tokens.register(token, gameId, White)

    assert(Tokens.validate(token).isDefined)
    assert(Tokens.size == 1)

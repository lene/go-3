package go3d.server

import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant

class TestSecurityUtils extends AnyFunSuite:

  test("sha256 produces 64-character hex string"):
    val input = "test_token_12345"
    val hash = SecurityUtils.sha256(input)

    assert(hash.length == 64, s"SHA-256 hash should be 64 characters, got ${hash.length}")
    assert(hash.matches("[0-9a-f]{64}"), "SHA-256 hash should only contain lowercase hex characters")

  test("sha256 is deterministic"):
    val input = "same_token"
    val hash1 = SecurityUtils.sha256(input)
    val hash2 = SecurityUtils.sha256(input)

    assert(hash1 == hash2, "Same input should produce same hash")

  test("sha256 produces different hashes for different inputs"):
    val hash1 = SecurityUtils.sha256("token1")
    val hash2 = SecurityUtils.sha256("token2")

    assert(hash1 != hash2, "Different inputs should produce different hashes")

  test("sha256 is sensitive to case"):
    val hashLower = SecurityUtils.sha256("token")
    val hashUpper = SecurityUtils.sha256("TOKEN")

    assert(hashLower != hashUpper, "Hash should be case-sensitive")

  test("sha256 known value verification"):
    // SHA-256 of "test" is a well-known hash
    val hash = SecurityUtils.sha256("test")
    val expected = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    assert(hash == expected, s"SHA-256 of 'test' should match known value")

  test("tokenHashPrefix returns correct length"):
    val token = "mySecretToken123"
    val prefix = SecurityUtils.tokenHashPrefix(token, 8)

    assert(prefix.length == 8, "tokenHashPrefix should return 8 characters by default")

  test("tokenHashPrefix returns first N characters of hash"):
    val token = "token"
    val fullHash = SecurityUtils.sha256(token)
    val prefix = SecurityUtils.tokenHashPrefix(token, 10)

    assert(prefix == fullHash.take(10), "tokenHashPrefix should return first N chars of hash")

  test("tokenHashPrefix default length is 8"):
    val token = "defaultTest"
    val prefix = SecurityUtils.tokenHashPrefix(token)

    assert(prefix.length == 8, "Default tokenHashPrefix length should be 8")

  test("tokenHashPrefix is safe for logging (no token recovery possible)"):
    val token = "verySecretToken123"
    val prefix = SecurityUtils.tokenHashPrefix(token)

    // Hash prefix should be 8 chars, making brute force impractical
    assert(prefix.length == 8)
    assert(!prefix.contains(token), "Prefix should not contain original token")

  test("expirationTimestamp returns future timestamp"):
    val now = Instant.now().getEpochSecond
    val expiresAt = SecurityUtils.expirationTimestamp(24)

    assert(expiresAt > now, "Expiration timestamp should be in the future")

  test("expirationTimestamp calculates correct hours"):
    val before = Instant.now().getEpochSecond
    val expiresAt = SecurityUtils.expirationTimestamp(1) // 1 hour
    val after = Instant.now().getEpochSecond

    val expectedMin = before + 3600 // 1 hour in seconds
    val expectedMax = after + 3600

    assert(expiresAt >= expectedMin && expiresAt <= expectedMax,
      s"Expiration should be ~1 hour from now")

  test("expirationTimestamp defaults to 24 hours"):
    val before = Instant.now().getEpochSecond
    val expiresAt = SecurityUtils.expirationTimestamp()
    val after = Instant.now().getEpochSecond

    val expectedMin = before + (24 * 3600)
    val expectedMax = after + (24 * 3600)

    assert(expiresAt >= expectedMin && expiresAt <= expectedMax,
      s"Default expiration should be ~24 hours from now")

  test("isExpired returns true for past timestamp"):
    val pastTimestamp = Instant.now().minusSeconds(3600).getEpochSecond // 1 hour ago

    assert(SecurityUtils.isExpired(pastTimestamp), "Past timestamp should be expired")

  test("isExpired returns false for future timestamp"):
    val futureTimestamp = Instant.now().plusSeconds(3600).getEpochSecond // 1 hour from now

    assert(!SecurityUtils.isExpired(futureTimestamp), "Future timestamp should not be expired")

  test("isExpired handles edge case of current second"):
    val now = Instant.now().getEpochSecond

    // This might be true or false depending on timing, but shouldn't crash
    SecurityUtils.isExpired(now)

  test("token hashing prevents log injection attacks"):
    val maliciousToken = "token\nINFO Fake log message"
    val hash = SecurityUtils.sha256(maliciousToken)

    assert(!hash.contains("\n"), "Hash should not contain newlines")
    assert(!hash.contains("INFO"), "Hash should not contain log keywords")

  test("empty string hashing"):
    val hash = SecurityUtils.sha256("")
    val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    assert(hash == expected, "Empty string should hash to known value")

  test("unicode token hashing"):
    val unicodeToken = "token_用户_🔒"
    val hash = SecurityUtils.sha256(unicodeToken)

    assert(hash.length == 64, "Unicode tokens should hash correctly")
    assert(hash.matches("[0-9a-f]{64}"), "Unicode hash should still be valid hex")

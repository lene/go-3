package go3d.server.aws

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach

/**
 * Unit tests for S3 integration layer.
 *
 * All tests run without AWS credentials — they verify configuration
 * and no-op behaviour only.
 *
 * Integration tests against a real S3 bucket require AWS credentials
 * and should be run manually after running infra/setup-s3.sh.
 */
class TestS3 extends AnyFunSuite with BeforeAndAfterEach:

  override def beforeEach(): Unit = S3Client.close()
  override def afterEach(): Unit = S3Client.close()

  // --- S3Config ---

  test("S3Config.fromEnv returns None when AWS_REGION not set") {
    if sys.env.contains("AWS_REGION") || sys.env.contains("AWS_DEFAULT_REGION") then
      cancel("AWS_REGION is set in this environment")
    assert(S3Config.fromEnv().isEmpty)
  }

  test("S3Config default bucket name is correct") {
    assert(S3Config.DefaultBucket == "go3d-game-archives")
  }

  test("S3Config.s3Key produces archives/gameId.json") {
    assert(S3Config.s3Key("abc123") == "archives/abc123.json")
  }

  test("S3Config archive prefix is 'archives'") {
    assert(S3Config.ArchivePrefix == "archives")
  }

  // --- S3Client ---

  test("S3Client.get returns None when not configured") {
    if sys.env.contains("AWS_REGION") || sys.env.contains("AWS_DEFAULT_REGION") then
      cancel("AWS_REGION is set in this environment")
    assert(S3Client.get().isEmpty)
  }

  test("S3Client.close is idempotent") {
    S3Client.close()
    S3Client.close()
  }

  test("S3Client.archiveGame is no-op when S3 not configured") {
    if sys.env.contains("AWS_REGION") || sys.env.contains("AWS_DEFAULT_REGION") then
      cancel("AWS_REGION is set in this environment")
    S3Client.archiveGame("testGame", """{"game":"data"}""")
  }

  test("S3Client.generatePresignedUrl returns None when S3 not configured") {
    if sys.env.contains("AWS_REGION") || sys.env.contains("AWS_DEFAULT_REGION") then
      cancel("AWS_REGION is set in this environment")
    assert(S3Client.generatePresignedUrl("testGame").isEmpty)
  }

  test("S3Client pre-signed URL expiry is 5 minutes") {
    assert(S3Client.PresignedUrlExpiryMinutes == 5)
  }

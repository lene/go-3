package go3d.server

import go3d.{BadKomi, Komi}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.util.Try

class TestKomiQuery:

  private def assertBadKomi(result: Try[Double], description: String): Unit =
    Assertions.assertInstanceOf(classOf[BadKomi], result.failure.exception, description)

  @Test def testParameterIsKomi(): Unit =
    Assertions.assertEquals("komi", KomiQuery.Parameter)

  @Test def testAbsentParameterGivesDefault(): Unit =
    Assertions.assertEquals(Komi.Default, KomiQuery.fromValues(None, 5).success.value)

  @Test def testAbsentParameterGivesDefaultEvenForUnvalidatedSize(): Unit =
    Assertions.assertEquals(Komi.Default, KomiQuery.fromValues(None, 0).success.value)

  @Test def testOneValueIsParsed(): Unit =
    Assertions.assertEquals(6.5, KomiQuery.fromValues(Some(Seq("6.5")), 5).success.value)
    assertBadKomi(KomiQuery.fromValues(Some(Seq("7")), 5), "an integer")

  @Test def testBareParameterIsRejected(): Unit =
    assertBadKomi(KomiQuery.fromValues(Some(Seq()), 5), "no value, as in http4s")
    assertBadKomi(KomiQuery.fromValues(Some(Seq("")), 5), "empty value, as in the Lambda")

  @Test def testRepeatedParameterIsRejected(): Unit =
    assertBadKomi(KomiQuery.fromValues(Some(Seq("6.5", "6.5")), 5), "twice the same")
    assertBadKomi(KomiQuery.fromValues(Some(Seq("6.5", "7.5")), 5), "twice different")
    assertBadKomi(KomiQuery.fromValues(Some(Seq("6.5", "abc")), 5), "good and bad")

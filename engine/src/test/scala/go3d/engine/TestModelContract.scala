package go3d.engine

import go3d.client.MoveEngineError
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestModelContract:

  private val metadata = Map(
    "contract_version" -> "1",
    "board_size" -> "5",
    "generation" -> "0",
    "scoring_rule" -> "area",
    "komi" -> "4.5",
    "input_planes" -> "5",
    "actions" -> "126"
  )

  private def failure(metadata: Map[String, String], size: Int): String =
    val error = ModelContract.check(metadata, size).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
    error.getMessage

  @Test def testMatchingModelGivesItsKomi(): Unit =
    Assertions.assertEquals(4.5, ModelContract.check(metadata, 5).success.value)

  @Test def testOtherSizeNamesEveryMismatch(): Unit =
    Assertions.assertEquals(
      "board_size is 5, expected 7, actions is 126, expected 344",
      failure(metadata, 7)
    )

  @Test def testEveryContractValueIsChecked(): Unit =
    ModelContract.expected(5).foreach { (key, _) =>
      Assertions.assertTrue(failure(metadata.updated(key, "x"), 5).startsWith(s"$key is x"))
    }

  @Test def testScoringRuleIsArea(): Unit =
    Assertions.assertEquals(
      "scoring_rule is area+captures, expected area",
      failure(metadata.updated("scoring_rule", "area+captures"), 5)
    )

  @Test def testMissingValueIsNamed(): Unit =
    Assertions.assertEquals(
      "contract_version is missing, expected 1",
      failure(metadata.removed("contract_version"), 5)
    )

  @Test def testKomiMustBeANumber(): Unit =
    Assertions.assertTrue(failure(metadata.updated("komi", "x"), 5).startsWith("komi"))

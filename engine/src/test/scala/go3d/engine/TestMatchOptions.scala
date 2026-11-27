package go3d.engine

import org.junit.jupiter.api.{Assertions, Test}

class TestMatchOptions:

  private val required = Seq("--size", "5", "--generation", "1")

  private def failure(args: Seq[String]): String =
    MatchOptions.parse(args).fold(identity, options => Assertions.fail(options.toString))

  @Test def testDefaults(): Unit =
    Assertions.assertEquals(
      Right(MatchOptions(5, 1, 100, 200, 55, 1L, 1, "models", Device.Cuda)),
      MatchOptions.parse(required)
    )

  @Test def testEveryOption(): Unit =
    val args = required ++ Seq("--games", "10", "--simulations", "50", "--threshold", "6") ++
      Seq("--seed", "-7", "--parallel", "4", "--model-store", "m", "--device", "cpu")
    Assertions.assertEquals(
      Right(MatchOptions(5, 1, 10, 50, 6, -7L, 4, "m", Device.Cpu)),
      MatchOptions.parse(args)
    )

  @Test def testOptionsOfThePromotionLoop(): Unit =
    // as training/go3d_training/loop.py `command` builds them for a MatchStep: keep in sync
    val loop = "--size 5 --generation 1 --games 100 --threshold 55 --seed 3 --simulations 200 " +
      "--parallel 1 --model-store /store/models --device cuda"
    Assertions.assertEquals(
      Right(MatchOptions(5, 1, 100, 200, 55, 3L, 1, "/store/models", Device.Cuda)),
      MatchOptions.parse(loop.split(" ").toSeq)
    )

  @Test def testOpponentIsTheGenerationBefore(): Unit =
    Assertions.assertEquals(
      Right(2),
      MatchOptions.parse(Seq("--size", "5", "--generation", "3"))
        .map(_.opponent)
    )

  @Test def testGenerationZeroHasNoOpponent(): Unit =
    val message = failure(Seq("--size", "5", "--generation", "0"))
    Assertions.assertTrue(message.contains("generation"), message)

  @Test def testOddGamesWouldGiveOneSideMoreGamesAsBlack(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--games", "9")).contains("even"))
    Assertions.assertTrue(failure(required ++ Seq("--games", "0")).contains("games"))

  @Test def testThresholdMustLeaveAWayToBePromoted(): Unit =
    val all = failure(required ++ Seq("--games", "10", "--threshold", "10"))
    Assertions.assertTrue(all.contains("threshold"), all)
    Assertions.assertTrue(failure(required ++ Seq("--threshold", "-1")).contains("threshold"))
    // the default threshold needs more games than these
    Assertions.assertTrue(failure(required ++ Seq("--games", "10")).contains("threshold"))

  @Test def testSizeThatTheMatchDoesNotPlayYet(): Unit =
    Assertions.assertTrue(failure(Seq("--size", "7", "--generation", "1")).contains("size"))

  @Test def testNoSimulationsOrParallelGames(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--simulations", "0")).contains("simulations"))
    Assertions.assertTrue(failure(required ++ Seq("--parallel", "0")).contains("parallel"))

  @Test def testUnknownDeviceOrOption(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--device", "tpu")).contains("tpu"))
    Assertions.assertTrue(failure(required ++ Seq("--komi", "4.5")).contains("komi"))

  @Test def testHelp(): Unit =
    Assertions.assertTrue(MatchOptions.asksForHelp(Seq("--help")))
    Assertions.assertFalse(MatchOptions.asksForHelp(required))

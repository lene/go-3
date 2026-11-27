package go3d.engine

import org.junit.jupiter.api.{Assertions, Test}

class TestSelfPlayOptions:

  private val required = Seq("--size", "5", "--generation", "0", "--games", "4")

  private def failure(args: Seq[String]): String =
    SelfPlayOptions.parse(args).fold(identity, options => Assertions.fail(options.toString))

  @Test def testDefaults(): Unit =
    Assertions.assertEquals(
      Right(SelfPlayOptions(5, 0, 4, 200, 1L, 1, "models", "selfplay", Device.Cuda, false)),
      SelfPlayOptions.parse(required)
    )

  @Test def testEveryOption(): Unit =
    val args = Seq("--size", "5", "--generation", "3", "--games", "10", "--simulations", "50") ++
      Seq(
        "--seed",
        "-7",
        "--parallel",
        "4",
        "--model-store",
        "m",
        "--out",
        "o",
        "--device",
        "cpu"
      ) ++
      Seq("--resume")
    Assertions.assertEquals(
      Right(SelfPlayOptions(5, 3, 10, 50, -7L, 4, "m", "o", Device.Cpu, true)),
      SelfPlayOptions.parse(args)
    )

  @Test def testOptionsOfThePromotionLoop(): Unit =
    // as training/go3d_training/loop.py `command` builds them for a SelfPlayStep: keep in sync
    val loop = "--size 5 --generation 0 --games 200 --seed 2 --simulations 200 --parallel 1 " +
      "--model-store /store/models --device cuda --out /store/selfplay --resume"
    Assertions.assertEquals(
      Right(SelfPlayOptions(
        5,
        0,
        200,
        200,
        2L,
        1,
        "/store/models",
        "/store/selfplay",
        Device.Cuda,
        true
      )),
      SelfPlayOptions.parse(loop.split(" ").toSeq)
    )

  @Test def testNoGames(): Unit =
    val message = failure(Seq("--size", "5", "--generation", "0", "--games", "0"))
    Assertions.assertTrue(message.contains("games"), message)

  @Test def testEvenSize(): Unit =
    val message = failure(Seq("--size", "4", "--generation", "0", "--games", "1"))
    Assertions.assertTrue(message.contains("size"), message)

  @Test def testSizeThatSelfPlayDoesNotPlayYet(): Unit =
    val message = failure(Seq("--size", "7", "--generation", "0", "--games", "1"))
    Assertions.assertTrue(message.contains("size"), message)

  @Test def testNoGeneration(): Unit =
    val message = failure(Seq("--size", "5", "--games", "1"))
    Assertions.assertTrue(message.contains("generation"), message)

  @Test def testNegativeGeneration(): Unit =
    val message = failure(Seq("--size", "5", "--generation", "-1", "--games", "1"))
    Assertions.assertTrue(message.contains("generation"), message)

  @Test def testNoSimulations(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--simulations", "0")).contains("simulations"))

  @Test def testNoParallelGames(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--parallel", "0")).contains("parallel"))

  @Test def testUnknownDevice(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--device", "tpu")).contains("tpu"))

  @Test def testDeviceNamesAreTheirCommandLineNames(): Unit =
    Assertions.assertEquals(List("cpu", "cuda"), Device.values.toList.map(_.name))
    Assertions.assertEquals(Some(Device.Cuda), Device.named("CUDA"))

  @Test def testUnknownOption(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--komi", "4.5")).contains("komi"))

  @Test def testNotANumber(): Unit =
    Assertions.assertTrue(failure(required ++ Seq("--seed", "x")).contains("seed"))

  @Test def testHelp(): Unit =
    Assertions.assertTrue(SelfPlayOptions.asksForHelp(Seq("--help")))
    Assertions.assertFalse(SelfPlayOptions.asksForHelp(required))
    Assertions.assertFalse(SelfPlayOptions.asksForHelp(Seq("--games", "0")))

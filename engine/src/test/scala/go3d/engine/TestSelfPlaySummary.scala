package go3d.engine

import org.junit.jupiter.api.{Assertions, Test}

class TestSelfPlaySummary:

  private val summary = SelfPlaySummary(
    Seq(GameSummary(10, true, 5000, 2000), GameSummary(20, false, 7000, 3000)),
    4,
    1_000_000_000L
  )
  private val options = SelfPlayOptions(5, 0, 2, 4, 1, 1, "models", "out", Device.Cpu)
  private val tolerance = 1e-12

  @Test def testCountsTheMovesAndBlackWins(): Unit =
    Assertions.assertEquals(30, summary.moves)
    Assertions.assertEquals(1, summary.blackWins)

  @Test def testTimesArePerSimulation(): Unit =
    // 30 moves of 4 simulations
    Assertions.assertEquals(
      (5000 - 2000 + 7000 - 3000) / 1000.0 / 120,
      summary.engineMicros,
      tolerance
    )
    Assertions.assertEquals((2000 + 3000) / 1000.0 / 120, summary.networkMicros, tolerance)

  @Test def testReport(): Unit =
    val lines = summary.report(options, Device.Cpu).linesIterator.toList
    Assertions.assertEquals(
      List(
        "size 5, generation 0, 4 simulations on cpu: 2 games, 30 moves in 1.0 s",
        s"records in ${SelfPlay.recordDir(options)}",
        "time per simulation, summed over the games played at the same time: network 0.0 µs, " +
          "engine 0.1 µs",
        "Black wins 1 of 2 games (50.0%)"
      ),
      lines
    )

package go3d.server

import org.junit.jupiter.api.{Assertions, Test}
import java.util.concurrent.{CountDownLatch, Executors}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._

class TestConcurrentState:

  @Test def testGetReturnsInitialValue(): Unit =
    val state = new ConcurrentState(42)
    Assertions.assertEquals(42, state.get())

  @Test def testSetUpdatesValue(): Unit =
    val state = new ConcurrentState(42)
    state.set(100)
    Assertions.assertEquals(100, state.get())

  @Test def testUpdateAtomicallyModifiesValue(): Unit =
    val state = new ConcurrentState(0)
    val newValue = state.update(_ + 1)
    Assertions.assertEquals(1, newValue)
    Assertions.assertEquals(1, state.get())

  @Test def testUpdateReturnsNewValue(): Unit =
    val state = new ConcurrentState("hello")
    val result = state.update(_ + " world")
    Assertions.assertEquals("hello world", result)
    Assertions.assertEquals("hello world", state.get())

  @Test def testConcurrentUpdatesAllSucceed(): Unit =
    val state = new ConcurrentState(0)
    val numThreads = 100
    val executor = Executors.newFixedThreadPool(numThreads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    try
      val futures = (1 to numThreads).map { _ =>
        Future {
          state.update(_ + 1)
        }
      }

      Await.result(Future.sequence(futures), 5.seconds)
      Assertions.assertEquals(numThreads, state.get())
    finally
      executor.shutdown()

  @Test def testConcurrentUpdatesWithContention(): Unit =
    // This test simulates high contention by having all threads
    // try to update at exactly the same time
    val state = new ConcurrentState(0)
    val numThreads = 50
    val latch = new CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(numThreads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    try
      val futures = (1 to numThreads).map { _ =>
        Future {
          latch.await() // All threads wait here
          state.update(_ + 1) // Then all update simultaneously
        }
      }

      latch.countDown() // Release all threads at once
      Await.result(Future.sequence(futures), 5.seconds)
      Assertions.assertEquals(numThreads, state.get())
    finally
      executor.shutdown()

  @Test def testUpdateWithComplexTransformation(): Unit =
    case class Counter(value: Int, history: List[Int])
    val state = new ConcurrentState(Counter(0, Nil))

    val newState = state.update { c =>
      Counter(c.value + 1, c.value :: c.history)
    }

    Assertions.assertEquals(1, newState.value)
    Assertions.assertEquals(List(0), newState.history)

  @Test def testConcurrentUpdatesPreserveAllIncrements(): Unit =
    // This test verifies that no updates are lost even under contention
    val state = new ConcurrentState(0)
    val numThreads = 100
    val incrementsPerThread = 10
    val executor = Executors.newFixedThreadPool(numThreads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    try
      val futures = (1 to numThreads).map { _ =>
        Future {
          (1 to incrementsPerThread).foreach { _ =>
            state.update(_ + 1)
          }
        }
      }

      Await.result(Future.sequence(futures), 10.seconds)
      Assertions.assertEquals(numThreads * incrementsPerThread, state.get())
    finally
      executor.shutdown()

  @Test def testUpdateWithExceptionDoesNotModifyState(): Unit =
    val state = new ConcurrentState(42)

    Assertions.assertThrows(
      classOf[IllegalStateException],
      () => state.update { _ =>
        throw new IllegalStateException("Test exception")
      }
    )

    Assertions.assertEquals(42, state.get())

  @Test def testMultipleUpdatesInSequence(): Unit =
    val state = new ConcurrentState(1)
    val result1 = state.update(_ * 2) // 2
    val result2 = state.update(_ + 3) // 5
    val result3 = state.update(_ * 10) // 50

    Assertions.assertEquals(2, result1)
    Assertions.assertEquals(5, result2)
    Assertions.assertEquals(50, result3)
    Assertions.assertEquals(50, state.get())

  @Test def testUpdateWithIdentityFunction(): Unit =
    val state = new ConcurrentState(42)
    val result = state.update(identity)
    Assertions.assertEquals(42, result)
    Assertions.assertEquals(42, state.get())

  @Test def testConcurrentReadsNeverBlock(): Unit =
    val state = new ConcurrentState(0)
    val numReaders = 1000
    val executor = Executors.newFixedThreadPool(100)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

    try
      // Start one writer thread
      val writerFuture = Future {
        (1 to 1000).foreach { i =>
          state.set(i)
          Thread.sleep(1)
        }
      }

      // Start many concurrent readers
      val readerFutures = (1 to numReaders).map { _ =>
        Future {
          (1 to 10).foreach { _ =>
            state.get() // Should never block
          }
        }
      }

      Await.result(Future.sequence(readerFutures :+ writerFuture), 15.seconds)
      // Test passes if it completes without timeout
    finally
      executor.shutdown()

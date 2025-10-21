package go3d.server

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * Thread-safe container for lock-free optimistic updates.
 *
 * Uses Compare-And-Set (CAS) to atomically update state without locks.
 * If concurrent updates occur, the CAS fails and retries automatically.
 *
 * Why AtomicReference instead of synchronized:
 * - Lock-free: No thread blocking, better scalability
 * - Wait-free reads: Readers never block or retry
 * - Optimistic: Assumes low contention (true for turn-based games)
 * - Functional: Works perfectly with immutable objects
 *
 * Example usage:
 * {{{
 *   val gameState = new ConcurrentState(Game.start(5))
 *
 *   // Lock-free read
 *   val currentGame = gameState.get()
 *
 *   // Lock-free atomic update
 *   val newGame = gameState.update { game =>
 *     game.makeMove(Move(1, 2, 3, Black))
 *   }
 * }}}
 *
 * @param initial The initial value (use immutable objects)
 * @tparamaram T The type of state being managed (should be immutable)
 */
class ConcurrentState[T](initial: T):
  private val ref = new AtomicReference(initial)

  /**
   * Get current state (wait-free read, never blocks).
   *
   * @return The current state
   */
  def get(): T = ref.get()

  /**
   * Atomically update state using function f.
   *
   * Retries automatically if concurrent update occurs.
   * This is optimistic locking - assumes contention is rare.
   *
   * Thread-safety guarantee: The function f will be called with
   * the most recent value, and the update will only succeed if
   * no other thread modified the state between the read and write.
   *
   * @param f Function to transform old state to new state
   * @return The new state after successful update
   */
  def update(f: T => T): T =
    @tailrec
    def attemptUpdate(): T =
      val oldValue = ref.get()
      val newValue = f(oldValue)
      if (ref.compareAndSet(oldValue, newValue))
        newValue  // Success - return new state
      else
        attemptUpdate()  // CAS failed due to concurrent update, retry with fresh value

    attemptUpdate()

  /**
   * Atomically set state to a new value.
   *
   * This is equivalent to update(_ => newValue) but more efficient
   * since it doesn't retry on CAS failure - it just sets the value.
   *
   * @param newValue The new state value
   */
  def set(newValue: T): Unit =
    ref.set(newValue)

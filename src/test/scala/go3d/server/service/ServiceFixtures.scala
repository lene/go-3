package go3d.server.service

import go3d.{Black, Komi, White}
import go3d.server.GameCreatedResponse
import org.scalatest.TryValues.*

import scala.util.Try

/** Games set up through a [[GameService]], for the suites of the service and the Lambdas. */
object ServiceFixtures:

  /** A new game of `size` with the default komi. */
  def newGame(service: GameService, size: Int): Try[GameCreatedResponse] =
    service.newGame(size, Komi.Default)

  /** A game that ended while the archive of `failingService` was down: it has no archive key.
    * Returns its id.
    */
  def gameWithFailedArchive(failingService: GameService): String =
    val gameId = newGame(failingService, 3).success.value.id
    val black = failingService.register(gameId, Black).success.value.authToken
    val white = failingService.register(gameId, White).success.value.authToken
    failingService.pass(gameId, black).success.value
    failingService.pass(gameId, white).success.value
    gameId

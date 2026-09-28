package go3d.server.service

import com.typesafe.scalalogging.LazyLogging
import go3d.BadColor
import go3d.Black
import go3d.Color
import go3d.Game
import go3d.GameOver
import go3d.Move
import go3d.Pass
import go3d.White
import go3d.server.AuthorizationError
import go3d.server.GameCreatedResponse
import go3d.server.IdGenerator
import go3d.server.NonexistentGame
import go3d.server.NotReadyToSet
import go3d.server.NullRequestInfo
import go3d.server.Player
import go3d.server.PlayerRegisteredResponse
import go3d.server.SaveGame
import go3d.server.SecurityUtils
import go3d.server.StatusResponse

import scala.util.{Failure, Success, Try}

class InvalidToken(val gameId: String)
  extends AuthorizationError("token is not registered in game " + gameId)

/**
 * The game server's operations on top of a [[GameStore]], independent of HTTP and of how state
 * is stored. The rules come from [[Game]]; errors are the exception types the http4s server
 * already maps to HTTP statuses (see `http4s/BaseHandler.scala`).
 *
 * @param now   current time in Unix milliseconds
 * @param newId generates game ids
 * @param newToken generates bearer tokens; only their SHA-256 hash is stored
 */
class GameService(
  store: GameStore, archive: GameArchive, now: () => Long, newId: () => String,
  newToken: () => String
) extends LazyLogging:

  def newGame(size: Int): Try[GameCreatedResponse] =
    for
      game <- Game.start(size)
      gameId = newId()
      _ <- store.createGame(gameId, game)
    yield GameCreatedResponse(gameId, size)

  /** Registers a black or white player; any other color fails with [[BadColor]]. */
  def register(gameId: String, color: Color): Try[PlayerRegisteredResponse] =
    val token = newToken()
    for
      _ <- check(color == Black || color == White, BadColor(color.ascii))
      stored <- existing(gameId)
      _ <- check(!stored.game.isOver, GameOver(stored.game))
      _ <- store.registerPlayer(gameId, color, SecurityUtils.sha256(token))
    yield
      val ready = color == Black && stored.players.contains(White)
      PlayerRegisteredResponse(stored.game, color, token, ready, NullRequestInfo)

  /**
   * Without a token the status shows the board only. With a token it also shows the caller's
   * possible moves and whether it is their turn with both players registered; a token not
   * registered in the game fails with [[InvalidToken]].
   */
  def status(gameId: String, token: Option[String]): Try[StatusResponse] =
    for
      stored <- existing(gameId)
      color <- token.fold[Try[Option[Color]]](Success(None))(
        t => authenticate(gameId, t).map(Some(_))
      )
    yield
      val game = stored.game
      color.fold(StatusResponse(game, List(), false, game.isOver, None, NullRequestInfo)) { c =>
        val ready = game.isTurn(c) && stored.players.size == 2
        StatusResponse(game, game.possibleMoves(c), ready, game.isOver, Some(c), NullRequestInfo)
      }

  /**
   * The game after its first `moveCount` moves, for replaying it; the count is clamped to the
   * moves played so far.
   */
  def statusAt(gameId: String, moveCount: Int): Try[StatusResponse] =
    for
      stored <- existing(gameId)
      game <- stored.game.atMove(moveCount.max(0).min(stored.game.moves.length))
    yield StatusResponse(game, List(), false, game.isOver, None, NullRequestInfo)

  /** A short-lived download URL for the archive of a finished game; None when it has none. */
  def archivedUrl(gameId: String): Try[Option[String]] = archive.url(gameId)

  def set(gameId: String, token: String, x: Int, y: Int, z: Int): Try[StatusResponse] =
    play(gameId, token, color => Try(Move(x, y, z, color)))

  def pass(gameId: String, token: String): Try[StatusResponse] =
    play(gameId, token, color => Success(Pass(color)))

  def openGames(): Try[Array[String]] = store.openGames()

  private def play(
    gameId: String, token: String, move: Color => Try[Move | Pass]
  ): Try[StatusResponse] =
    for
      stored <- existing(gameId)
      color <- authenticate(gameId, token)
      _ <- check(!stored.game.isOver, GameOver(stored.game))
      _ <- check(stored.game.isTurn(color), NotReadyToSet(gameId, color))
      newGame <- move(color).flatMap(stored.game.makeMove)
      _ <- store.updateGame(gameId, stored.version, newGame)
    yield
      if newGame.isOver then complete(gameId, newGame, stored.players)
      StatusResponse(
        newGame, newGame.possibleMoves(color), false, newGame.isOver, Some(color), NullRequestInfo
      )

  /**
   * Archives a finished game, then marks it completed so the store can expire it. The move that
   * ended the game is already stored, so a failure here does not fail the request: the game
   * stays in the store without an expiry and can be archived again later.
   */
  private def complete(gameId: String, game: Game, players: Set[Color]): Unit =
    val saveGame = SaveGame(game, players.map(c => c -> Player(c, gameId)).toMap)
    val expiresAt = now() / 1000L + GameService.RetentionSeconds
    archive.archive(gameId, saveGame)
      .flatMap(key => store.markCompleted(gameId, key, expiresAt))
      .failed.foreach(e => logger.error("archiving finished game " + gameId + " failed", e))

  private def existing(gameId: String): Try[StoredGame] =
    store.getGame(gameId).flatMap(
      _.fold[Try[StoredGame]](Failure(NonexistentGame(gameId, List())))(Success(_))
    )

  private def authenticate(gameId: String, token: String): Try[Color] =
    store.playerColor(gameId, SecurityUtils.sha256(token)).flatMap(
      _.fold[Try[Color]](Failure(InvalidToken(gameId)))(Success(_))
    )

  private def check(condition: Boolean, error: => Throwable): Try[Unit] =
    if condition then Success(()) else Failure(error)

object GameService:
  /** Finished games and their players stay in the store this long after the game ends. */
  val RetentionSeconds: Long = 7L * 24 * 3600

  def apply(store: GameStore, archive: GameArchive): GameService =
    new GameService(
      store, archive, () => System.currentTimeMillis(), () => IdGenerator.getId,
      () => IdGenerator.generateAuthToken
    )

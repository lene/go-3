package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.Games
import go3d.server.aws.S3Client
import go3d.server.given  // Circe encoders from Jsonify.scala
import io.circe.syntax._
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.io._
import org.http4s.headers.Location

/**
 * Serves archived game state.
 *
 * Priority:
 *  1. Local file archive  → returns SaveGame JSON directly (200)
 *  2. S3 archive          → redirects to 5-minute pre-signed URL (302)
 *  3. Neither             → 404
 */
case class GetArchivedGame(gameId: String) extends LazyLogging:

  def response: IO[Response[IO]] =
    // 1. Check local file archive
    Games.fileIO.flatMap(_.loadArchivedGame(gameId).toOption) match
      case Some(saveGame) =>
        logger.debug(s"Serving archived game $gameId from local file")
        Ok(saveGame.asJson.noSpaces)
      case None =>
        // 2. Try S3 pre-signed URL
        S3Client.generatePresignedUrl(gameId) match
          case Some(url) =>
            logger.debug(s"Redirecting archived game $gameId to S3 pre-signed URL")
            IO(Response[IO](status = Status.Found)
              .withHeaders(Location(Uri.unsafeFromString(url))))
          case None =>
            NotFound(s"Game $gameId not found in archive")

package go3d.server.service

import go3d.Color
import go3d.server.{StatusResponse, decodeColor, decodeStatusResponse}
import io.circe.Json
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions

/** Checks of the `over` and `winner` of status response documents, the same on every server. */
object StatusAssertions:

  private def jsonOf(body: String): Json = decode[Json](body).getOrElse(Assertions.fail(body))

  /** Decoding also checks that `over` and `winner` agree with the game in the document. */
  private def statusOf(body: String): StatusResponse =
    decode[StatusResponse](body).getOrElse(Assertions.fail(body))

  /** The status of a game that is not over has `over` false and no `winner` key. */
  def assertNotOver(body: String): Unit =
    val json = jsonOf(body)
    Assertions.assertEquals(Right(false), json.hcursor.downField("over").as[Boolean], body)
    Assertions.assertFalse(json.hcursor.downField("winner").succeeded, body)
    Assertions.assertEquals(None, statusOf(body).winner, body)

  /** The status of a finished game has `over` true and the `winner`, in the format of a colour. */
  def assertWonBy(body: String, winner: Color): Unit =
    val json = jsonOf(body)
    Assertions.assertEquals(Right(true), json.hcursor.downField("over").as[Boolean], body)
    Assertions.assertEquals(Right(winner), json.hcursor.downField("winner").as[Color], body)
    Assertions.assertEquals(Some(winner), statusOf(body).winner, body)

  /** Checks a game played to its end, with the response to each move and the final pass last:
    * only the final response and the statuses fetched afterwards name the winner, and a replay
    * names it only at the end. A replay count is clamped to the moves played, so a negative one
    * replays the start and one above the end replays the end.
    */
  def assertWinnerOnlyWhenOver(
      moveResponses: List[String],
      laterStatuses: List[String],
      replayAt: Int => String,
      winner: Color
  ): Unit =
    val end = moveResponses.length
    moveResponses.init.foreach(assertNotOver)
    assertWonBy(moveResponses.last, winner)
    laterStatuses.foreach(assertWonBy(_, winner))
    List(-1, 0, end - 1).foreach(count => assertNotOver(replayAt(count)))
    List(end, end + 1).foreach(count => assertWonBy(replayAt(count), winner))

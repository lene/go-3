package go3d.client

import go3d.Game
import go3d.Position
import go3d.server.{RequestInfo, StatusResponse}
import scala.util.{Success, Try}
import org.scalatest.TryValues.*

def mockStatus(ready: Boolean, moves: List[Position] = List()): StatusResponse =
  StatusResponse(
    Game.start(3).success.value, moves, ready, false, None, RequestInfo(Map(), "", "", false)
  )

class MockClient extends BaseClient("mock server", "mock id", None, None):
  override def status: Try[StatusResponse] = Success(mockStatus(ready = true))

/// Reports a status that is not ready until `status` has been called `readyAfter` times.
class NotReadyClient(readyAfter: Int) extends BaseClient("mock server", "mock id", None, None):
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  var statusCalls: Int = 0
  override def status: Try[StatusResponse] =
    statusCalls += 1
    Success(mockStatus(ready = statusCalls >= readyAfter))

/// Records the moves sent to the server and answers each with a status that is not game over.
class RecordingClient extends BaseClient("mock server", "mock id", None, None):
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  var sent: List[String] = List()
  override def set(x: Int, y: Int, z: Int): Try[StatusResponse] =
    sent = sent :+ s"set $x $y $z"
    Success(mockStatus(ready = false))
  override def pass: Try[StatusResponse] =
    sent = sent :+ "pass"
    Success(mockStatus(ready = false))

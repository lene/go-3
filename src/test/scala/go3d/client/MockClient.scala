package go3d.client

import go3d.Game
import go3d.server.{RequestInfo, StatusResponse}
import scala.util.Success
import org.scalatest.TryValues.*

class MockClient extends BaseClient("mock server", "mock id", None, None):
  override def status: scala.util.Try[StatusResponse] =
    Success(StatusResponse(
      Game.start(3).success.value, List(), true, false, None, RequestInfo(Map(), "", "", false)
    ))


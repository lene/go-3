package go3d.client

import go3d.Game
import go3d.server.{RequestInfo, StatusResponse}
import scala.util.Success

class MockClient extends BaseClient("mock server", "mock id", None, None):
  override def status: scala.util.Try[StatusResponse] =
    Success(StatusResponse(Game.start(3).get, List(), true, false, None, RequestInfo(Map(), "", "", false)))

